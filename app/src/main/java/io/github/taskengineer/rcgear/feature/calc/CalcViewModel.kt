package io.github.taskengineer.rcgear.feature.calc

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.ui.UiText
import io.github.taskengineer.rcgear.domain.calculator.GearCalculator
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput
import io.github.taskengineer.rcgear.domain.model.Maker
import io.github.taskengineer.rcgear.domain.model.SetupValue
import io.github.taskengineer.rcgear.domain.model.UserPreferences
import io.github.taskengineer.rcgear.domain.model.toGearInput
import io.github.taskengineer.rcgear.domain.repository.CarRepository
import io.github.taskengineer.rcgear.domain.repository.ChassisRepository
import io.github.taskengineer.rcgear.domain.repository.PreferencesRepository
import io.github.taskengineer.rcgear.domain.repository.SetupSheetRepository
import io.github.taskengineer.rcgear.navigation.calcRoute
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
 * ### シートとの双方向連携（G-5）
 * - **シート → CALC**: ルート引数 `Calc(sheetId)` で開かれたら、そのシートの
 *   ギアセクションの値を初期値にする。値はシートから読み直す（画面間で状態を運ばない）
 * - **CALC → シート**: [onApplyToSheet] で現在の入力をそのシートに書き戻す。
 *   書き戻すのは CALC が持っている 5 項目だけで、内部減速比は触らない
 *   （CALC では編集できない ＝ シャーシDB由来の値なので、上書きすると
 *   「シートに焼き込んだ値」を勝手に変えることになる）
 *
 * 計算は純粋関数で 16ms を大きく下回るため、debounce せず入力のたびに同期実行する（PLAN 9.2）。
 */
@HiltViewModel
class CalcViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chassisRepository: ChassisRepository,
    private val preferencesRepository: PreferencesRepository,
    private val sheetRepository: SetupSheetRepository,
    private val carRepository: CarRepository
) : ViewModel() {

    /** 流し込み元のシート。null なら素のスクラッチパッド（U-3 / G-5） */
    private val sheetId: String? = savedStateHandle.calcRoute().sheetId

    private val _uiState = MutableStateFlow(CalcUiState())
    val uiState: StateFlow<CalcUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // 1. 初期値を決めてから DB 購読を始める。
            //    こうすることで「デフォルト値が一瞬見えてから前回値に変わる」チラつきを防ぐ。
            val prefs = preferencesRepository.userPreferences.first()
            val fed = sheetId?.let { loadSheetValues(it) }
            setState { state ->
                val restored = state.withInitialValues(prefs)
                fed?.applyTo(restored) ?: restored
            }
            // シートから来たならそのシャーシを選ぶ。無ければ前回選択を復元する
            val initialChassisId = fed?.chassisId ?: prefs.lastSelectedChassisId

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

        // 流し込み元のシートが消えたら、書き戻し先が無いのでバナーを畳む（BUG-6）。
        // CALC はタブなので開きっぱなしのまま GARAGE や CONFIG からシートを消せてしまう。
        // ここで購読していないと「存在しないシートに反映」が押せる状態が残る。
        if (sheetId != null) {
            viewModelScope.launch {
                sheetRepository.observeSheet(sheetId).collect { sheet ->
                    // 消えたときだけ畳む。ここで文脈を作り直しはしない
                    // （初期化は init の loadSheetValues が唯一の入り口）
                    if (sheet == null) {
                        _uiState.update { it.copy(sheetContext = null) }
                    }
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
     * 流し込み元のシートを読む。
     *
     * 値はシート自身から読み直す（画面から引き渡さない）。ルート引数に載るのは id だけなので、
     * プロセス death のあとでも同じ結果になる。
     */
    private suspend fun loadSheetValues(id: String): SheetFeed? {
        val sheet = sheetRepository.getSheet(id) ?: return null
        val car = carRepository.getCar(sheet.sheet.carId)
        return SheetFeed(
            sheetId = id,
            sheetName = sheet.sheet.name,
            carName = car?.name.orEmpty(),
            chassisId = car?.chassisId,
            // 1 項目でも欠けていれば計算入力にならない。その場合は値だけ拾えるものを使う
            input = sheet.values.toGearInput(),
            pinion = sheet.values.intOf("pinion"),
            spur = sheet.values.intOf("spur"),
            kv = sheet.values.intOf("motorKv"),
            cells = sheet.values.intOf("cells"),
            tireMm = sheet.values.intOf("tireMm")
        )
    }

    /**
     * 現在の入力を流し込み元のシートに書き戻す（G-5）。
     *
     * 内部減速比は書かない（CALC では編集できず、シャーシDB由来の焼き込み値なので）。
     * 変わっていない項目も含めて 5 項目を upsert する — EAV なので 1 項目 1 行の
     * 上書きで済み、差分計算のために元の値を持ち回る必要がない。
     *
     * 書き戻し先が消えていた場合（BUG-6）は、押した瞬間に消えた等の取りこぼしなので
     * 1 項目目で止めて文脈を畳む。押せてしまったこと自体が想定外なので、
     * 黙って何もせずに終わらせず理由を出す。
     */
    fun onApplyToSheet() {
        val context = _uiState.value.sheetContext ?: return
        val state = _uiState.value
        viewModelScope.launch {
            val applied = listOf(
                "pinion" to SetupValue.IntV(state.pinion),
                "spur" to SetupValue.IntV(state.spur),
                "motorKv" to SetupValue.IntV(state.kv),
                "cells" to SetupValue.IntV(state.cells),
                "tireMm" to SetupValue.IntV(state.tireMm)
                // all は最初の false で止まる。消えたシートに残りを書きに行っても無駄なので
                // ここでは短絡が正しい
            ).all { (key, value) -> sheetRepository.setValue(context.sheetId, key, value) }
            _uiState.update {
                if (applied) {
                    it.copy(
                        message = UiText.Res(
                            R.string.calc_applied_to_sheet,
                            listOf(context.sheetName)
                        )
                    )
                } else {
                    it.copy(
                        sheetContext = null,
                        message = UiText.Res(R.string.calc_sheet_gone)
                    )
                }
            }
        }
    }

    /** スナックバーを出し終えた */
    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
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

    /** 流し込み元のシートから読んだ値。[applyTo] で UiState に載せる */
    private data class SheetFeed(
        val sheetId: String,
        val sheetName: String,
        val carName: String,
        val chassisId: String?,
        val input: GearCalculationInput?,
        val pinion: Int?,
        val spur: Int?,
        val kv: Int?,
        val cells: Int?,
        val tireMm: Int?
    ) {
        /**
         * 空欄の項目は前回値（DataStore 由来）を残す。
         * 0 で埋めると「シートに 0T と書いてある」ように見えてしまう。
         */
        fun applyTo(state: CalcUiState): CalcUiState = state.copy(
            pinion = pinion ?: state.pinion,
            spur = spur ?: state.spur,
            kv = kv ?: state.kv,
            cells = cells ?: state.cells,
            tireMm = tireMm ?: state.tireMm,
            sheetContext = CalcSheetContext(
                sheetId = sheetId,
                sheetName = sheetName,
                carName = carName,
                isComplete = input != null
            )
        )
    }

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
