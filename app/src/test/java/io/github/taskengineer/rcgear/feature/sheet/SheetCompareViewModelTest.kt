package io.github.taskengineer.rcgear.feature.sheet

import androidx.lifecycle.SavedStateHandle
import io.github.taskengineer.rcgear.core.ui.ScreenEvent
import io.github.taskengineer.rcgear.core.ui.UiText
import io.github.taskengineer.rcgear.domain.diff.DiffKind
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * [SheetCompareViewModel] のテスト（G-6）。
 *
 * 差分そのものは `SheetDiffTest`（:core:domain）が見ているので、ここは
 * **「どの軸でどの束と比べるか」**だけを確かめる。軸を取り違えると
 * 「変えていない所が変更点として出る」という静かな間違いになる。
 */
class SheetCompareViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `axis_ベースラインがあれば既定は軸 B`() = runTest {
        val sheets = FakeSetupSheetRepository()
        val baselineId = sheets.createSheet(carId = CAR_ID, name = "Rd1", values = SetupValues.EMPTY)
        val sheetId = sheets.createSheet(
            carId = CAR_ID,
            name = "Rd2",
            values = SetupValues.EMPTY,
            baselineId = baselineId
        )
        val vm = viewModel(sheets, sheetId)
        advanceUntilIdle()

