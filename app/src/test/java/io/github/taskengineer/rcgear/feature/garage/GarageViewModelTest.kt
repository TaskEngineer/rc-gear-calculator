package io.github.taskengineer.rcgear.feature.garage

import io.github.taskengineer.rcgear.domain.model.Car
import io.github.taskengineer.rcgear.fake.FakeCarRepository
import io.github.taskengineer.rcgear.fake.FakeChassisRepository
import io.github.taskengineer.rcgear.testing.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * [GarageViewModel] のテスト（G-1）。
 *
 * この ViewModel の仕事は「車（chassisId しか持たない）とシャーシDBの突き合わせ」と
 * 「アーカイブの絞り込み」の 2 つだけなので、そこを見る。
 * 突き合わせに失敗した場合（chassis = null）に落ちないことが一番大事
 * — 一覧全体が空白になる形の失敗になるため。
 */
class GarageViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `list_車にシャーシDBの情報が結合される`() = runTest {
        val vm = viewModel(cars = listOf(car(id = "car-1", chassisId = "tamiya_ta08")))
        advanceUntilIdle()

        val item = vm.uiState.value.cars.single()
        assertEquals("car-1", item.id)
        assertEquals(FakeChassisRepository.TA08.name, item.chassis?.name)
    }

    @Test
    fun `list_シャーシが見つからない車も一覧に出る`() = runTest {
        // ユーザー定義シャーシ（F-5 で入る）や壊れたインポートで起こりうる。
        // ここで落としたり行を消したりすると、ユーザーには「車が消えた」ように見える。
        val vm = viewModel(cars = listOf(car(id = "car-1", chassisId = "unknown_id")))
        advanceUntilIdle()

        val item = vm.uiState.value.cars.single()
        assertNull(item.chassis)
        assertEquals("unknown_id", item.chassisId)
    }

    @Test
    fun `filter_既定ではアーカイブ済みを出さない`() = runTest {
        val vm = viewModel(
            cars = listOf(
                car(id = "car-1"),
                car(id = "car-2", isArchived = true)
            )
        )
        advanceUntilIdle()

        assertEquals(listOf("car-1"), vm.uiState.value.cars.map { it.id })
        assertEquals("アーカイブ件数はフィルターに関係なく数える", 1, vm.uiState.value.archivedCount)
    }

    @Test
    fun `filter_アーカイブタブではアーカイブ済みだけ出す`() = runTest {
        val vm = viewModel(
            cars = listOf(
                car(id = "car-1"),
                car(id = "car-2", isArchived = true)
            )
        )
        advanceUntilIdle()

        vm.onFilterChange(GarageFilter.ARCHIVED)
        advanceUntilIdle()

        assertEquals(listOf("car-2"), vm.uiState.value.cars.map { it.id })
    }

    @Test
    fun `list_車が無いときは空で読込完了になる`() = runTest {
        val vm = viewModel(cars = emptyList())
        advanceUntilIdle()

        assertTrue(vm.uiState.value.cars.isEmpty())
        assertFalse(vm.uiState.value.isLoading)
    }

    // ----- helpers -----

    private fun car(
        id: String,
        chassisId: String = "tamiya_tt02",
        isArchived: Boolean = false
    ) = Car(
        id = id,
        name = "車 $id",
        chassisId = chassisId,
        isArchived = isArchived,
        createdAt = 1_000L,
        updatedAt = 1_000L
    )

    /**
     * `uiState` は `stateIn(WhileSubscribed)` なので、**購読者がいないと一度も流れない**
     * （初期値のまま `isLoading = true` で止まる）。テスト側で購読を始めておく。
     */
    private fun TestScope.viewModel(cars: List<Car>): GarageViewModel {
        val vm = GarageViewModel(
            carRepository = FakeCarRepository(initial = cars),
            chassisRepository = FakeChassisRepository()
        )
        backgroundScope.launch { vm.uiState.collect { } }
        return vm
    }
}
