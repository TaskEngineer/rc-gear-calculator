package io.github.taskengineer.rcgear.feature.sheet

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.ui.ScreenEvent
import io.github.taskengineer.rcgear.core.ui.ScreenEvents
import io.github.taskengineer.rcgear.core.ui.UiText
import io.github.taskengineer.rcgear.domain.diff.FieldDiff
import io.github.taskengineer.rcgear.domain.diff.SheetDiff
import io.github.taskengineer.rcgear.domain.model.ChassisDefaults
import io.github.taskengineer.rcgear.domain.model.SetupValues
import io.github.taskengineer.rcgear.domain.repository.CarRepository
import io.github.taskengineer.rcgear.domain.repository.ChassisRepository
import io.github.taskengineer.rcgear.domain.repository.SetupSheetRepository
import io.github.taskengineer.rcgear.domain.schema.TouringSetupSchema
import io.github.taskengineer.rcgear.navigation.sheetCompareRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * シートの比較画面の ViewModel（G-6）。
 *
 * 3 つの比較軸（[DiffAxis]）は相手が違うだけで、差分そのものは
 * `:core:domain` の `SheetDiff.compare()` 1 本で出る。この ViewModel の仕事は
 * **相手の束を用意すること**と、結果をセクションごとにまとめることだけ。
 *
 * | 軸 | 相手 |
 * |---|---|
 * | A | シャーシ標準（`TouringSetupSchema.initialValues`。キット標準から変えた所） |
 * | B | ベースラインシート（**前回のセットから何を変えたか**。セッティングシートの核心） |
 * | C | 同じ車の任意のシート |
 *
 * 軸 C の相手を同じ車に限っているのは、シャーシが違うシート同士を並べても
 * 「内部減速比が違う」のような当たり前の差で埋まるため。
 */
