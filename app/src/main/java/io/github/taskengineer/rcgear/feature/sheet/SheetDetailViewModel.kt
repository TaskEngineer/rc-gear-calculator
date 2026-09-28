package io.github.taskengineer.rcgear.feature.sheet

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.taskengineer.rcgear.core.ui.ScreenEvent
import io.github.taskengineer.rcgear.core.ui.ScreenEvents
import io.github.taskengineer.rcgear.domain.calculator.GearCalculator
import io.github.taskengineer.rcgear.domain.model.Chassis
import io.github.taskengineer.rcgear.domain.model.ChassisTraits
import io.github.taskengineer.rcgear.domain.model.GearCalculationResult
import io.github.taskengineer.rcgear.domain.model.SessionConditions
import io.github.taskengineer.rcgear.domain.model.SetupSheet
import io.github.taskengineer.rcgear.domain.model.SetupValues
import io.github.taskengineer.rcgear.domain.model.toGearInput
import io.github.taskengineer.rcgear.domain.repository.CarRepository
import io.github.taskengineer.rcgear.domain.repository.ChassisRepository
import io.github.taskengineer.rcgear.domain.repository.PreferencesRepository
import io.github.taskengineer.rcgear.domain.repository.SetupSheetRepository
import io.github.taskengineer.rcgear.navigation.sheetDetailRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * セッティングシートの閲覧画面の ViewModel（G-3）。
 *
 * 画面が読むのは「シート 1 枚（ヘッダ + 値）」と、項目の出し分けに使う
 * 「その車のシャーシの素性」だけ。項目の定義はレジストリ（`:core:domain`）にあるので、
 * **項目が増えてもこの ViewModel は変わらない。**
 *
 * 車とシャーシはシートが流れてくるたびに単発取得する（`getChassisById` は M-7 で O(1)）。
 * `carId` はシートを読むまで分からないので、3 本の Flow を `combine` する形にはできない。
 *
 * ギアの計算結果（G-5）もここで出す。`SetupValues.toGearInput()` が bag と計算機の
 * 唯一の接続点で、1 項目でも欠けていれば `null`（＝結果を出さない）。
 * CALC を開かずにシート上で FDR と最高速が読めるようにするためで、
 * 計算そのものは純粋関数なので状態を増やさずに済む。
 */
@HiltViewModel
class SheetDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sheetRepository: SetupSheetRepository,
    private val carRepository: CarRepository,
    private val chassisRepository: ChassisRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val sheetId: String = savedStateHandle.sheetDetailRoute().sheetId

    private val _uiState = MutableStateFlow(SheetDetailUiState())
    val uiState: StateFlow<SheetDetailUiState> = _uiState.asStateFlow()

    private val screenEvents = ScreenEvents()

    /** 画面を閉じる等の一度きりの出来事（U-2） */
    val events: Flow<ScreenEvent> = screenEvents.flow

    private var sheet: SetupSheet? = null

    init {
        viewModelScope.launch {
            sheetRepository.observeSheet(sheetId).collect { loaded ->
                if (loaded == null) {
                    // 自分で削除した / 車ごと消えた
                    screenEvents.emit(ScreenEvent.NavigateBack)
                    return@collect
                }
                sheet = loaded.sheet
                val car = carRepository.getCar(loaded.sheet.carId)
                val chassis = car?.chassisId?.let { chassisRepository.getChassisById(it) }
                // 傾向バーの基準 FDR は CONFIG の設定に合わせる（CALC と同じ見え方にする）
                val balanceFdr = preferencesRepository.userPreferences.first().balanceFdr
                val gearResult = loaded.values.toGearInput()?.let { input ->
                    GearCalculator.calculate(input, balanceFdr)
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        sheetId = loaded.sheet.id,
                        name = loaded.sheet.name,
                        carName = car?.name.orEmpty(),
                        chassis = chassis,
                        traits = chassis?.traits ?: ChassisTraits.UNKNOWN,
                        conditions = loaded.sheet.conditions,
                        note = loaded.sheet.note,
                        isFavorite = loaded.sheet.isFavorite,
                        values = loaded.values,
                        gearResult = gearResult,
                        updatedAt = loaded.sheet.updatedAt
                    )
                }
            }
        }
    }

    /** お気に入り（一覧で目印になる）を切り替える */
    fun onFavoriteToggle() {
        val current = sheet ?: return
        viewModelScope.launch {
            sheetRepository.updateSheet(current.copy(isFavorite = !current.isFavorite))
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
        viewModelScope.launch {
            // このシートをベースラインにしている他のシートは残る（FK は SET NULL）
            sheetRepository.deleteSheet(sheetId)
            _uiState.update { it.copy(showDeleteConfirm = false) }
            screenEvents.emit(ScreenEvent.NavigateBack)
        }
    }
}

/**
 * @property chassis 車のシャーシ（上書き合成済み）。解決できなければ null
 * @property traits  項目の出し分けに使う素性。シャーシ不明なら
 *   [ChassisTraits.UNKNOWN]（＝全項目を出す）
 * @property values  シートの値の束。**表示の順序はレジストリが決める**ので、
 *   ここでは並べ替えない
 * @property gearResult ギアセクションの値から計算した結果（G-5）。
 *   値が欠けていれば null
 */
data class SheetDetailUiState(
    val isLoading: Boolean = true,
    val sheetId: String = "",
    val name: String = "",
    val carName: String = "",
    val chassis: Chassis? = null,
    val traits: ChassisTraits = ChassisTraits.UNKNOWN,
    val conditions: SessionConditions = SessionConditions(),
    val note: String? = null,
    val isFavorite: Boolean = false,
    val values: SetupValues = SetupValues.EMPTY,
    val gearResult: GearCalculationResult? = null,
    val updatedAt: Long = 0L,
    val showDeleteConfirm: Boolean = false
)
