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
 *
 * ### 検証は入力のたび（BUG-7）
 * 以前は保存を押すまで何も言わず、押した瞬間に違反を 1 件ずつ出していた。
 * 入るはずのない値を入れさせてから弾く形なので、`SheetEditViewModel` と同じく
 * **入力のたびに検証してインラインにエラーを出し、保存ボタンを無効にする**形に揃えた。
 * 範囲の定義は [GearCalculationInput] の companion が単一の真実（REF-1 / BUG-1）。
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
            // 入力欄は現在の有効値（上書きがあれば上書き値）で初期化する。
            // 読み込んだ値も検証に通す — 古いデータや手で書いた JSON から
            // 範囲外の値が入っていた場合、開いた時点でエラーが出ていないと
            // 「触っていないのに保存できる」状態が残る
            val ratioInput = current.internalRatio.formatRatio()
            val tireInput = current.defaultTireMm.toString()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    standard = standard,
                    current = current,
                    ratioInput = ratioInput,
                    tireInput = tireInput,
                    noteInput = current.note.orEmpty(),
                    ratioError = ratioErrorOf(ratioInput),
                    tireError = tireErrorOf(tireInput)
                )
            }
        }
    }

    // ----- 入力 -----

    fun onRatioChange(value: String) {
        _uiState.update { it.copy(ratioInput = value, ratioError = ratioErrorOf(value)) }
    }

    fun onTireChange(value: String) {
        _uiState.update { it.copy(tireInput = value, tireError = tireErrorOf(value)) }
    }

    fun onNoteChange(value: String) {
        _uiState.update { it.copy(noteInput = value) }
    }

    /**
     * 入力のたびに走る検証（BUG-7）。違反なら [UiText]、問題なければ null。
     *
     * **空欄はエラーにしない。** 消して打ち直している最中に赤くなるのは煩わしいだけで、
     * 「まだ入力していない」は違反ではない。空のまま保存されないことは
     * [ChassisEditUiState.canSave] が担保する。
     *
     * ここを通った値は CALC で [GearCalculationInput] にそのまま渡るので、
     * 「正の数」ではなく計算側の有効範囲まで見る。
     */
    private fun ratioErrorOf(text: String): UiText? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val ratio = trimmed.toDoubleOrNull()
        return if (ratio == null || !GearCalculationInput.isValidInternalRatio(ratio)) {
            UiText.Res(R.string.chassis_edit_error_internal_ratio)
        } else {
            null
        }
    }

    /** タイヤ径版。[ratioErrorOf] と同じ規則 */
    private fun tireErrorOf(text: String): UiText? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val tire = trimmed.toIntOrNull()
        return if (tire == null || tire !in GearCalculationInput.TIRE_MM_RANGE) {
            UiText.Res(
                R.string.chassis_edit_error_tire_mm,
                listOf(GearCalculationInput.MIN_TIRE_MM, GearCalculationInput.MAX_TIRE_MM)
            )
        } else {
            null
        }
    }

    // ----- 保存 -----

    fun onSave() {
        val state = _uiState.value
        val standard = state.standard ?: return
        // 画面側もボタンを無効にしているが、ここでも止める（最後の関所）。
        // canSave が true なら両方とも変換できるが、例外を投げないよう null 安全に取る
        if (!state.canSave) return
        val ratio = state.ratioInput.trim().toDoubleOrNull() ?: return
        val tire = state.tireInput.trim().toIntOrNull() ?: return
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
 * @property ratioError 内部減速比のインラインエラー。null なら違反なし（BUG-7）
 * @property tireError  タイヤ径のインラインエラー
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
    val ratioError: UiText? = null,
    val tireError: UiText? = null,
    val showResetConfirm: Boolean = false
) {
    /**
     * 保存できるか（`SheetEditUiState.canSave` と同じ規約）。
     *
     * 空欄を弾くのはこちら側の仕事。内部減速比とタイヤ径は必須で、
     * 空のまま保存すると「上書きを外す」操作（リセット）と区別が付かなくなる。
     */
    val canSave: Boolean
        get() = ratioError == null && tireError == null &&
            ratioInput.isNotBlank() && tireInput.isNotBlank()
}
