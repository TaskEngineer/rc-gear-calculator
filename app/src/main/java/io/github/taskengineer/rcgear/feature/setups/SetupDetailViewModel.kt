package io.github.taskengineer.rcgear.feature.setups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.taskengineer.rcgear.core.common.CalcRequestBus
import io.github.taskengineer.rcgear.domain.calculator.GearCalculator
import io.github.taskengineer.rcgear.domain.model.Chassis
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput
import io.github.taskengineer.rcgear.domain.model.GearCalculationResult
import io.github.taskengineer.rcgear.domain.model.SavedSetup
import io.github.taskengineer.rcgear.domain.repository.ChassisRepository
import io.github.taskengineer.rcgear.domain.repository.PreferencesRepository
import io.github.taskengineer.rcgear.domain.repository.SetupRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * セッティング詳細画面の ViewModel（PLAN Step 9）。
 *
 * 中核はスナップショット差分の可視化（PLAN 9.5）:
 * - 保存時に凍結した internalRatioSnapshot と、現在のシャーシDB値を比較
 * - 差があれば両方の計算結果を出し「保存時 / 現在」で並べて見せる
 */
@HiltViewModel
class SetupDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val setupRepository: SetupRepository,
    private val chassisRepository: ChassisRepository,
    private val preferencesRepository: PreferencesRepository,
    private val calcRequestBus: CalcRequestBus
) : ViewModel() {

    // ナビゲーション引数 "setups/{setupId}" から取得
    private val setupId: Long = checkNotNull(savedStateHandle["setupId"])

    private val _uiState = MutableStateFlow(SetupDetailUiState())
    val uiState: StateFlow<SetupDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val setup = setupRepository.getById(setupId)
            if (setup == null) {
                // 削除直後に戻ってきた等のケース。閉じるだけ
                _uiState.update { it.copy(isLoading = false, notFound = true) }
                return@launch
            }

            val chassis = chassisRepository.getChassisById(setup.chassisId)
            val balanceFdr = preferencesRepository.userPreferences.first().balanceFdr

            // 保存時のスナップショット比で計算
            val snapshotResult = calculate(setup, setup.internalRatioSnapshot, balanceFdr)

            // 現在のDB値が異なる場合のみ「現在値での計算」も行う
            val currentRatio = chassis?.internalRatio
            val currentResult = currentRatio
                ?.takeIf { it != setup.internalRatioSnapshot }
                ?.let { calculate(setup, it, balanceFdr) }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    setup = setup,
                    chassis = chassis,
                    snapshotResult = snapshotResult,
                    currentResult = currentResult
                )
            }
        }
    }

    // ----- CALC への流し込み（PLAN 5.3） -----

    /**
     * このセッティングを CALC に流し込む準備をする。
     * 実際の画面遷移は UI 側（NavHost）が行う。
     */
    fun onLoadToCalc() {
        _uiState.value.setup?.let { calcRequestBus.send(it) }
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
            setupRepository.delete(setupId)
            _uiState.update { it.copy(showDeleteConfirm = false, isDeleted = true) }
        }
    }

    /**
     * 保存値から計算する。値が有効範囲外なら計算せず null を返す（REF-1 / BUG-2）。
     *
     * インポート時の検証（ImportDataUseCase）で範囲外は弾くようになったが、
     * それ以前に取り込まれた行が DB に残っている可能性がある。ここで例外を
     * 投げると詳細画面を開いただけでアプリが落ち、しかもその行を削除する
     * 画面にたどり着けなくなるため、結果を伏せるだけにとどめる。
     */
    private fun calculate(
        setup: SavedSetup,
        internalRatio: Double,
        balanceFdr: Double
    ): GearCalculationResult? {
        val isValid = GearCalculationInput.isValid(
            pinion = setup.pinion,
            spur = setup.spur,
            internalRatio = internalRatio,
            kv = setup.kv,
            cells = setup.cells,
            tireMm = setup.tireMm
        )
        if (!isValid) return null
        return GearCalculator.calculate(
            GearCalculationInput(
                pinion = setup.pinion,
                spur = setup.spur,
                internalRatio = internalRatio,
                kv = setup.kv,
                cells = setup.cells,
                tireMm = setup.tireMm
            ),
            balanceFdr
        )
    }
}

/**
 * @property setup          表示対象。notFound 時は null
 * @property chassis        現在のシャーシDB値（上書き合成済み）。DBから消えていたら null
 * @property snapshotResult 保存時スナップショット比での計算結果
 * @property currentResult  現在のDB値での計算結果。スナップショットと同値なら null（差分なし）
 * @property isDeleted      削除完了。UI 側はこれを見て前の画面に戻る
 * @property notFound       セッティングが見つからない（削除済みIDへの遷移など）
 */
data class SetupDetailUiState(
    val isLoading: Boolean = true,
    val setup: SavedSetup? = null,
    val chassis: Chassis? = null,
    val snapshotResult: GearCalculationResult? = null,
    val currentResult: GearCalculationResult? = null,
    val showDeleteConfirm: Boolean = false,
    val isDeleted: Boolean = false,
    val notFound: Boolean = false
)
