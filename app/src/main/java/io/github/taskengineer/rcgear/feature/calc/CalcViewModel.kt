package io.github.taskengineer.rcgear.feature.calc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.taskengineer.rcgear.domain.calculator.GearCalculator
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput
import io.github.taskengineer.rcgear.domain.model.Maker
import io.github.taskengineer.rcgear.domain.model.UserPreferences
import io.github.taskengineer.rcgear.domain.repository.ChassisRepository
import io.github.taskengineer.rcgear.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * CALC 画面の ViewModel。
 *
 * 責務:
 * - シャーシDB（上書き合成済み）とユーザー設定の購読
 * - スライダー入力の状態管理と GearCalculator による再計算
 * - 前回終了時の状態復元（DataStore）と保存
 *
 * シートからの「流し込み」（ルート引数）と保存は M-3 で一旦外した。
 * 受け皿だった SETUPS がシート（GARAGE）に置き換わるため、Phase 3 の G-5 で
 * `Calc(sheetId)` として入れ直す。
 *
 * 計算は純粋関数で 16ms を大きく下回るため、debounce せず入力のたびに同期実行する（PLAN 9.2）。
 */
@HiltViewModel
class CalcViewModel @Inject constructor(
    private val chassisRepository: ChassisRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalcUiState())
    val uiState: StateFlow<CalcUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // 1. 初期値を決めてから DB 購読を始める。
            //    こうすることで「デフォルト値が一瞬見えてから前回値に変わる」チラつきを防ぐ。
            val prefs = preferencesRepository.userPreferences.first()
            setState { it.withInitialValues(prefs) }
            val initialChassisId = prefs.lastSelectedChassisId

            // 2. シャーシDBを購読。上書きの変更（DB画面での編集）にもリアルタイム追従する。
            chassisRepository.getAllMakers().collect { makers ->
                setState { state ->
                    // 既に選択済みならそのIDを、初回なら 1. で決めたIDを解決する
                    val targetId = state.selectedChassis?.chassis?.id ?: initialChassisId
                    state.copy(
                        isLoading = false,
                        makers = makers,
                        selectedChassis = targetId?.let { id -> findChassis(makers, id) }
                    )
                }
            }
        }

        // 設定変更（CONFIG 画面での mph 表示切替・基準FDR変更）に追従する
        viewModelScope.launch {
            preferencesRepository.userPreferences.collect { prefs ->
                setState { state ->
                    state.copy(
                        showMphAlongside = prefs.showMphAlongside,
                        animationEnabled = prefs.animationEnabled,
                        balanceFdr = prefs.balanceFdr
                    )
                }
            }
        }
    }

    /**
     * 入力に影響する状態更新の唯一の入り口（U-4 / DEBT-10）。
     *
     * `transform` は「状態を作るだけ」の純粋な関数に限る。再計算・クランプは
     * ここで 1 回だけ行う。`MutableStateFlow.update {}` の中で再計算していたのを
     * 外に出したのは次の 2 つの理由:
     *  - `update` は CAS のリトライでラムダを何度も呼ぶ契約なので、
     *    重い処理や副作用（例外・永続化）を置く場所ではない
     *  - 「読んで・作って・書く」が 1 行に並ぶと、どこまでが状態遷移なのかが読めなくなる
     *
     * 状態変更は全てメインディスパッチャ上（UI コールバックと viewModelScope の
     * collect）なので、CAS ループ無しの read-modify-write で足りる。
     */
    private fun setState(transform: (CalcUiState) -> CalcUiState) {
        _uiState.value = recalculate(transform(_uiState.value))
    }

    /**
     * 前回終了時の値を載せた状態を返す。
     * 範囲外の値は [recalculate] 側でクランプされる（REF-1）。
     */
    private fun CalcUiState.withInitialValues(prefs: UserPreferences) = copy(
        pinion = prefs.lastPinion,
        spur = prefs.lastSpur,
        kv = prefs.lastKv,
        cells = prefs.lastCells,
        tireMm = prefs.lastTireMm,
        showMphAlongside = prefs.showMphAlongside,
        animationEnabled = prefs.animationEnabled,
        balanceFdr = prefs.balanceFdr
    )

    // ----- シャーシ選択 -----

    fun onChassisCardClick() {
        _uiState.update { it.copy(isChassisSheetOpen = true) }
    }

    fun onChassisSheetDismiss() {
        _uiState.update { it.copy(isChassisSheetOpen = false) }
    }

    /**
     * ボトムシートでシャーシが選択された。
     * Web 版と同様、タイヤ径はそのシャーシのデフォルト値に自動セットする。
     */
    fun onChassisSelected(chassisId: String) {
        // 見つからないシャーシは無視する（シートを開いたまま DB から消えた場合）
        val selected = findChassis(_uiState.value.makers, chassisId) ?: return
        setState { state ->
            state.copy(
                selectedChassis = selected,
                tireMm = selected.chassis.defaultTireMm,
                isChassisSheetOpen = false
            )
        }
        persistLastCalcState()
    }

    // ----- スライダー入力 -----
    // onValueChange のたびに再計算する（純粋関数なので軽い）。
    // DataStore への保存はスライダー操作確定時（onSliderChangeFinished）のみ。

    fun onPinionChange(value: Int) = setState { it.copy(pinion = value) }
    fun onSpurChange(value: Int) = setState { it.copy(spur = value) }
    fun onKvChange(value: Int) = setState { it.copy(kv = value) }
    fun onCellsChange(value: Int) = setState { it.copy(cells = value) }
    fun onTireMmChange(value: Int) = setState { it.copy(tireMm = value) }

    /** スライダーの操作が確定した（指が離れた）タイミングで前回状態として永続化する */
    fun onSliderChangeFinished() {
        persistLastCalcState()
    }

    // ----- 内部処理 -----

    /**
     * シャーシ未選択なら result = null、選択済みなら再計算した状態を返す。
     *
     * 入力値の防御について（REF-1 / BUG-1・BUG-2）:
     * 状態を変える経路（前回値の復元・シャーシ選択・スライダー）は
     * すべてここを通る。`GearCalculationInput` は範囲外で例外を
     * 投げるので、**計算の直前に一度だけクランプする**ことで全経路をまとめて守る。
     * クランプ後の値を state に書き戻すため、スライダーの表示と計算結果もズレない。
     */
    private fun recalculate(state: CalcUiState): CalcUiState {
        val clamped = state.clampInputs()
        val selected = clamped.selectedChassis ?: return clamped.copy(result = null)
        val internalRatio = selected.chassis.internalRatio
        // 内部減速比は上書き経由で 0 以下が入りうる。その場合は計算せず結果を伏せる
        // （クランプできる性質の値ではないため）。
        if (!GearCalculationInput.isValidInternalRatio(internalRatio)) {
            return clamped.copy(result = null)
        }
        val input = clamped.toCalculationInput(internalRatio)
        return clamped.copy(result = GearCalculator.calculate(input, clamped.balanceFdr))
    }

    /** 入力値を有効範囲に丸めた状態を返す。既に範囲内ならインスタンスをそのまま返す */
    private fun CalcUiState.clampInputs(): CalcUiState {
        val newPinion = GearCalculationInput.clampPinion(pinion)
        val newSpur = GearCalculationInput.clampSpur(spur)
        val newKv = GearCalculationInput.clampKv(kv)
        val newCells = GearCalculationInput.clampCells(cells)
        val newTireMm = GearCalculationInput.clampTireMm(tireMm)
        val unchanged = newPinion == pinion && newSpur == spur && newKv == kv &&
            newCells == cells && newTireMm == tireMm
        return if (unchanged) {
            this
        } else {
            copy(
                pinion = newPinion,
                spur = newSpur,
                kv = newKv,
                cells = newCells,
                tireMm = newTireMm
            )
        }
    }

    private fun CalcUiState.toCalculationInput(internalRatio: Double) =
        GearCalculationInput(
            pinion = pinion,
            spur = spur,
            internalRatio = internalRatio,
            kv = kv,
            cells = cells,
            tireMm = tireMm
        )

    private fun findChassis(makers: List<Maker>, chassisId: String): SelectedChassis? {
        makers.forEach { maker ->
            maker.chassis.firstOrNull { it.id == chassisId }?.let {
                return SelectedChassis(makerName = maker.name, chassis = it)
            }
        }
        return null
    }

    /** 現在の入力を「前回状態」として DataStore に保存する（次回起動時の復元用） */
    private fun persistLastCalcState() {
        val state = _uiState.value
        viewModelScope.launch {
            preferencesRepository.setLastCalcState(
                chassisId = state.selectedChassis?.chassis?.id,
                pinion = state.pinion,
                spur = state.spur,
                kv = state.kv,
                cells = state.cells,
                tireMm = state.tireMm
            )
        }
    }
}
