package io.github.taskengineer.rcgear.feature.sheet

import androidx.lifecycle.SavedStateHandle
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.ui.ScreenEvent
import io.github.taskengineer.rcgear.core.ui.UiText
import io.github.taskengineer.rcgear.domain.model.SetupValues
import io.github.taskengineer.rcgear.fake.FakeSetupSheetRepository
import io.github.taskengineer.rcgear.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * [SheetHeaderEditViewModel] のテスト（G-4）。
 *
 * 走行条件は文字列で受けて保存時に数値化するので、その境界を見る。
 * 日付は「UTC 0 時で渡ってくる値を端末時間の日付として保存する」変換が本題
 * （ここを素通しすると UTC より西の地域で前日になる）。
 */
class SheetHeaderEditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `load_既存のヘッダが入力欄に載る`() = runTest {
        val sheets = FakeSetupSheetRepository()
        val sheetId = sheets.createSheet(carId = CAR_ID, name = "Rd1", values = SetupValues.EMPTY)
        val vm = viewModel(sheets, sheetId)
        advanceUntilIdle()

        assertEquals("Rd1", vm.uiState.value.nameInput)
        assertNull(vm.uiState.value.sessionDate)
    }

    @Test
    fun `load_ベースライン候補に自分は入らない`() = runTest {
        val sheets = FakeSetupSheetRepository()
        val sheetId = sheets.createSheet(carId = CAR_ID, name = "Rd1", values = SetupValues.EMPTY)
        val otherId = sheets.createSheet(carId = CAR_ID, name = "Rd2", values = SetupValues.EMPTY)
        sheets.createSheet(carId = "other-car", name = "よそ", values = SetupValues.EMPTY)
        val vm = viewModel(sheets, sheetId)
        advanceUntilIdle()

        assertEquals(listOf(otherId), vm.uiState.value.baselineCandidates.map { it.id })
    }

    @Test
    fun `save_走行条件が数値になって保存される`() = runTest {
        val sheets = FakeSetupSheetRepository()
        val sheetId = sheets.createSheet(carId = CAR_ID, name = "Rd1", values = SetupValues.EMPTY)
        val vm = viewModel(sheets, sheetId)
        advanceUntilIdle()

        vm.onNameChange("Rd1 予選")
        vm.onTrackChange("パルスサーキット")
        vm.onAirTempChange("23.5")
        vm.onTrackTempChange("31")
        vm.onHumidityChange("48")
        vm.onBestLapChange("12.345")
        vm.onNoteChange(" リアグリップ不足 ")
        vm.onSave()
        advanceUntilIdle()

        val saved = sheets.stored.single().sheet
        assertEquals("Rd1 予選", saved.name)
        assertEquals("パルスサーキット", saved.conditions.trackName)
        assertEquals(23.5, saved.conditions.airTempC!!, 0.0001)
        assertEquals(31.0, saved.conditions.trackTempC!!, 0.0001)
        assertEquals(48, saved.conditions.humidityPct)
        assertEquals("秒 → ms の変換", 12_345, saved.conditions.bestLapMs)
        assertEquals("リアグリップ不足", saved.note)
        assertEquals(ScreenEvent.NavigateBack, vm.events.first())
    }

    @Test
    fun `save_空欄の条件は null で保存する`() = runTest {
        // 空欄と 0 は違う。0 を入れると「気温 0℃ で走った」ことになる
        val sheets = FakeSetupSheetRepository()
        val sheetId = sheets.createSheet(carId = CAR_ID, name = "Rd1", values = SetupValues.EMPTY)
        val vm = viewModel(sheets, sheetId)
        advanceUntilIdle()

        vm.onSave()
        advanceUntilIdle()

        val conditions = sheets.stored.single().sheet.conditions
        assertNull(conditions.airTempC)
        assertNull(conditions.humidityPct)
        assertNull(conditions.bestLapMs)
        assertNull(sheets.stored.single().sheet.note)
    }

    @Test
    fun `save_名前が空なら保存しない`() = runTest {
        val sheets = FakeSetupSheetRepository()
        val sheetId = sheets.createSheet(carId = CAR_ID, name = "Rd1", values = SetupValues.EMPTY)
        val vm = viewModel(sheets, sheetId)
        advanceUntilIdle()

        vm.onNameChange("   ")
        vm.onSave()
        advanceUntilIdle()

        assertEquals("Rd1", sheets.stored.single().sheet.name)
        assertEquals(
            UiText.Res(R.string.car_detail_error_sheet_name),
            vm.uiState.value.errorMessage
        )
    }

    @Test
    fun `save_範囲外の気温はエラーにして保存しない`() = runTest {
        val sheets = FakeSetupSheetRepository()
        val sheetId = sheets.createSheet(carId = CAR_ID, name = "Rd1", values = SetupValues.EMPTY)
        val vm = viewModel(sheets, sheetId)
        advanceUntilIdle()

        vm.onAirTempChange("300")
        vm.onSave()
        advanceUntilIdle()

        assertNull(sheets.stored.single().sheet.conditions.airTempC)
        assertEquals(
            UiText.Res(R.string.sheet_header_error_air_temp),
            vm.uiState.value.errorMessage
        )
    }

    @Test
    fun `save_数値でないラップタイムはエラーにする`() = runTest {
        val sheets = FakeSetupSheetRepository()
        val sheetId = sheets.createSheet(carId = CAR_ID, name = "Rd1", values = SetupValues.EMPTY)
        val vm = viewModel(sheets, sheetId)
        advanceUntilIdle()

        vm.onBestLapChange("12.3.4")
        vm.onSave()
        advanceUntilIdle()

        assertEquals(
            UiText.Res(R.string.sheet_header_error_best_lap),
            vm.uiState.value.errorMessage
        )
    }

    @Test
    fun `date_UTC 0 時で選ばれた日付が端末時間の同じ日になる`() = runTest {
        // DatePicker は UTC 0 時を返す。素通しすると UTC-5 の地域では前日に見える
        val sheets = FakeSetupSheetRepository()
        val sheetId = sheets.createSheet(carId = CAR_ID, name = "Rd1", values = SetupValues.EMPTY)
        val vm = viewModel(sheets, sheetId)
        advanceUntilIdle()

        // 2026-09-28T00:00:00Z
        vm.onDatePicked(1_790_553_600_000L)
        advanceUntilIdle()

        val saved = vm.uiState.value.sessionDate!!
        val localDate = Instant.ofEpochMilli(saved).atZone(ZoneId.systemDefault()).toLocalDate()
        assertEquals(
            Instant.ofEpochMilli(1_790_553_600_000L).atZone(ZoneOffset.UTC).toLocalDate(),
            localDate
        )
        assertTrue(vm.uiState.value.isDatePickerOpen.not())
    }

    @Test
    fun `date_消すと null に戻る`() = runTest {
        val sheets = FakeSetupSheetRepository()
        val sheetId = sheets.createSheet(carId = CAR_ID, name = "Rd1", values = SetupValues.EMPTY)
        val vm = viewModel(sheets, sheetId)
        advanceUntilIdle()

        vm.onDatePicked(1_790_553_600_000L)
        vm.onDateClear()
        vm.onSave()
        advanceUntilIdle()

        assertNull(sheets.stored.single().sheet.conditions.sessionDate)
    }

    @Test
    fun `baseline_選ぶと保存される`() = runTest {
        val sheets = FakeSetupSheetRepository()
        val baselineId = sheets.createSheet(carId = CAR_ID, name = "Rd1", values = SetupValues.EMPTY)
        val sheetId = sheets.createSheet(carId = CAR_ID, name = "Rd2", values = SetupValues.EMPTY)
        val vm = viewModel(sheets, sheetId)
        advanceUntilIdle()

        vm.onBaselineSelect(baselineId)
        vm.onSave()
        advanceUntilIdle()

        assertEquals(baselineId, sheets.stored.first { it.id == sheetId }.sheet.baselineId)
    }

    @Test
    fun `load_シートが無ければすぐ戻る`() = runTest {
        val vm = viewModel(FakeSetupSheetRepository(), sheetId = "missing")
        advanceUntilIdle()

        assertEquals(ScreenEvent.NavigateBack, vm.events.first())
    }

    // ----- helpers -----

    private fun viewModel(sheets: FakeSetupSheetRepository, sheetId: String) =
        SheetHeaderEditViewModel(
            savedStateHandle = SavedStateHandle(mapOf("sheetId" to sheetId)),
            sheetRepository = sheets
        )

    private companion object {
        const val CAR_ID = "car-1"
    }
}
