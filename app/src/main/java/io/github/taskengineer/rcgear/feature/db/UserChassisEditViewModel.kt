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
import io.github.taskengineer.rcgear.domain.model.ChassisCategory
import io.github.taskengineer.rcgear.domain.model.ChassisDrive
import io.github.taskengineer.rcgear.domain.model.ChassisTraits
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput
import io.github.taskengineer.rcgear.domain.model.UserChassis
import io.github.taskengineer.rcgear.domain.repository.ChassisRepository
import io.github.taskengineer.rcgear.navigation.userChassisEditRouteOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ユーザー定義シャーシの作成・編集の ViewModel（F-5）。
 *
 * 同梱シャーシの編集（[ChassisEditViewModel]）とは別物。あちらは
 * **同梱エントリへの差分**を持つ画面で、こちらは**エントリそのもの**を作る。
 * 自作エントリに差分を重ねる仕組みは入れない（自分で直せるので意味が無い）。
 *
 * id は Repository が `user_<uuid>` で採番する。画面からは触らせない —
 * 接頭辞は同梱 DB と衝突させないための規約で、破られるとインポートの判定
 * （`ImportDataUseCase.isKnownChassis`）まで狂う。
 */
@HiltViewModel
class UserChassisEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chassisRepository: ChassisRepository
) : ViewModel() {

    /** 編集対象。null なら新規作成 */
    private val chassisId: String? = savedStateHandle.userChassisEditRouteOrNull()?.chassisId

    private val _uiState = MutableStateFlow(UserChassisEditUiState(isNew = chassisId == null))
    val uiState: StateFlow<UserChassisEditUiState> = _uiState.asStateFlow()

    private val screenEvents = ScreenEvents()

    /** 画面を閉じる等の一度きりの出来事（U-2） */
    val events: Flow<ScreenEvent> = screenEvents.flow

    private var editing: UserChassis? = null

    init {
        viewModelScope.launch {
            if (chassisId == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            val loaded = chassisRepository.getUserChassis(chassisId)
            if (loaded == null) {
                screenEvents.emit(ScreenEvent.NavigateBack)
                return@launch
            }
            editing = loaded
            // 読み込んだ値も検証に通す。範囲外の値が入っていたら開いた時点でエラーを出す
            // （出さないと「触っていないのに保存できる」状態が残る）
            val ratioInput = loaded.internalRatio.formatRatio()
            val tireInput = loaded.defaultTireMm.toString()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    makerInput = loaded.makerName,
                    nameInput = loaded.name,
                    ratioInput = ratioInput,
                    tireInput = tireInput,
                    noteInput = loaded.note.orEmpty(),
                    category = loaded.category,
                    drive = loaded.traits.drive,
                    hasCenterDiff = loaded.traits.hasCenterDiff,
                    ratioError = ratioErrorOf(ratioInput),
                    tireError = tireErrorOf(tireInput)
                )
            }
        }
    }

    // ----- 入力 -----

    fun onMakerChange(value: String) = update { copy(makerInput = value) }

    fun onNameChange(value: String) = update { copy(nameInput = value) }

    fun onRatioChange(value: String) = update {
        copy(ratioInput = value, ratioError = ratioErrorOf(value))
    }

    fun onTireChange(value: String) = update {
        copy(tireInput = value, tireError = tireErrorOf(value))
    }

    fun onNoteChange(value: String) = update { copy(noteInput = value) }

    fun onCategoryChange(value: ChassisCategory) = update { copy(category = value) }

    /** 駆動方式。null は「不明」で、その場合は全ての項目が出る（HANDOFF §5.7） */
    fun onDriveChange(value: ChassisDrive?) = update { copy(drive = value) }

    fun onCenterDiffChange(value: Boolean?) = update { copy(hasCenterDiff = value) }

    // ----- 保存 -----

    fun onSave() {
        val state = _uiState.value
        // 画面側もボタンを無効にしているが、ここでも止める（最後の関所）
        if (!state.canSave) return
        val name = state.nameInput.trim()
        val maker = state.makerInput.trim()
        val ratio = state.ratioInput.trim().toDoubleOrNull() ?: return
        val tire = state.tireInput.trim().toIntOrNull() ?: return

        val draft = UserChassis(
            // 新規の id と時刻は Repository が入れる。ここで作った値は使われない
            id = editing?.id.orEmpty(),
            makerName = maker,
            name = name,
            internalRatio = ratio,
            defaultTireMm = tire,
            category = state.category,
            traits = ChassisTraits(drive = state.drive, hasCenterDiff = state.hasCenterDiff),
            note = state.noteInput.trim().takeIf { it.isNotEmpty() },
            createdAt = editing?.createdAt ?: 0L,
            updatedAt = 0L
        )
        viewModelScope.launch {
            if (editing == null) {
                chassisRepository.addUserChassis(draft)
            } else {
                chassisRepository.updateUserChassis(draft)
            }
            screenEvents.emit(ScreenEvent.NavigateBack)
        }
    }

    // ----- 削除 -----

    fun onDeleteClick() = update { copy(showDeleteConfirm = true) }

    fun onDeleteConfirmDismiss() = update { copy(showDeleteConfirm = false) }

    fun onDeleteConfirm() {
        val id = chassisId ?: return
        viewModelScope.launch {
            // このシャーシを使っている車は残る（外部キーを張っていない）。
            // 一覧には「不明なシャーシ」として id が出る — 車ごと消すより復旧できる
            chassisRepository.deleteUserChassis(id)
            _uiState.update { it.copy(showDeleteConfirm = false) }
            screenEvents.emit(ScreenEvent.NavigateBack)
        }
    }

    // ----- 内部 -----

    private fun update(transform: UserChassisEditUiState.() -> UserChassisEditUiState) {
        _uiState.update { it.transform() }
    }

    /**
     * 入力のたびに走る検証（BUG-7。[ChassisEditViewModel] と同じ規則）。
     * 空欄はエラーにしない — 打ち直しの途中で赤くなるのは煩わしく、
     * 空のまま保存されないことは [UserChassisEditUiState.canSave] が担保する。
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
}

/**
 * @property drive         駆動方式。**null は「不明」**で、項目の出し分けでは
 *   「分からないから隠す」をしない（全て出す）
 * @property hasCenterDiff 同上。センターデフの有無が不明ならデフ欄を出す
 */
data class UserChassisEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val makerInput: String = "",
    val nameInput: String = "",
    val ratioInput: String = "",
    val tireInput: String = "",
    val noteInput: String = "",
    val category: ChassisCategory = ChassisCategory.TOURING,
    val drive: ChassisDrive? = null,
    val hasCenterDiff: Boolean? = null,
    val ratioError: UiText? = null,
    val tireError: UiText? = null,
    val showDeleteConfirm: Boolean = false
) {
    /**
     * 保存できるか（BUG-7 と同じ規約。HANDOFF §5.9）。
     *
     * メーカー名と名前は自由文字列なので「不正」が無く、埋まっているかだけを見る。
     * 内部減速比とタイヤ径は範囲があるので [ratioError] / [tireError] も見る。
     */
    val canSave: Boolean
        get() = ratioError == null && tireError == null &&
            makerInput.isNotBlank() && nameInput.isNotBlank() &&
            ratioInput.isNotBlank() && tireInput.isNotBlank()
}
