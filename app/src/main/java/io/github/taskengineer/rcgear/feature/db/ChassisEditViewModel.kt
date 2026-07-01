package io.github.taskengineer.rcgear.feature.db

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.taskengineer.rcgear.data.repository.ChassisRepository
import io.github.taskengineer.rcgear.domain.model.Chassis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
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

    // ナビゲーション引数 "db/{chassisId}" から取得
    private val chassisId: String = checkNotNull(savedStateHandle["chassisId"])

    private val _uiState = MutableStateFlow(ChassisEditUiState())
    val uiState: StateFlow<ChassisEditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val standard = chassisRepository.getStandardChassisById(chassisId)
            val current = chassisRepository.getChassisById(chassisId)
            if (standard == null || current == null) {
                _uiState.update { it.copy(isLoading = false, notFound = true) }
                return@launch
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    standard = standard,
                    current = current,
                    // 入力欄は現在の有効値（上書きがあれば上書き値）で初期化
                    ratioInput = String.format(Locale.US, "%.2f", current.internalRatio),
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

        // バリデーション: 数値としてパースできて正の値であること
        val ratio = state.ratioInput.trim().toDoubleOrNull()
        if (ratio == null || ratio <= 0.0) {
            _uiState.update { it.copy(errorMessage = "内部減速比は正の数値で入力してください") }
            return
        }
        val tire = state.tireInput.trim().toIntOrNull()
        if (tire == null || tire <= 0) {
            _uiState.update { it.copy(errorMessage = "タイヤ径は正の整数で入力してください") }
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
            _uiState.update { it.copy(isDone = true) }
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
            _uiState.update { it.copy(showResetConfirm = false, isDone = true) }
        }
    }
}

/**
 * @property standard   JSON 由来の標準値（上書き適用前）
 * @property current    現在の有効値（上書き合成済み）。isUserEdited でリセットボタンの表示を決める
 * @property isDone     保存・リセット完了。UI 側はこれを見て前の画面に戻る
 * @property notFound   対象シャーシが見つからない
 */
data class ChassisEditUiState(
    val isLoading: Boolean = true,
    val standard: Chassis? = null,
    val current: Chassis? = null,
    val ratioInput: String = "",
    val tireInput: String = "",
    val noteInput: String = "",
    val errorMessage: String? = null,
    val showResetConfirm: Boolean = false,
    val isDone: Boolean = false,
    val notFound: Boolean = false
)
