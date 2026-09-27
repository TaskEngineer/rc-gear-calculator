package io.github.taskengineer.rcgear.feature.garage

import androidx.lifecycle.SavedStateHandle
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.ui.ScreenEvent
import io.github.taskengineer.rcgear.core.ui.UiText
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * [CarDetailViewModel] のテスト（G-2）。
 *
 * 見たいのは「シートを起こす 2 つの経路」の違い:
 *  - 新規はシャーシDBの値（内部減速比・タイヤ径）が焼き込まれる
 *  - 複製は元の値を引き継ぎ、**元シートがベースラインになる**（差分の軸 B の前提）
 */
class CarDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // ----- list_ -----

    @Test
    fun `list_車とシャーシとシートが載る`() = runTest {
        val sheets = FakeSetupSheetRepository()
        val vm = viewModel(sheets = sheets)
        advanceUntilIdle()
        sheets.createSheet(carId = CAR_ID, name = "Rd1", values = SetupValues.EMPTY)
        advanceUntilIdle()

        with(vm.uiState.value) {
            assertEquals("TT-02 #1", car?.name)
            assertEquals(FakeChassisRepository.TT02.name, chassis?.name)
            assertEquals(listOf("Rd1"), this.sheets.map { it.name })
        }
    }

    @Test
    fun `list_他の車のシートは出ない`() = runTest {
        val sheets = FakeSetupSheetRepository()
        val vm = viewModel(sheets = sheets)
        advanceUntilIdle()
        sheets.createSheet(carId = "other-car", name = "よそのシート", values = SetupValues.EMPTY)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.sheets.isEmpty())
    }

    @Test
    fun `list_車が消えたら戻る`() = runTest {
        val cars = FakeCarRepository(initial = listOf(car()))
        val vm = viewModel(cars = cars)
        advanceUntilIdle()

        cars.deleteCar(CAR_ID)
        advanceUntilIdle()

        assertEquals(ScreenEvent.NavigateBack, vm.events.first())
    }

    // ----- new_ -----

    @Test
    fun `new_シャーシの標準値が焼き込まれる`() = runTest {
        val sheets = FakeSetupSheetRepository()
        val vm = viewModel(sheets = sheets)
        advanceUntilIdle()

        vm.onNewSheetClick(defaultName = "Rd1")
        vm.onSheetDialogConfirm()
        advanceUntilIdle()

        val created = sheets.stored.single()
        assertEquals("Rd1", created.sheet.name)
        assertEquals(CAR_ID, created.sheet.carId)
        assertNull("新規シートにベースラインは無い", created.sheet.baselineId)
        // 内部減速比とタイヤ径はシャーシDB由来、ピニオン等はレジストリの既定値
        assertEquals(
            FakeChassisRepository.TT02.internalRatio,
            created.values.decimalOf("internalRatio")!!,
            0.0001
        )
        assertEquals(FakeChassisRepository.TT02.defaultTireMm, created.values.intOf("tireMm"))
        assertNotNull(created.values.intOf("pinion"))
        assertNull("ダイアログが閉じていない", vm.uiState.value.sheetDialog)
    }

    @Test
    fun `new_名前が空なら作らない`() = runTest {
        val sheets = FakeSetupSheetRepository()
        val vm = viewModel(sheets = sheets)
        advanceUntilIdle()

        vm.onNewSheetClick(defaultName = "Rd1")
        vm.onSheetNameChange("  ")
        vm.onSheetDialogConfirm()
        advanceUntilIdle()

        assertTrue(sheets.stored.isEmpty())
        assertEquals(
            UiText.Res(R.string.car_detail_error_sheet_name),
            vm.uiState.value.sheetDialog?.error
        )
    }

    @Test
    fun `new_シャーシが解決できなくても空欄で起こせる`() = runTest {
        // ユーザー定義シャーシ（F-5）や壊れたインポート。ここで落とすとシートが 1 枚も作れない
        val sheets = FakeSetupSheetRepository()
        val vm = viewModel(
            cars = FakeCarRepository(initial = listOf(car(chassisId = "unknown_id"))),
            sheets = sheets
        )
        advanceUntilIdle()

        vm.onNewSheetClick(defaultName = "Rd1")
        vm.onSheetDialogConfirm()
        advanceUntilIdle()

        val created = sheets.stored.single()
        assertNull(created.values.decimalOf("internalRatio"))
        assertNotNull("レジストリ既定値のある項目は入るはず", created.values.intOf("pinion"))
    }

    // ----- duplicate_ -----

    @Test
    fun `duplicate_値を引き継ぎ元シートがベースラインになる`() = runTest {
        val sheets = FakeSetupSheetRepository()
        val vm = viewModel(sheets = sheets)
        advanceUntilIdle()
        val sourceId = sheets.createSheet(
            carId = CAR_ID,
            name = "Rd1",
            values = SetupValues.of("pinion" to SetupValue.IntV(29))
        )
        advanceUntilIdle()

        vm.onDuplicateClick(sourceSheetId = sourceId, defaultName = "Rd2")
        vm.onSheetDialogConfirm()
        advanceUntilIdle()

        val created = sheets.stored.first { it.sheet.name == "Rd2" }
        assertEquals(29, created.values.intOf("pinion"))
        assertEquals(sourceId, created.sheet.baselineId)
    }

    @Test
    fun `duplicate_複製元が消えていても初期値で起こす`() = runTest {
        val sheets = FakeSetupSheetRepository()
        val vm = viewModel(sheets = sheets)
        advanceUntilIdle()
        val sourceId = sheets.createSheet(carId = CAR_ID, name = "Rd1", values = SetupValues.EMPTY)
        advanceUntilIdle()

        vm.onDuplicateClick(sourceSheetId = sourceId, defaultName = "Rd2")
        sheets.deleteSheet(sourceId)
        vm.onSheetDialogConfirm()
        advanceUntilIdle()

        val created = sheets.stored.single()
        assertEquals("Rd2", created.sheet.name)
        assertEquals(FakeChassisRepository.TT02.defaultTireMm, created.values.intOf("tireMm"))
    }

    @Test
    fun `dialog_取り消すと作られない`() = runTest {
        val sheets = FakeSetupSheetRepository()
        val vm = viewModel(sheets = sheets)
        advanceUntilIdle()

        vm.onNewSheetClick(defaultName = "Rd1")
        vm.onSheetDialogDismiss()
        advanceUntilIdle()

        assertNull(vm.uiState.value.sheetDialog)
        assertTrue(sheets.stored.isEmpty())
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
        cars: FakeCarRepository = FakeCarRepository(initial = listOf(car())),
        sheets: FakeSetupSheetRepository = FakeSetupSheetRepository()
    ) = CarDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("carId" to CAR_ID)),
        carRepository = cars,
        chassisRepository = FakeChassisRepository(),
        sheetRepository = sheets
    )

    private companion object {
        const val CAR_ID = "car-1"
    }
}
