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
import io.github.taskengineer.rcgear.domain.model.ChassisDefaults
import io.github.taskengineer.rcgear.domain.model.SetupSheet
import io.github.taskengineer.rcgear.domain.model.initialValues
import io.github.taskengineer.rcgear.domain.repository.CarRepository
import io.github.taskengineer.rcgear.domain.repository.ChassisRepository
import io.github.taskengineer.rcgear.domain.repository.SetupSheetRepository
import io.github.taskengineer.rcgear.domain.schema.TouringSetupSchema
import io.github.taskengineer.rcgear.navigation.carDetailRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 車詳細（その車のセッティングシート一覧）の ViewModel（G-2）。
 *
 * シートを起こす経路は 2 つある:
 *  - **新規**: `TouringSetupSchema.initialValues()`（シャーシDBの内部減速比・タイヤ径 +
 *    レジストリの既定値）で始める
 *  - **複製**: 元シートの値をそのまま引き継ぎ、**元シートを `baselineId` に入れる**。
 *    これで差分の軸 B（前回のセットから何を変えたか）が効くようになる
 *
 * 値はシート作成時に絶対値として焼き込まれる。後からシャーシDBを編集しても
 * 既存シートが変わらないのはそのため（HANDOFF §5.3 / `SetupValues` の KDoc）。
 */
@HiltViewModel
class CarDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    carRepository: CarRepository,
    chassisRepository: ChassisRepository,
    private val sheetRepository: SetupSheetRepository
) : ViewModel() {

    private val carId: String = savedStateHandle.carDetailRoute().carId

    private val _uiState = MutableStateFlow(CarDetailUiState(carId = carId))
    val uiState: StateFlow<CarDetailUiState> = _uiState.asStateFlow()

    private val screenEvents = ScreenEvents()

    /** 画面を閉じる等の一度きりの出来事（U-2） */
    val events: Flow<ScreenEvent> = screenEvents.flow

    /** 新規シートの初期値に使うシャーシ既定値。解決できなければ空欄で始まる */
    private var chassisDefaults: ChassisDefaults = ChassisDefaults.NONE

    init {
        viewModelScope.launch {
            combine(
                carRepository.observeCar(carId),
                sheetRepository.observeSheets(carId),
                chassisRepository.getAllMakers()
            ) { car, sheets, makers ->
                Snapshot(
                    car = car,
                    sheets = sheets,
                    chassis = car?.chassisId?.let { id ->
                        makers.asSequence().flatMap { it.chassis }.firstOrNull { it.id == id }
                    }
                )
            }.collect { snapshot ->
                if (snapshot.car == null) {
                    // 別経路で車が削除された（車を消すとシートも CASCADE で消える）
                    screenEvents.emit(ScreenEvent.NavigateBack)
                    return@collect
                }
                chassisDefaults = snapshot.chassis
                    ?.let { ChassisDefaults.from(it) }
                    ?: ChassisDefaults.NONE
                val nameById = snapshot.sheets.associate { it.id to it.name }
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        car = snapshot.car,
                        chassis = snapshot.chassis,
                        sheets = snapshot.sheets.map { sheet ->
                            SheetListItem(
                                id = sheet.id,
                                name = sheet.name,
                                sessionDate = sheet.conditions.sessionDate,
                                trackName = sheet.conditions.trackName,
                                baselineName = sheet.baselineId?.let { nameById[it] },
                                isFavorite = sheet.isFavorite,
                                updatedAt = sheet.updatedAt
                            )
                        }
                    )
                }
            }
        }
    }

    // ----- シートを起こす -----

    /**
     * 新規シートのダイアログを開く。
     *
     * @param defaultName 既定のシート名。文言なので **画面側が解決して渡す**
     *   （ViewModel は `R.string` を文字列にできない。S-11 / `UiText` の KDoc）
     */
    fun onNewSheetClick(defaultName: String) {
        _uiState.update {
            it.copy(sheetDialog = SheetDialogState(nameInput = defaultName, sourceSheetId = null))
        }
    }

    /**
     * 複製のダイアログを開く。
     *
     * @param sourceSheetId 複製元。作られたシートの `baselineId` になる
     */
    fun onDuplicateClick(sourceSheetId: String, defaultName: String) {
        _uiState.update {
            it.copy(
                sheetDialog = SheetDialogState(
                    nameInput = defaultName,
                    sourceSheetId = sourceSheetId,
                    sourceSheetName = it.sheets.firstOrNull { sheet -> sheet.id == sourceSheetId }?.name
                )
            )
        }
    }

    fun onSheetNameChange(value: String) {
        _uiState.update { state ->
            state.copy(sheetDialog = state.sheetDialog?.copy(nameInput = value, error = null))
        }
    }

    fun onSheetDialogDismiss() {
        _uiState.update { it.copy(sheetDialog = null) }
    }

    fun onSheetDialogConfirm() {
        val dialog = _uiState.value.sheetDialog ?: return
        val name = dialog.nameInput.trim()
        if (name.isEmpty()) {
            _uiState.update { state ->
                state.copy(
                    sheetDialog = state.sheetDialog?.copy(
                        error = UiText.Res(R.string.car_detail_error_sheet_name)
                    )
                )
            }
            return
        }
        viewModelScope.launch {
            val sourceId = dialog.sourceSheetId
            val values = if (sourceId == null) {
                TouringSetupSchema.initialValues(chassisDefaults)
            } else {
                // 複製元が消えていたら値を引き継げない。初期値で起こす方がマシなので落とさない
                sheetRepository.getSheet(sourceId)?.values
                    ?: TouringSetupSchema.initialValues(chassisDefaults)
            }
            sheetRepository.createSheet(
                carId = carId,
                name = name,
                values = values,
                baselineId = sourceId
            )
            _uiState.update { it.copy(sheetDialog = null) }
        }
    }

    /** 車・シート・シャーシの 3 本の Flow をまとめる中間の型 */
    private data class Snapshot(
        val car: Car?,
        val sheets: List<SetupSheet>,
        val chassis: Chassis?
    )
}

/**
 * 一覧に出すシート 1 枚。
 *
 * @property baselineName ベースライン（差分の軸 B の相手）の名前。無ければ null。
 *   id ではなく名前を持つのは、表示側でもう一度一覧を引かせないため
 */
data class SheetListItem(
    val id: String,
    val name: String,
    val sessionDate: Long?,
    val trackName: String?,
    val baselineName: String?,
    val isFavorite: Boolean,
    val updatedAt: Long
)

/**
 * シートを起こすダイアログの状態。
 *
 * @property sourceSheetId null なら新規、値があれば複製（その id がベースラインになる）
 */
data class SheetDialogState(
    val nameInput: String,
    val sourceSheetId: String?,
    val sourceSheetName: String? = null,
    val error: UiText? = null
)

/**
 * @property carId   画面遷移（車の編集へ）に使う。ルート引数のキー名を画面に漏らさないため、
 *   NavHost ではなくこの状態から取る
 * @property chassis 車のシャーシ（上書き合成済み）。解決できなければ null
 */
data class CarDetailUiState(
    val carId: String,
    val isLoading: Boolean = true,
    val car: Car? = null,
    val chassis: Chassis? = null,
    val sheets: List<SheetListItem> = emptyList(),
    val sheetDialog: SheetDialogState? = null
)
