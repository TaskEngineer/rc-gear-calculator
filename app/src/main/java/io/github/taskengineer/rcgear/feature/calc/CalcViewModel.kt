package io.github.taskengineer.rcgear.feature.calc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.taskengineer.rcgear.core.domain.GearCalculator
import io.github.taskengineer.rcgear.data.repository.ChassisRepository
import io.github.taskengineer.rcgear.data.repository.PreferencesRepository
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput
import io.github.taskengineer.rcgear.domain.model.Maker
import io.github.taskengineer.rcgear.domain.usecase.SaveSetupUseCase
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
 * - セッティング保存ダイアログのハンドリング
 *
 * 計算は純粋関数で 16ms を大きく下回るため、debounce せず入力のたびに同期実行する（PLAN 9.2）。
 */
@HiltViewModel
class CalcViewModel @Inject constructor(
    private val chassisRepository: ChassisRepository,
    private val preferencesRepository: PreferencesRepository,
    private val saveSetupUseCase: SaveSetupUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalcUiState())
    val uiState: StateFlow<CalcUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // 1. 前回終了時の状態を先に復元してから DB 購読を始める。
            //    こうすることで「デフォルト値が一瞬見えてから前回値に変わる」チラつきを防ぐ。
            val prefs = preferencesRepository.userPreferences.first()
            _uiState.update {
                it.copy(
                    pinion = prefs.lastPinion,
                    spur = prefs.lastSpur,
                    kv = prefs.lastKv,
                    cells = prefs.lastCells,
                    tireMm = prefs.lastTireMm,
                    showMphAlongside = prefs.showMphAlongside,
                    balanceFdr = prefs.balanceFdr
                )
            }
            val lastChassisId = prefs.lastSelectedChassisId

            // 2. シャーシDBを購読。上書きの変更（DB画面での編集）にもリアルタイム追従する。
            chassisRepository.getAllMakers().collect { makers ->
                _uiState.update { state ->
                    // 既に選択済みならそのIDを、初回なら前回終了時のIDを解決する
                    val targetId = state.selectedChassis?.chassis?.id ?: lastChassisId
                    val selected = targetId?.let { id -> findChassis(makers, id) }
                    recalculate(
                        state.copy(
                            isLoading = false,
                            makers = makers,
                            selectedChassis = selected
                        )
                    )
                }
            }
        }

        // 設定変更（CONFIG 画面での mph 表示切替・基準FDR変更）に追従する
        viewModelScope.launch {
            preferencesRepository.userPreferences.collect { prefs ->
                _uiState.update { state ->
                    recalculate(
                        state.copy(
                            showMphAlongside = prefs.showMphAlongside,
                            balanceFdr = prefs.balanceFdr
                        )
                    )
                }
            }
        }
    }

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
        _uiState.update { state ->
            val selected = findChassis(state.makers, chassisId) ?: return@update state
            recalculate(
                state.copy(
                    selectedChassis = selected,
                    tireMm = selected.chassis.defaultTireMm,
                    isChassisSheetOpen = false
                )
            )
        }
        persistLastCalcState()
    }

    // ----- スライダー入力 -----
    // onValueChange のたびに再計算する（純粋関数なので軽い）。
    // DataStore への保存はスライダー操作確定時（onSliderChangeFinished）のみ。

    fun onPinionChange(value: Int) = updateInput { it.copy(pinion = value) }
    fun onSpurChange(value: Int) = updateInput { it.copy(spur = value) }
    fun onKvChange(value: Int) = updateInput { it.copy(kv = value) }
    fun onCellsChange(value: Int) = updateInput { it.copy(cells = value) }
    fun onTireMmChange(value: Int) = updateInput { it.copy(tireMm = value) }

    /** スライダーの操作が確定した（指が離れた）タイミングで前回状態として永続化する */
    fun onSliderChangeFinished() {
        persistLastCalcState()
    }

    private inline fun updateInput(transform: (CalcUiState) -> CalcUiState) {
        _uiState.update { recalculate(transform(it)) }
    }

    // ----- 保存ダイアログ -----

    fun onSaveClick() {
        // シャーシ未選択では保存できない（UI 側でもボタンを無効化している）
        if (_uiState.value.selectedChassis == null) return
        _uiState.update { it.copy(saveDialog = SaveDialogState()) }
    }

    fun onSaveDialogNameChange(name: String) {
        _uiState.update { state ->
            state.copy(saveDialog = state.saveDialog?.copy(name = name, errorMessage = null))
        }
    }

    fun onSaveDialogDismiss() {
        _uiState.update { it.copy(saveDialog = null) }
    }

    fun onSaveDialogConfirm() {
        val state = _uiState.value
        val dialog = state.saveDialog ?: return
        val selected = state.selectedChassis ?: return
        val result = state.result ?: return
        if (dialog.isSaving) return

        _uiState.update { it.copy(saveDialog = dialog.copy(isSaving = true)) }

        viewModelScope.launch {
            val input = state.toCalculationInput(selected.chassis.internalRatio)
            when (saveSetupUseCase(dialog.name, selected.chassis.id, input, result)) {
                is SaveSetupUseCase.Result.Success -> _uiState.update {
                    it.copy(
                        saveDialog = null,
                        savedMessage = "「${dialog.name.trim()}」を保存しました"
                    )
                }

                SaveSetupUseCase.Result.BlankName -> _uiState.update {
                    it.copy(
                        saveDialog = it.saveDialog?.copy(
                            isSaving = false,
                            errorMessage = "名前を入力してください"
                        )
                    )
                }

                SaveSetupUseCase.Result.DuplicateName -> _uiState.update {
                    it.copy(
                        saveDialog = it.saveDialog?.copy(
                            isSaving = false,
                            errorMessage = "同じ名前のセッティングが既にあります"
                        )
                    )
                }
            }
        }
    }

    /** スナックバー表示後に呼び、メッセージの再表示を防ぐ */
    fun onSavedMessageShown() {
        _uiState.update { it.copy(savedMessage = null) }
    }

    // ----- 内部処理 -----

    /** シャーシ未選択なら result = null、選択済みなら再計算した状態を返す */
    private fun recalculate(state: CalcUiState): CalcUiState {
        val selected = state.selectedChassis ?: return state.copy(result = null)
        val input = state.toCalculationInput(selected.chassis.internalRatio)
        return state.copy(result = GearCalculator.calculate(input, state.balanceFdr))
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
