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
            _uiState.update {
                it.copy(
                    isLoading = false,
                    makerInput = loaded.makerName,
                    nameInput = loaded.name,
                    ratioInput = loaded.internalRatio.formatRatio(),
                    tireInput = loaded.defaultTireMm.toString(),
                    noteInput = loaded.note.orEmpty(),
                    category = loaded.category,
                    drive = loaded.traits.drive,
                    hasCenterDiff = loaded.traits.hasCenterDiff
                )
            }
        }
    }

    // ----- 入力 -----

    fun onMakerChange(value: String) = update { copy(makerInput = value) }

    fun onNameChange(value: String) = update { copy(nameInput = value) }

    fun onRatioChange(value: String) = update { copy(ratioInput = value) }

    fun onTireChange(value: String) = update { copy(tireInput = value) }

    fun onNoteChange(value: String) = update { copy(noteInput = value) }

    fun onCategoryChange(value: ChassisCategory) = update { copy(category = value) }

    /** 駆動方式。null は「不明」で、その場合は全ての項目が出る（HANDOFF §5.7） */
    fun onDriveChange(value: ChassisDrive?) = update { copy(drive = value) }

    fun onCenterDiffChange(value: Boolean?) = update { copy(hasCenterDiff = value) }

    // ----- 保存 -----

    fun onSave() {
        val state = _uiState.value
        val name = state.nameInput.trim()
        if (name.isEmpty()) {
            setError(UiText.Res(R.string.user_chassis_error_name))
            return
        }
        val maker = state.makerInput.trim()
        if (maker.isEmpty()) {
            setError(UiText.Res(R.string.user_chassis_error_maker))
            return
        }
        val ratio = state.ratioInput.trim().toDoubleOrNull()
        if (ratio == null || !GearCalculationInput.isValidInternalRatio(ratio)) {
            setError(UiText.Res(R.string.chassis_edit_error_internal_ratio))
            return
        }
        val tire = state.tireInput.trim().toIntOrNull()
        if (tire == null || tire !in GearCalculationInput.TIRE_MM_RANGE) {
            setError(
                UiText.Res(
                    R.string.chassis_edit_error_tire_mm,
                    listOf(GearCalculationInput.MIN_TIRE_MM, GearCalculationInput.MAX_TIRE_MM)
                )
            )
            return
        }

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
        _uiState.update { it.transform().copy(errorMessage = null) }
    }

    private fun setError(message: UiText) {
        _uiState.update { it.copy(errorMessage = message) }
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
    val errorMessage: UiText? = null,
    val showDeleteConfirm: Boolean = false
)
