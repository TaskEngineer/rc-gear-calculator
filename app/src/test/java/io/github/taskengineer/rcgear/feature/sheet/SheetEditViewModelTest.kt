package io.github.taskengineer.rcgear.feature.sheet

import androidx.lifecycle.SavedStateHandle
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.ui.ScreenEvent
import io.github.taskengineer.rcgear.core.ui.UiText
import io.github.taskengineer.rcgear.domain.model.Car
import io.github.taskengineer.rcgear.domain.model.SetupValue
import io.github.taskengineer.rcgear.domain.model.SetupValues
import io.github.taskengineer.rcgear.domain.schema.NumberFieldDef
import io.github.taskengineer.rcgear.domain.schema.TextFieldDef
import io.github.taskengineer.rcgear.domain.schema.TouringSetupSchema
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
 * [SheetEditViewModel] のテスト（G-4）。
 *
 * ここが Phase 3 で一番壊れると痛い所。守りたい性質は 3 つ:
 *  1. **不正な入力を確定しない**（画面の表示と保存される値がズレない）
 *  2. **空欄は値を消す**（0 で埋めない。空欄と 0 は意味が違う）
 *  3. **保存は変わったキーだけ**（他セクションの値を巻き込まない）
 */
class SheetEditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val pinion = TouringSetupSchema.byKey.getValue("pinion") as NumberFieldDef
    private val internalRatio = TouringSetupSchema.byKey.getValue("internalRatio") as NumberFieldDef
    private val oilBrand = TouringSetupSchema.byKey.getValue("front.damperOilBrand") as TextFieldDef

    // ----- load_ -----

    @Test
    fun `load_セクションと値が載る`() = runTest {
        val sheets = FakeSetupSheetRepository()
        val sheetId = sheets.createSheet(
            carId = CAR_ID,
            name = "Rd1",
            values = SetupValues.of("pinion" to SetupValue.IntV(29))
        )
        val vm = viewModel(sheets, sheetId, sectionKey = "gear")
        advanceUntilIdle()

        with(vm.uiState.value) {
            assertFalse(isLoading)
            assertEquals("gear", section?.key)
            assertEquals("Rd1", sheetName)
            assertEquals(29, values.intOf("pinion"))
            assertFalse(isDirty)
        }
    }

    @Test
    fun `load_知らないセクションキーならすぐ戻る`() = runTest {
        // 古いディープリンク・レジストリから消えたセクション
        val sheets = FakeSetupSheetRepository()
        val sheetId = sheets.createSheet(carId = CAR_ID, name = "Rd1", values = SetupValues.EMPTY)
        val vm = viewModel(sheets, sheetId, sectionKey = "unknown_section")
        advanceUntilIdle()

        assertEquals(ScreenEvent.NavigateBack, vm.events.first())
    }

    // ----- input_ -----

    @Test
    fun `input_数値欄の値が確定する`() = runTest {
        val (vm, _) = loaded()
        advanceUntilIdle()

        vm.onNumberInput(internalRatio, "2.15")

        assertEquals(2.15, vm.uiState.value.values.decimalOf("internalRatio")!!, 0.0001)
        assertTrue(vm.uiState.value.errors.isEmpty())
        assertTrue(vm.uiState.value.isDirty)
    }

    @Test
    fun `input_空欄にすると値が消える`() = runTest {
        // 空欄と 0 は意味が違う。0 で埋めると「0mm に設定した」ことになってしまう
        val (vm, _) = loaded(values = SetupValues.of("internalRatio" to SetupValue.DecimalV(2.6)))
        advanceUntilIdle()

        vm.onNumberInput(internalRatio, "")

        assertFalse("internalRatio" in vm.uiState.value.values)
        assertTrue(vm.uiState.value.errors.isEmpty())
    }

    @Test
    fun `input_数値でない入力はエラーになり値は変わらない`() = runTest {
        val (vm, _) = loaded(values = SetupValues.of("internalRatio" to SetupValue.DecimalV(2.6)))
        advanceUntilIdle()

        vm.onNumberInput(internalRatio, "2.6x")

        assertEquals(2.6, vm.uiState.value.values.decimalOf("internalRatio")!!, 0.0001)
        assertEquals("2.6x", vm.uiState.value.drafts["internalRatio"])
        assertEquals(
            UiText.Res(R.string.sheet_edit_error_number),
            vm.uiState.value.errors["internalRatio"]
        )
    }

    @Test
    fun `input_範囲外はエラーになり値は変わらない`() = runTest {
        // 範囲はレジストリ（FieldValidator）が知っている。ここで二重に書かない
        val (vm, _) = loaded(values = SetupValues.of("internalRatio" to SetupValue.DecimalV(2.6)))
        advanceUntilIdle()

        vm.onNumberInput(internalRatio, "99")

        assertEquals(2.6, vm.uiState.value.values.decimalOf("internalRatio")!!, 0.0001)
        assertTrue(vm.uiState.value.errors.containsKey("internalRatio"))
        assertFalse("エラー中は保存させない", vm.uiState.value.canSave)
    }

    @Test
    fun `input_エラーを直すと保存できるようになる`() = runTest {
        val (vm, _) = loaded()
        advanceUntilIdle()

        vm.onNumberInput(internalRatio, "99")
        vm.onNumberInput(internalRatio, "2.6")

        assertTrue(vm.uiState.value.errors.isEmpty())
        assertTrue(vm.uiState.value.canSave)
    }

    @Test
    fun `input_文字数超過はエラーになる`() = runTest {
        val (vm, _) = loaded(sectionKey = "damper")
        advanceUntilIdle()

        vm.onTextInput(oilBrand, "x".repeat(oilBrand.maxLength + 1))

        assertEquals(
            UiText.Res(R.string.sheet_edit_error_too_long, listOf(oilBrand.maxLength)),
            vm.uiState.value.errors[oilBrand.key]
        )
        assertFalse(oilBrand.key in vm.uiState.value.values)
    }

    @Test
    fun `input_スライダー等の確定値はそのまま入る`() = runTest {
        val (vm, _) = loaded()
        advanceUntilIdle()

        vm.onValueChange(pinion, SetupValue.IntV(34))

        assertEquals(34, vm.uiState.value.values.intOf("pinion"))
    }

    @Test
    fun `input_部品が範囲外の値を作っても弾く`() = runTest {
        // 刻み計算のバグで範囲外の値が来た場合。保存前の最後の関所
        val (vm, _) = loaded()
        advanceUntilIdle()

        vm.onValueChange(pinion, SetupValue.IntV(999))

        // 元の値が残り、エラーが付く（範囲外の値で上書きされない）
        assertEquals(22, vm.uiState.value.values.intOf("pinion"))
        assertTrue(vm.uiState.value.errors.containsKey("pinion"))
    }

    // ----- save_ -----

    @Test
    fun `save_変わったキーだけ書き込み他セクションは触らない`() = runTest {
        val sheets = FakeSetupSheetRepository()
        val sheetId = sheets.createSheet(
            carId = CAR_ID,
            name = "Rd1",
            values = SetupValues.of(
                "pinion" to SetupValue.IntV(29),
                "front.camberDeg" to SetupValue.DecimalV(-2.0)
            )
        )
        val vm = viewModel(sheets, sheetId, sectionKey = "gear")
        advanceUntilIdle()

        vm.onValueChange(pinion, SetupValue.IntV(34))
        vm.onSave()
        advanceUntilIdle()

        val saved = sheets.stored.single().values
        assertEquals(34, saved.intOf("pinion"))
        assertEquals("他セクションの値が消えている", -2.0, saved.decimalOf("front.camberDeg")!!, 0.0001)
        assertEquals(ScreenEvent.NavigateBack, vm.events.first())
    }

    @Test
    fun `save_空欄にした項目は行ごと消える`() = runTest {
        val sheets = FakeSetupSheetRepository()
        val sheetId = sheets.createSheet(
            carId = CAR_ID,
            name = "Rd1",
            values = SetupValues.of("internalRatio" to SetupValue.DecimalV(2.6))
        )
        val vm = viewModel(sheets, sheetId, sectionKey = "gear")
        advanceUntilIdle()

        vm.onNumberInput(internalRatio, "")
        vm.onSave()
        advanceUntilIdle()

        assertFalse("internalRatio" in sheets.stored.single().values)
    }

    @Test
    fun `save_エラーがあるときは書き込まない`() = runTest {
        val sheets = FakeSetupSheetRepository()
        val sheetId = sheets.createSheet(
            carId = CAR_ID,
            name = "Rd1",
            values = SetupValues.of("internalRatio" to SetupValue.DecimalV(2.6))
        )
        val vm = viewModel(sheets, sheetId, sectionKey = "gear")
        advanceUntilIdle()

        vm.onNumberInput(internalRatio, "99")
        vm.onSave()
        advanceUntilIdle()

        assertEquals(2.6, sheets.stored.single().values.decimalOf("internalRatio")!!, 0.0001)
    }

    // ----- back_ -----

    @Test
    fun `back_変更が無ければそのまま戻る`() = runTest {
        val (vm, _) = loaded()
        advanceUntilIdle()

        vm.onBackRequest()

        assertFalse(vm.uiState.value.showDiscardConfirm)
        assertEquals(ScreenEvent.NavigateBack, vm.events.first())
    }

    @Test
    fun `back_未保存の変更があれば確認を出す`() = runTest {
        val (vm, sheets) = loaded()
        advanceUntilIdle()

        vm.onValueChange(pinion, SetupValue.IntV(34))
        vm.onBackRequest()

        assertTrue(vm.uiState.value.showDiscardConfirm)
        assertEquals("確認中に保存してしまっている", 22, sheets.stored.single().values.intOf("pinion"))
    }

    @Test
    fun `back_破棄すると保存せずに戻る`() = runTest {
        val (vm, sheets) = loaded()
        advanceUntilIdle()

        vm.onValueChange(pinion, SetupValue.IntV(34))
        vm.onBackRequest()
        vm.onDiscardConfirm()
        advanceUntilIdle()

        assertEquals(22, sheets.stored.single().values.intOf("pinion"))
        assertEquals(ScreenEvent.NavigateBack, vm.events.first())
    }

    // ----- helpers -----

    private suspend fun loaded(
        values: SetupValues = SetupValues.of("pinion" to SetupValue.IntV(22)),
        sectionKey: String = "gear"
    ): Pair<SheetEditViewModel, FakeSetupSheetRepository> {
        val sheets = FakeSetupSheetRepository()
        val sheetId = sheets.createSheet(carId = CAR_ID, name = "Rd1", values = values)
        return viewModel(sheets, sheetId, sectionKey) to sheets
    }

    private fun viewModel(
        sheets: FakeSetupSheetRepository,
        sheetId: String,
        sectionKey: String
    ) = SheetEditViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf("sheetId" to sheetId, "sectionKey" to sectionKey)
        ),
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
