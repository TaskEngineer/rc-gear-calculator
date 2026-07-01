package io.github.taskengineer.rcgear.feature.db

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.taskengineer.rcgear.data.repository.ChassisRepository
import io.github.taskengineer.rcgear.domain.model.Maker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * DB 画面（シャーシDB管理）の ViewModel（PLAN Step 10）。
 *
 * 上書き合成済みのシャーシ一覧をフィルター付きで提供する。
 * 上書きの追加・リセットは編集画面（ChassisEditViewModel）が行い、
 * その変更は Room の Flow 経由でこの一覧に自動反映される。
 */
@HiltViewModel
class DbViewModel @Inject constructor(
    chassisRepository: ChassisRepository
) : ViewModel() {

    private val filter = MutableStateFlow(DbFilter.ALL)

    val uiState: StateFlow<DbUiState> = combine(
        chassisRepository.getAllMakers(),
        filter
    ) { makers, currentFilter ->
        val filtered = when (currentFilter) {
            DbFilter.ALL -> makers
            DbFilter.EDITED -> makers
                .map { maker -> maker.copy(chassis = maker.chassis.filter { it.isUserEdited }) }
                .filter { it.chassis.isNotEmpty() }
        }
        DbUiState(
            isLoading = false,
            makers = filtered,
            filter = currentFilter,
            editedCount = makers.sumOf { maker -> maker.chassis.count { it.isUserEdited } }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DbUiState()
    )

    fun onFilterChange(newFilter: DbFilter) {
        filter.value = newFilter
    }
}

/** 一覧のフィルター種別（フィルタータブに対応） */
enum class DbFilter(val label: String) {
    ALL("すべて"),
    EDITED("編集済み")
}

/**
 * @property makers      フィルター適用後のメーカー一覧（上書き合成済み）
 * @property editedCount フィルターに関係ない全体の編集済み件数（タブのバッジ用）
 */
data class DbUiState(
    val isLoading: Boolean = true,
    val makers: List<Maker> = emptyList(),
    val filter: DbFilter = DbFilter.ALL,
    val editedCount: Int = 0
)
