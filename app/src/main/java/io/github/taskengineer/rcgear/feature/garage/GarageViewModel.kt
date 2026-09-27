package io.github.taskengineer.rcgear.feature.garage

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.domain.model.Car
import io.github.taskengineer.rcgear.domain.model.Chassis
import io.github.taskengineer.rcgear.domain.repository.CarRepository
import io.github.taskengineer.rcgear.domain.repository.ChassisRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * GARAGE 画面（車一覧）の ViewModel（G-1）。
 *
 * 車は `chassisId` しか持たないので、表示にはシャーシDB（上書き合成済み）との
 * 突き合わせが必要になる。その結合をここで 1 回だけ行い、画面には
 * 解決済みの [GarageCarItem] を渡す。
 *
 * アーカイブ済みを含めて購読し、絞り込みはこの ViewModel で行う。
 * `observeCars(includeArchived = false)` を使うと「アーカイブ タブの件数」を
 * 出すためにもう 1 本 Flow を購読することになるため。
 */
@HiltViewModel
class GarageViewModel @Inject constructor(
    carRepository: CarRepository,
    chassisRepository: ChassisRepository
) : ViewModel() {

    private val filter = MutableStateFlow(GarageFilter.ACTIVE)

    val uiState: StateFlow<GarageUiState> = combine(
        carRepository.observeCars(includeArchived = true),
        chassisRepository.getAllMakers(),
        filter
    ) { cars, makers, currentFilter ->
        val chassisById = makers.asSequence()
            .flatMap { it.chassis }
            .associateBy { it.id }
        val visible = cars.filter { car ->
            when (currentFilter) {
                GarageFilter.ACTIVE -> !car.isArchived
                GarageFilter.ARCHIVED -> car.isArchived
            }
        }
        GarageUiState(
            isLoading = false,
            cars = visible.map { car -> car.toItem(chassisById[car.chassisId]) },
            filter = currentFilter,
            archivedCount = cars.count { it.isArchived }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GarageUiState()
    )

    fun onFilterChange(newFilter: GarageFilter) {
        filter.value = newFilter
    }

    private fun Car.toItem(chassis: Chassis?) = GarageCarItem(
        id = id,
        name = name,
        chassisId = chassisId,
        chassis = chassis,
        note = note,
        isArchived = isArchived
    )
}

/** 一覧のフィルター種別（フィルタータブに対応） */
enum class GarageFilter(@StringRes val labelRes: Int) {
    ACTIVE(R.string.garage_filter_active),
    ARCHIVED(R.string.garage_filter_archived)
}

/**
 * 一覧に出す車 1 台。
 *
 * @property chassis   解決できたシャーシ（上書き合成済み）。**見つからなければ null**。
 *   同梱DBから id が消えることは無い（AGENTS.md §4）が、ユーザー定義シャーシ（F-5）や
 *   壊れたインポートで起こりうる。そのときは [chassisId] をそのまま出す
 */
data class GarageCarItem(
    val id: String,
    val name: String,
    val chassisId: String,
    val chassis: Chassis?,
    val note: String?,
    val isArchived: Boolean
)

/**
 * @property cars          フィルター適用後の一覧。更新の新しい順（Repository の並び）
 * @property archivedCount フィルターに関係ないアーカイブ済みの件数（タブのバッジ用）
 */
data class GarageUiState(
    val isLoading: Boolean = true,
    val cars: List<GarageCarItem> = emptyList(),
    val filter: GarageFilter = GarageFilter.ACTIVE,
    val archivedCount: Int = 0
)
