package io.github.taskengineer.rcgear.feature.db

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.ui.ScreenEvent
import io.github.taskengineer.rcgear.core.ui.ScreenEvents
import io.github.taskengineer.rcgear.core.ui.UiText
import io.github.taskengineer.rcgear.core.ui.formatRatio
import io.github.taskengineer.rcgear.domain.model.Chassis
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput
import io.github.taskengineer.rcgear.domain.repository.ChassisRepository
import io.github.taskengineer.rcgear.navigation.chassisEditRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * シャーシ編集画面の ViewModel（PLAN Step 10）。
 *
 * 標準値（JSON）に対する上書きをフィールド単位で編集する。
 * - 標準値と同じ値のフィールドは上書きとして保存しない（null のまま）
 * - 全フィールドが標準値と同じなら上書きレコード自体を消す（Repository 側の仕様）
 * - リセットで上書きレコードを削除し標準値に戻す
 */
@HiltViewModel
class ChassisEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chassisRepository: ChassisRepository
) : ViewModel() {

    // ナビゲーション引数（型安全ルート [ChassisEdit] の chassisId）から取得（S-12）
    private val chassisId: String = savedStateHandle.chassisEditRoute().chassisId

    private val _uiState = MutableStateFlow(ChassisEditUiState())
    val uiState: StateFlow<ChassisEditUiState> = _uiState.asStateFlow()

    private val screenEvents = ScreenEvents()

    /** 画面を閉じる等の一度きりの出来事（U-2） */
    val events: Flow<ScreenEvent> = screenEvents.flow

    init {
        viewModelScope.launch {
            val standard = chassisRepository.getStandardChassisById(chassisId)
            val current = chassisRepository.getChassisById(chassisId)
            if (standard == null || current == null) {
                screenEvents.emit(ScreenEvent.NavigateBack)
                return@launch
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    standard = standard,
                    current = current,
                    // 入力欄は現在の有効値（上書きがあれば上書き値）で初期化
                    ratioInput = current.internalRatio.formatRatio(),
                    tireInput = current.defaultTireMm.toString(),
                    noteInput = current.note.orEmpty()
                )
            }
        }
    }

    // ----- 入力 -----

    fun onRatioChange(value: String) {
        _uiState.update { it.copy(ratioInput = value, errorMessage = null) }
    }

    fun onTireChange(value: String) {
        _uiState.update { it.copy(tireInput = value, errorMessage = null) }
    }

    fun onNoteChange(value: String) {
        _uiState.update { it.copy(noteInput = value, errorMessage = null) }
    }

    // ----- 保存 -----

    fun onSave() {
        val state = _uiState.value
        val standard = state.standard ?: return

        // バリデーション（REF-1 / BUG-1）:
        // ここを通った値は CALC 画面で GearCalculationInput にそのまま渡るため、
        // 「正の数」だけでなく計算側の有効範囲まで確認する。範囲の定義は
        // GearCalculationInput の companion が単一の真実。
        val ratio = state.ratioInput.trim().toDoubleOrNull()
        if (ratio == null || !GearCalculationInput.isValidInternalRatio(ratio)) {
            _uiState.update {
                it.copy(errorMessage = UiText.Res(R.string.chassis_edit_error_internal_ratio))
            }
            return
        }
        val tire = state.tireInput.trim().toIntOrNull()
        if (tire == null || tire !in GearCalculationInput.TIRE_MM_RANGE) {
            _uiState.update {
                it.copy(
                    errorMessage = UiText.Res(
                        R.string.chassis_edit_error_tire_mm,
                        listOf(
                            GearCalculationInput.MIN_TIRE_MM,
                            GearCalculationInput.MAX_TIRE_MM
                        )
                    )
                )
            }
            return
        }
        val note = state.noteInput.trim()

        viewModelScope.launch {
            // 標準値と同じフィールドは null（上書きなし）にする。
            // 全フィールドが null なら Repository がレコードごと削除する
            chassisRepository.overrideChassis(
                chassisId = chassisId,
                internalRatio = ratio.takeIf { it != standard.internalRatio },
                defaultTireMm = tire.takeIf { it != standard.defaultTireMm },
                note = note.takeIf { it.isNotEmpty() && it != standard.note.orEmpty() }
            )
            screenEvents.emit(ScreenEvent.NavigateBack)
        }
    }

    // ----- リセット -----

    fun onResetClick() {
        _uiState.update { it.copy(showResetConfirm = true) }
    }

    fun onResetConfirmDismiss() {
        _uiState.update { it.copy(showResetConfirm = false) }
    }

    fun onResetConfirm() {
        viewModelScope.launch {
            chassisRepository.resetOverride(chassisId)
            _uiState.update { it.copy(showResetConfirm = false) }
            screenEvents.emit(ScreenEvent.NavigateBack)
        }
    }
}

/**
 * @property standard   JSON 由来の標準値（上書き適用前）
 * @property current    現在の有効値（上書き合成済み）。isUserEdited でリセットボタンの表示を決める
 *
 * 保存・リセット完了と「対象なし」は状態ではなく [ScreenEvent.NavigateBack] で流す（U-2）。
 */
data class ChassisEditUiState(
    val isLoading: Boolean = true,
    val standard: Chassis? = null,
    val current: Chassis? = null,
    val ratioInput: String = "",
    val tireInput: String = "",
    val noteInput: String = "",
    val errorMessage: UiText? = null,
    val showResetConfirm: Boolean = false
)
