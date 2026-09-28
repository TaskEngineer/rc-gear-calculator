package io.github.taskengineer.rcgear.feature.garage

import androidx.lifecycle.SavedStateHandle
import io.github.taskengineer.rcgear.core.ui.ScreenEvent
import io.github.taskengineer.rcgear.domain.model.Car
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
 * [CarEditViewModel] のテスト（G-1）。
 *
 * 新規作成と編集を 1 つの ViewModel で受け持つので、**分岐がルート引数の有無だけで
 * 決まっている**ことを両方向から確かめる。名前とシャーシの検証（保存を止める）と、
 * 削除がシートまで連れて行くことも見る。
 */
class CarEditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // ----- create_ -----

    @Test
    fun `create_引数が無ければ新規作成として開く`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        with(vm.uiState.value) {
            assertTrue(isNew)
            assertFalse(isLoading)
            assertEquals("", nameInput)
            assertNull(selectedChassis)
            assertTrue("シャーシ選択の選択肢が空", makers.isNotEmpty())
        }
    }

    @Test
    fun `create_名前とシャーシを入れると車が作られる`() = runTest {
        val cars = FakeCarRepository()
        val vm = viewModel(carRepository = cars)
        advanceUntilIdle()

        vm.onNameChange("TA08 #1")
        vm.onChassisSelected("tamiya_ta08")
        vm.onSave()
        advanceUntilIdle()

        val created = cars.stored.single()
        assertEquals("TA08 #1", created.name)
        assertEquals("tamiya_ta08", created.chassisId)
        assertEquals(ScreenEvent.NavigateBack, vm.events.first())
    }

    @Test
    fun `input_名前が空なら保存できない`() = runTest {
        // BUG-7 の横展開。押してから弾くのではなく、埋まるまで押させない
        val cars = FakeCarRepository()
        val vm = viewModel(carRepository = cars)
        advanceUntilIdle()

        vm.onChassisSelected("tamiya_ta08")
        vm.onNameChange("   ")

        assertFalse(vm.uiState.value.canSave)

        vm.onSave()
        advanceUntilIdle()
        assertTrue("保存ボタンを無効にしていても ViewModel 側で止まっていない", cars.stored.isEmpty())
    }

    @Test
    fun `input_シャーシ未選択なら保存できない`() = runTest {
        val cars = FakeCarRepository()
        val vm = viewModel(carRepository = cars)
        advanceUntilIdle()

        vm.onNameChange("TA08 #1")

        assertFalse(vm.uiState.value.canSave)

        vm.onSave()
        advanceUntilIdle()
        assertTrue(cars.stored.isEmpty())
    }

    @Test
    fun `input_名前とシャーシが揃うと保存できるようになる`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertFalse("空の新規作成が最初から保存できてしまう", vm.uiState.value.canSave)

        vm.onNameChange("TA08 #1")
        vm.onChassisSelected("tamiya_ta08")

        assertTrue(vm.uiState.value.canSave)
    }

    @Test
    fun `create_備考が空欄なら null で保存する`() = runTest {
        // 空文字を入れると「備考あり」として一覧に空行が出る
        val cars = FakeCarRepository()
        val vm = viewModel(carRepository = cars)
        advanceUntilIdle()

        vm.onNameChange("TT-02")
        vm.onChassisSelected("tamiya_tt02")
        vm.onNoteChange("  ")
        vm.onSave()
        advanceUntilIdle()

        assertNull(cars.stored.single().note)
    }

    // ----- edit_ -----

    @Test
    fun `edit_既存の値が入力欄に載る`() = runTest {
        val cars = FakeCarRepository(initial = listOf(existingCar()))
        val vm = viewModel(carRepository = cars, carId = "car-1")
        advanceUntilIdle()

        with(vm.uiState.value) {
            assertFalse(isNew)
            assertEquals("TT-02 #1", nameInput)
            assertEquals("メモ", noteInput)
            assertEquals("tamiya_tt02", selectedChassis?.chassis?.id)
        }
    }

    @Test
    fun `edit_名前とシャーシを変えても createdAt は変わらない`() = runTest {
        val cars = FakeCarRepository(initial = listOf(existingCar()))
        cars.now = 9_999L
        val vm = viewModel(carRepository = cars, carId = "car-1")
        advanceUntilIdle()

        vm.onNameChange("TA08 #1")
        vm.onChassisSelected("tamiya_ta08")
        vm.onSave()
        advanceUntilIdle()

        val saved = cars.stored.single()
        assertEquals("TA08 #1", saved.name)
        assertEquals("tamiya_ta08", saved.chassisId)
        assertEquals(1_000L, saved.createdAt)
        assertEquals(9_999L, saved.updatedAt)
    }

    @Test
    fun `edit_アーカイブの切り替えが保存される`() = runTest {
        val cars = FakeCarRepository(initial = listOf(existingCar()))
        val vm = viewModel(carRepository = cars, carId = "car-1")
        advanceUntilIdle()

        vm.onArchivedChange(true)
        vm.onSave()
        advanceUntilIdle()

        assertTrue(cars.stored.single().isArchived)
    }

    @Test
    fun `edit_対象が消えていたらすぐ戻る`() = runTest {
        // 一覧を開いたまま別経路で削除された場合。空のフォームを見せる意味が無い
        val vm = viewModel(carRepository = FakeCarRepository(), carId = "car-1")
        advanceUntilIdle()

        assertEquals(ScreenEvent.NavigateBack, vm.events.first())
    }

    // ----- delete_ -----

    @Test
    fun `delete_確認してから削除しシートも消える`() = runTest {
        val sheets = FakeSetupSheetRepository()
        val cars = FakeCarRepository(initial = listOf(existingCar()), sheetRepository = sheets)
        sheets.createSheet(carId = "car-1", name = "Rd1", values = SetupValues.EMPTY)
        val vm = viewModel(carRepository = cars, carId = "car-1")
        advanceUntilIdle()

        vm.onDeleteClick()
        assertTrue(vm.uiState.value.showDeleteConfirm)

        vm.onDeleteConfirm()
        advanceUntilIdle()

        assertTrue(cars.stored.isEmpty())
        assertTrue("車を消したのにシートが残っている", sheets.stored.isEmpty())
        assertEquals(ScreenEvent.NavigateBack, vm.events.first())
    }

    @Test
    fun `delete_確認を取り消すと消えない`() = runTest {
        val cars = FakeCarRepository(initial = listOf(existingCar()))
        val vm = viewModel(carRepository = cars, carId = "car-1")
        advanceUntilIdle()

        vm.onDeleteClick()
        vm.onDeleteConfirmDismiss()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showDeleteConfirm)
        assertEquals(1, cars.stored.size)
    }

    // ----- helpers -----

    private fun existingCar() = Car(
        id = "car-1",
        name = "TT-02 #1",
        chassisId = "tamiya_tt02",
        note = "メモ",
        isArchived = false,
        createdAt = 1_000L,
        updatedAt = 1_000L
    )

    private fun viewModel(
        carRepository: FakeCarRepository = FakeCarRepository(),
        carId: String? = null
    ) = CarEditViewModel(
        savedStateHandle = SavedStateHandle(
            if (carId == null) emptyMap() else mapOf("carId" to carId)
        ),
        carRepository = carRepository,
        chassisRepository = FakeChassisRepository()
    )
}