@HiltViewModel
class SheetCompareViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sheetRepository: SetupSheetRepository,
    private val carRepository: CarRepository,
    private val chassisRepository: ChassisRepository
) : ViewModel() {

    private val sheetId: String = savedStateHandle.sheetCompareRoute().sheetId

    private val _uiState = MutableStateFlow(SheetCompareUiState())
    val uiState: StateFlow<SheetCompareUiState> = _uiState.asStateFlow()

    private val screenEvents = ScreenEvents()

    /** 画面を閉じる等の一度きりの出来事（U-2） */
    val events: Flow<ScreenEvent> = screenEvents.flow

    /** このシートの値。比較の主役 */
    private var values: SetupValues = SetupValues.EMPTY

    /** 軸 A の相手を作るためのシャーシ既定値 */
    private var chassisDefaults: ChassisDefaults = ChassisDefaults.NONE

    /** 軸 B の相手（ベースライン）。未設定なら null */
    private var baseline: Pair<String, SetupValues>? = null

    /** 軸 C で選べる相手。id → 値 */
    private var candidateValues: Map<String, SetupValues> = emptyMap()

    init {
        viewModelScope.launch {
            val loaded = sheetRepository.getSheet(sheetId)
            if (loaded == null) {
                screenEvents.emit(ScreenEvent.NavigateBack)
                return@launch
            }
            values = loaded.values
            val car = carRepository.getCar(loaded.sheet.carId)
            val chassis = car?.chassisId?.let { chassisRepository.getChassisById(it) }
            chassisDefaults = chassis?.let { ChassisDefaults.from(it) } ?: ChassisDefaults.NONE

            val siblings = sheetRepository.observeSheets(loaded.sheet.carId).first()
                .filter { it.id != sheetId }
            // 相手の値は 1 枚ずつ読む。シートは 1 台あたり数十枚なので一括で構わない
            candidateValues = siblings.associate { sheet ->
                sheet.id to (sheetRepository.getSheet(sheet.id)?.values ?: SetupValues.EMPTY)
            }
            baseline = loaded.sheet.baselineId?.let { id ->
                val name = siblings.firstOrNull { it.id == id }?.name
                val baselineValues = candidateValues[id]
                if (name != null && baselineValues != null) name to baselineValues else null
            }

            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    sheetName = loaded.sheet.name,
                    hasBaseline = baseline != null,
                    // ベースラインが無いシート（1 枚目）で軸 B を既定にすると何も出ない
                    axis = if (baseline != null) DiffAxis.BASELINE else DiffAxis.CHASSIS,
                    candidates = siblings.map { BaselineCandidate(id = it.id, name = it.name) },
                    // 軸 C は最初の候補を既定にしておく（選ばせる前に何か出したほうが分かる）
                    otherSheetId = siblings.firstOrNull()?.id
                )
            }
            recompute()
        }
    }

    fun onAxisChange(axis: DiffAxis) {
        _uiState.update { it.copy(axis = axis) }
        recompute()
    }

    fun onOtherSheetSelect(id: String) {
        _uiState.update { it.copy(otherSheetId = id) }
        recompute()
    }

    /** 「同じ値も表示」の切り替え。既定は変わった所だけ（軸 B の本命の使い方） */
    fun onIncludeSameChange(includeSame: Boolean) {
        _uiState.update { it.copy(includeSame = includeSame) }
        recompute()
    }

    /**
     * 相手を決めて差分を取り直す。
     *
     * 軸 A だけ `compareToChassisDefault` を通すのは、「シャーシ標準」の定義
     * （シャーシDBの値 + レジストリ既定値）をドメイン側に置いたままにするため。
     * ここで `initialValues` を組み立てると、定義が 2 箇所に分かれる。
     */
    private fun recompute() {
        val state = _uiState.value
        val label: UiText?
        val diffs: List<FieldDiff>
        when (state.axis) {
            DiffAxis.CHASSIS -> {
                label = UiText.Res(R.string.sheet_compare_axis_chassis)
                diffs = SheetDiff.compareToChassisDefault(
                    values = values,
                    defaults = chassisDefaults,
                    includeSame = state.includeSame
                )
            }

            DiffAxis.BASELINE -> {
                val target = baseline
                label = target?.let { UiText.Raw(it.first) }
                diffs = target?.let { SheetDiff.compare(values, it.second, state.includeSame) }
                    ?: emptyList()
            }

            DiffAxis.OTHER -> {
                val id = state.otherSheetId
                val target = id?.let { candidateValues[it] }
                label = id
                    ?.let { key -> state.candidates.firstOrNull { it.id == key }?.name }
                    ?.let { UiText.Raw(it) }
                diffs = target?.let { SheetDiff.compare(values, it, state.includeSame) }
                    ?: emptyList()
            }
        }
        _uiState.update { it.copy(otherLabel = label, groups = diffs.groupBySection()) }
    }

    /**
     * セクションごとにまとめる。順序は差分（＝レジストリの宣言順）のまま保ち、
     * レジストリに無いキーは末尾の「未分類」にまとめる。
     */
    private fun List<FieldDiff>.groupBySection(): List<DiffGroup> {
        val bySection = LinkedHashMap<String?, MutableList<FieldDiff>>()
        for (diff in this) {
            val sectionKey = TouringSetupSchema.sectionOf(diff.fieldKey)?.key
            bySection.getOrPut(sectionKey) { mutableListOf() } += diff
        }
        // 未分類（null）は最後に回す
        return bySection.entries
            .sortedBy { it.key == null }
            .map { (sectionKey, rows) -> DiffGroup(sectionKey = sectionKey, rows = rows) }
    }
}

/** 比較軸（G-6）。`SheetDiff` の KDoc の A / B / C に対応する */
enum class DiffAxis(@StringRes val labelRes: Int) {
    /** 軸 A: キット標準から変えた所 */
    CHASSIS(R.string.sheet_compare_axis_chassis),

    /** 軸 B: 前回のセットから変えた所 */
    BASELINE(R.string.sheet_compare_axis_baseline),

    /** 軸 C: 任意のシートとの比較 */
    OTHER(R.string.sheet_compare_axis_other)
}

/**
 * 差分をセクション単位でまとめたもの。
 *
 * @property sectionKey レジストリのセクションキー。**null は「未分類」**
 *   （このバージョンが知らない項目）
 */
data class DiffGroup(
    val sectionKey: String?,
    val rows: List<FieldDiff>
)

/**
 * @property otherLabel 比較相手の表示名。軸 A は文言、軸 B / C はシート名（データ）なので
 *   [UiText] で両方を扱う
 * @property hasBaseline ベースラインが設定されているか。軸 B を選べるかの判定に使う
 * @property groups     セクションごとの差分。空なら「差が無い」か「相手がいない」
 */
data class SheetCompareUiState(
    val isLoading: Boolean = true,
    val sheetName: String = "",
    val axis: DiffAxis = DiffAxis.BASELINE,
    val otherLabel: UiText? = null,
    val hasBaseline: Boolean = false,
    val candidates: List<BaselineCandidate> = emptyList(),
    val otherSheetId: String? = null,
    val includeSame: Boolean = false,
    val groups: List<DiffGroup> = emptyList()
)