        assertEquals(DiffAxis.BASELINE, vm.uiState.value.axis)
        assertTrue(vm.uiState.value.hasBaseline)
        assertEquals(UiText.Raw("Rd1"), vm.uiState.value.otherLabel)
    }

    @Test
    fun `axis_ベースラインが無ければ既定は軸 A`() = runTest {
        // 1 枚目のシートで軸 B を既定にすると、開いた瞬間は何も出ない
        val sheets = FakeSetupSheetRepository()
        val sheetId = sheets.createSheet(carId = CAR_ID, name = "Rd1", values = SetupValues.EMPTY)
        val vm = viewModel(sheets, sheetId)
        advanceUntilIdle()

        assertEquals(DiffAxis.CHASSIS, vm.uiState.value.axis)
        assertFalse(vm.uiState.value.hasBaseline)
    }

    @Test
    fun `axisA_シャーシ標準から変えた所が出る`() = runTest {
        val sheets = FakeSetupSheetRepository()
        // タイヤ径はシャーシ標準（63mm）のまま、ピニオンだけ既定（22T）から変える
        val sheetId = sheets.createSheet(
            carId = CAR_ID,
            name = "Rd1",
            values = SetupValues.of(
                "pinion" to SetupValue.IntV(30),
                "tireMm" to SetupValue.IntV(FakeChassisRepository.TT02.defaultTireMm)
            )
        )
        val vm = viewModel(sheets, sheetId)
        advanceUntilIdle()

        vm.onAxisChange(DiffAxis.CHASSIS)
        advanceUntilIdle()

        val keys = vm.uiState.value.groups.flatMap { group -> group.rows.map { it.fieldKey } }
        assertTrue("変えたピニオンが出ていない", "pinion" in keys)
        assertFalse("標準と同じタイヤ径が変更点になっている", "tireMm" in keys)
    }

    @Test
    fun `axisB_ベースラインから変えた所が出る`() = runTest {
        val sheets = FakeSetupSheetRepository()
        val baselineId = sheets.createSheet(
            carId = CAR_ID,
            name = "Rd1",
            values = SetupValues.of(
                "pinion" to SetupValue.IntV(22),
                "front.camberDeg" to SetupValue.DecimalV(-2.0)
            )
        )
        val sheetId = sheets.createSheet(
            carId = CAR_ID,
            name = "Rd2",
            values = SetupValues.of(
                "pinion" to SetupValue.IntV(22),
                "front.camberDeg" to SetupValue.DecimalV(-1.5)
            ),
            baselineId = baselineId
        )
        val vm = viewModel(sheets, sheetId)
        advanceUntilIdle()

        val rows = vm.uiState.value.groups.flatMap { it.rows }
        assertEquals(listOf("front.camberDeg"), rows.map { it.fieldKey })
        assertEquals(DiffKind.CHANGED, rows.single().kind)
    }

    @Test
    fun `axisC_選んだシートと比べる`() = runTest {
        val sheets = FakeSetupSheetRepository()
        val otherId = sheets.createSheet(
            carId = CAR_ID,
            name = "Rd1",
            values = SetupValues.of("spur" to SetupValue.IntV(84))
        )
        val sheetId = sheets.createSheet(
            carId = CAR_ID,
            name = "Rd2",
            values = SetupValues.of("spur" to SetupValue.IntV(90))
        )
        val vm = viewModel(sheets, sheetId)
        advanceUntilIdle()

        vm.onAxisChange(DiffAxis.OTHER)
        vm.onOtherSheetSelect(otherId)
        advanceUntilIdle()

        assertEquals(UiText.Raw("Rd1"), vm.uiState.value.otherLabel)
        assertEquals(
            listOf("spur"),
            vm.uiState.value.groups.flatMap { group -> group.rows.map { it.fieldKey } }
        )
    }

    @Test
    fun `axisC_他の車のシートは候補に入らない`() = runTest {
        // シャーシが違うシートと並べても「内部減速比が違う」のような当たり前の差で埋まる
        val sheets = FakeSetupSheetRepository()
        val sheetId = sheets.createSheet(carId = CAR_ID, name = "Rd1", values = SetupValues.EMPTY)
        sheets.createSheet(carId = "other-car", name = "よそ", values = SetupValues.EMPTY)
        val vm = viewModel(sheets, sheetId)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.candidates.isEmpty())
    }

    @Test
    fun `includeSame_同じ値も出せる`() = runTest {
        val sheets = FakeSetupSheetRepository()
        val baselineId = sheets.createSheet(
            carId = CAR_ID,
            name = "Rd1",
            values = SetupValues.of("pinion" to SetupValue.IntV(22))
        )
        val sheetId = sheets.createSheet(
            carId = CAR_ID,
            name = "Rd2",
            values = SetupValues.of("pinion" to SetupValue.IntV(22)),
            baselineId = baselineId
        )
        val vm = viewModel(sheets, sheetId)
        advanceUntilIdle()

        assertTrue("既定は変わった所だけ", vm.uiState.value.groups.isEmpty())

        vm.onIncludeSameChange(true)
        advanceUntilIdle()

        val rows = vm.uiState.value.groups.flatMap { it.rows }
        assertEquals(listOf("pinion"), rows.map { it.fieldKey })
        assertEquals(DiffKind.SAME, rows.single().kind)
    }

    @Test
    fun `group_セクションごとにまとまり未分類は末尾に来る`() = runTest {
        val sheets = FakeSetupSheetRepository()
        val baselineId = sheets.createSheet(carId = CAR_ID, name = "Rd1", values = SetupValues.EMPTY)
        val sheetId = sheets.createSheet(
            carId = CAR_ID,
            name = "Rd2",
            values = SetupValues.of(
                "pinion" to SetupValue.IntV(30),
                "front.camberDeg" to SetupValue.DecimalV(-2.0),
                // 将来の版で追加された項目が入ったデータを読んだ場合
                "future.someField" to SetupValue.IntV(1)
            ),
            baselineId = baselineId
        )
        val vm = viewModel(sheets, sheetId)
        advanceUntilIdle()

        val sections = vm.uiState.value.groups.map { it.sectionKey }
        assertEquals(listOf("gear", "suspension", null), sections)
    }

    @Test
    fun `load_シートが無ければすぐ戻る`() = runTest {
        val vm = viewModel(FakeSetupSheetRepository(), sheetId = "missing")
        advanceUntilIdle()

        assertEquals(ScreenEvent.NavigateBack, vm.events.first())
    }

    // ----- helpers -----

    private fun viewModel(sheets: FakeSetupSheetRepository, sheetId: String) =
        SheetCompareViewModel(
            savedStateHandle = SavedStateHandle(mapOf("sheetId" to sheetId)),
            sheetRepository = sheets,
            carRepository = FakeCarRepository(
                initial = listOf(
                    Car(
                        id = CAR_ID,
                        name = "TT-02 #1",
                        chassisId = "tamiya_tt02",
                        createdAt = 1_000L,
                        updatedAt = 1_000L
                    )
                )
            ),
            chassisRepository = FakeChassisRepository()
        )

    private companion object {
        const val CAR_ID = "car-1"
    }
}
