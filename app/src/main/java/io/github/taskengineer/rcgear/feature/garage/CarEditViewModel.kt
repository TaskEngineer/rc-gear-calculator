package io.github.taskengineer.rcgear.feature.garage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.ui.ScreenEvent
import io.github.taskengineer.rcgear.core.ui.ScreenEvents
import io.github.taskengineer.rcgear.core.ui.UiText
import io.github.taskengineer.rcgear.domain.model.Car
import io.github.taskengineer.rcgear.domain.model.Chassis
import io.github.taskengineer.rcgear.domain.model.Maker
import io.github.taskengineer.rcgear.domain.repository.CarRepository
import io.github.taskengineer.rcgear.domain.repository.ChassisRepository
import io.github.taskengineer.rcgear.navigation.carEditRouteOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 車の新規作成・編集画面の ViewModel（G-1）。
 *
 * 1 つの ViewModel で両方を受け持つ。新規か編集かはルート引数の有無で決まり
 * （[carEditRouteOrNull] が null なら新規）、違いは「保存が create か update か」と
 * 「削除・アーカイブを出すか」だけなので、画面を 2 つに割る理由が無い。
 *
 * シャーシ一覧は一度だけ読む（`first()`）。編集フォームを開いている間に
 * シャーシDBが変わることは実用上なく、購読すると「入力途中の状態」と
 * 「流れてきた一覧」の合成を考える必要が出るため。
 */
@HiltViewModel
class CarEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val carRepository: CarRepository,
    private val chassisRepository: ChassisRepository
) : ViewModel() {

    /** 編集対象の車。null なら新規作成（`CarCreate` で開かれた） */
    private val carId: String? = savedStateHandle.carEditRouteOrNull()?.carId

    private val _uiState = MutableStateFlow(CarEditUiState(isNew = carId == null))
    val uiState: StateFlow<CarEditUiState> = _uiState.asStateFlow()

    private val screenEvents = ScreenEvents()

    /** 画面を閉じる等の一度きりの出来事（U-2） */
    val events: Flow<ScreenEvent> = screenEvents.flow

    /** 編集中の車の元データ。`createdAt` を保つために持っておく */
    private var editing: Car? = null

    init {
        viewModelScope.launch {
            val makers = chassisRepository.getAllMakers().first()
            val car = carId?.let { carRepository.getCar(it) }
            if (carId != null && car == null) {
                // 一覧から開いた直後に消えた（他の経路で削除された）
                screenEvents.emit(ScreenEvent.NavigateBack)
                return@launch
            }
            editing = car
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    makers = makers,
                    nameInput = car?.name.orEmpty(),
                    noteInput = car?.note.orEmpty(),
                    isArchived = car?.isArchived ?: false,
                    selectedChassis = car?.chassisId?.let { id -> findChassis(makers, id) }
                )
            }
        }
    }

    // ----- 入力 -----

    fun onNameChange(value: String) {
        _uiState.update { it.copy(nameInput = value, errorMessage = null) }
    }

    fun onNoteChange(value: String) {
        _uiState.update { it.copy(noteInput = value, errorMessage = null) }
    }

    fun onArchivedChange(value: Boolean) {
        _uiState.update { it.copy(isArchived = value) }
    }

    // ----- シャーシ選択 -----

    fun onChassisCardClick() {
        _uiState.update { it.copy(isChassisSheetOpen = true) }
    }

    fun onChassisSheetDismiss() {
        _uiState.update { it.copy(isChassisSheetOpen = false) }
    }

    fun onChassisSelected(chassisId: String) {
        val selected = findChassis(_uiState.value.makers, chassisId) ?: return
        _uiState.update {
            it.copy(
                selectedChassis = selected,
                isChassisSheetOpen = false,
                errorMessage = null
            )
        }
    }

    // ----- 保存 -----

    fun onSave() {
        val state = _uiState.value
        val name = state.nameInput.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(errorMessage = UiText.Res(R.string.car_edit_error_name)) }
            return
        }
        // 車の名前は UNIQUE にしていない（CarEntity のコメント参照）。
        // 「TA08 #1」と「TA08 #1（旧）」を自由に付けられるほうが実用的なため。
        val chassis = state.selectedChassis
        if (chassis == null) {
            _uiState.update { it.copy(errorMessage = UiText.Res(R.string.car_edit_error_chassis)) }
            return
        }
        val note = state.noteInput.trim().takeIf { it.isNotEmpty() }

        viewModelScope.launch {
            val current = editing
            if (current == null) {
                carRepository.createCar(
                    name = name,
                    chassisId = chassis.chassis.id,
                    note = note
                )
            } else {
                carRepository.updateCar(
                    current.copy(
                        name = name,
                        chassisId = chassis.chassis.id,
                        note = note,
                        isArchived = state.isArchived
                    )
                )
            }
            screenEvents.emit(ScreenEvent.NavigateBack)
        }
    }

    // ----- 削除 -----

    fun onDeleteClick() {
        _uiState.update { it.copy(showDeleteConfirm = true) }
    }

    fun onDeleteConfirmDismiss() {
        _uiState.update { it.copy(showDeleteConfirm = false) }
    }

    fun onDeleteConfirm() {
        val id = carId ?: return
        viewModelScope.launch {
            // シートと値は Room の CASCADE で一緒に消える（M-3）
            carRepository.deleteCar(id)
            _uiState.update { it.copy(showDeleteConfirm = false) }
            screenEvents.emit(ScreenEvent.NavigateBack)
        }
    }

    private fun findChassis(makers: List<Maker>, chassisId: String): SelectedChassis? {
        makers.forEach { maker ->
            maker.chassis.firstOrNull { it.id == chassisId }?.let {
                return SelectedChassis(makerName = maker.name, chassis = it)
            }
        }
        return null
    }
}

/**
 * 選択中のシャーシ。メーカー名も表示に使うので合わせて持つ。
 *
 * CALC 画面の `feature.calc.SelectedChassis` と同じ形だが、型を共有していない。
 * 画面の UiState の一部であり、共有すると feature 間の参照が生まれるため
 * （共有すべきは部品の `ChassisSelectBottomSheet` のほうで、こちらは G-1 で `core/ui` へ移した）。
 */
data class SelectedChassis(
    val makerName: String,
    val chassis: Chassis
)

/**
 * @property isNew    新規作成か。タイトル・削除ボタン・アーカイブ欄の出し分けに使う
 * @property makers   シャーシ選択ボトムシートに出す全メーカー（上書き合成済み）
 *
 * 保存・削除の完了と「対象なし」は状態ではなく [ScreenEvent.NavigateBack] で流す（U-2）。
 */
data class CarEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val makers: List<Maker> = emptyList(),
    val nameInput: String = "",
    val noteInput: String = "",
    val selectedChassis: SelectedChassis? = null,
    val isArchived: Boolean = false,
    val isChassisSheetOpen: Boolean = false,
    val errorMessage: UiText? = null,
    val showDeleteConfirm: Boolean = false
)
