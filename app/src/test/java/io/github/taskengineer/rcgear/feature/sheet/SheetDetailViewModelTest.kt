package io.github.taskengineer.rcgear.feature.sheet

import androidx.lifecycle.SavedStateHandle
import io.github.taskengineer.rcgear.core.ui.ScreenEvent
import io.github.taskengineer.rcgear.domain.model.Car
import io.github.taskengineer.rcgear.domain.model.SetupValue
import io.github.taskengineer.rcgear.domain.model.SetupValues
import io.github.taskengineer.rcgear.fake.FakeCarRepository
import io.github.taskengineer.rcgear.fake.FakeChassisRepository
import io.github.taskengineer.rcgear.fake.FakeSetupSheetRepository
import io.github.taskengineer.rcgear.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * [SheetDetailViewModel] のテスト（G-3）。
 *
 * 閲覧画面なので状態の組み立てが仕事の大半。見たいのは
 * 「シートが消えたら画面を閉じる」「削除がベースライン参照を壊さない」の 2 点で、
 * どちらもクラッシュか幽霊行として現れる種類の失敗。
 */
class SheetDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `load_シートと車とシャーシが載る`() = runTest {
        val sheets = FakeSetupSheetRepository()
        val sheetId = sheets.createSheet(
            carId = CAR_ID,
            name = "Rd1",
            values = SetupValues.of("pinion" to SetupValue.IntV(29))
        )
        val vm = viewModel(sheets = sheets, sheetId = sheetId)
        advanceUntilIdle()

        with(vm.uiState.value) {
            assertFalse(isLoading)
            assertEquals("Rd1", name)
            assertEquals("TT-02 #1", carName)
            assertEquals(FakeChassisRepository.TT02.name, chassis?.name)
            assertEquals(29, values.intOf("pinion"))
        }
    }

    @Test
    fun `load_シャーシが解決できなくても表示できる`() = runTest {
        // 素性が不明なら項目は全部出す（ChassisTraits.satisfies）。
        // ここで落ちると「シートが開けない」という形の失敗になる
        val sheets = FakeSetupSheetRepository()
        val sheetId = sheets.createSheet(carId = CAR_ID, name = "Rd1", values = SetupValues.EMPTY)
        val vm = viewModel(
            cars = FakeCarRepository(initial = listOf(car(chassisId = "unknown_id"))),
            sheets = sheets,
            sheetId = sheetId
        )
        advanceUntilIdle()

        assertNull(vm.uiState.value.chassis)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `load_シートが無ければすぐ戻る`() = runTest {
        val vm = viewModel(sheets = FakeSetupSheetRepository(), sheetId = "missing")
        advanceUntilIdle()

        assertEquals(ScreenEvent.NavigateBack, vm.events.first())
    }

    @Test
    fun `favorite_切り替えが保存され状態に反映される`() = runTest {
        val sheets = FakeSetupSheetRepository()
        val sheetId = sheets.createSheet(carId = CAR_ID, name = "Rd1", values = SetupValues.EMPTY)
        val vm = viewModel(sheets = sheets, sheetId = sheetId)
        advanceUntilIdle()

        vm.onFavoriteToggle()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isFavorite)
        assertTrue(sheets.stored.single().sheet.isFavorite)
    }

    @Test
    fun `delete_確認してから削除し画面を閉じる`() = runTest {
        val sheets = FakeSetupSheetRepository()
        val sheetId = sheets.createSheet(carId = CAR_ID, name = "Rd1", values = SetupValues.EMPTY)
        val vm = viewModel(sheets = sheets, sheetId = sheetId)
        advanceUntilIdle()

        vm.onDeleteClick()
        assertTrue(vm.uiState.value.showDeleteConfirm)

        vm.onDeleteConfirm()
        advanceUntilIdle()

        assertTrue(sheets.stored.isEmpty())
        assertEquals(ScreenEvent.NavigateBack, vm.events.first())
    }

    @Test
    fun `delete_ベースラインにしていたシートは残る`() = runTest {
        // 自己参照 FK は ON DELETE SET NULL。ベースラインを消しても
        // それを基準にしたシートまで消えてはいけない
        val sheets = FakeSetupSheetRepository()
        val baselineId = sheets.createSheet(carId = CAR_ID, name = "Rd1", values = SetupValues.EMPTY)
        val childId = sheets.createSheet(
            carId = CAR_ID,
            name = "Rd2",
            values = SetupValues.EMPTY,
            baselineId = baselineId
        )
        val vm = viewModel(sheets = sheets, sheetId = baselineId)
        advanceUntilIdle()

        vm.onDeleteConfirm()
        advanceUntilIdle()

        val remaining = sheets.stored.single()
        assertEquals(childId, remaining.id)
        assertNull(remaining.sheet.baselineId)
    }

    // ----- helpers -----

    private fun car(chassisId: String = "tamiya_tt02") = Car(
        id = CAR_ID,
        name = "TT-02 #1",
        chassisId = chassisId,
        createdAt = 1_000L,
        updatedAt = 1_000L
    )

    private fun viewModel(
        sheets: FakeSetupSheetRepository,
        sheetId: String,
        cars: FakeCarRepository = FakeCarRepository(initial = listOf(car()))
    ) = SheetDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("sheetId" to sheetId)),
        sheetRepository = sheets,
        carRepository = cars,
        chassisRepository = FakeChassisRepository()
    )

    private companion object {
        const val CAR_ID = "car-1"
    }
}
