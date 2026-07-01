package io.github.taskengineer.rcgear.feature.setups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.taskengineer.rcgear.data.repository.ChassisRepository
import io.github.taskengineer.rcgear.data.repository.SetupRepository
import io.github.taskengineer.rcgear.domain.model.SavedSetup
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * SETUPS 画面（保存セッティング一覧）の ViewModel。
 *
 * セッティング一覧とシャーシDBを合成し、表示用のリストアイテム
 * （セッティング + シャーシ表示名）を作る。
 */
@HiltViewModel
class SetupsViewModel @Inject constructor(
    setupRepository: SetupRepository,
    chassisRepository: ChassisRepository
) : ViewModel() {

    val uiState: StateFlow<SetupsUiState> = combine(
        setupRepository.observeAll(),
        chassisRepository.getAllMakers()
    ) { setups, makers ->
        // chassisId → (メーカー名, シャーシ名) の索引を作ってから合成する
        val chassisIndex = buildMap {
            makers.forEach { maker ->
                maker.chassis.forEach { chassis ->
                    put(chassis.id, maker.name to chassis.name)
                }
            }
        }
        SetupsUiState(
            isLoading = false,
            setups = setups.map { setup ->
                val entry = chassisIndex[setup.chassisId]
                SetupListItem(
                    setup = setup,
                    makerName = entry?.first,
                    chassisName = entry?.second
                )
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SetupsUiState()
    )
}

/**
 * @property isLoading 初回ロード中
 * @property setups    更新日時の新しい順
 */
data class SetupsUiState(
    val isLoading: Boolean = true,
    val setups: List<SetupListItem> = emptyList()
)

/**
 * 一覧の1行分。シャーシがDBから見つからない場合は chassisName = null
 * （chassisId をそのまま表示するフォールバックに使う）。
 */
data class SetupListItem(
    val setup: SavedSetup,
    val makerName: String?,
    val chassisName: String?
)
