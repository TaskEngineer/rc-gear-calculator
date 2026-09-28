package io.github.taskengineer.rcgear.feature.calc

import androidx.lifecycle.SavedStateHandle
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.ui.UiText
import io.github.taskengineer.rcgear.domain.model.Car
import io.github.taskengineer.rcgear.domain.model.ChassisOverride
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput
import io.github.taskengineer.rcgear.domain.model.SetupValue
import io.github.taskengineer.rcgear.domain.model.SetupValues
import io.github.taskengineer.rcgear.domain.model.UserPreferences
import io.github.taskengineer.rcgear.fake.FakeCarRepository
import io.github.taskengineer.rcgear.fake.FakeChassisRepository
import io.github.taskengineer.rcgear.fake.FakePreferencesRepository
import io.github.taskengineer.rcgear.fake.FakeSetupSheetRepository
import io.github.taskengineer.rcgear.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * [CalcViewModel] のテスト（REF-3 / S-6）。
 *
 * ここで一番守りたいのは **BUG-1 / BUG-2 の再発防止**。
 * 状態を変える全経路（前回値の復元・シャーシ選択・スライダー）が
 * `recalculate()` のクランプを通っていないと、スライダーの表示と計算結果がズレる。
 * この「全経路が 1 点に集まっている」という性質はコードを読まないと分からず、
 * 壊れても気づきにくいので、経路ごとにテストを置く。
 *
 * メソッド名のプレフィクスでカテゴリを表現 (init_, chassis_, slider_, clamp_, prefs_)。
 *
 * G-5 でシートとの往復（feed_ / apply_）が入った。ここで守りたいのは
 * 「シートに無い項目で 0 埋めしない」「書き戻すのはギアの 5 項目だけ」。
 */
class CalcViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val chassisRepository = FakeChassisRepository()

    // ----- init_ -----

    @Test
    fun `init_前回終了時の値が復元される`() = runTest {
        val vm = viewModel(
            prefs = UserPreferences(
                lastSelectedChassisId = "tamiya_ta08",
                lastPinion = 26,
                lastSpur = 90,
                lastKv = 7500,
                lastCells = 3,
                lastTireMm = 62
            )
        )
        advanceUntilIdle()

        with(vm.uiState.value) {
            assertEquals("tamiya_ta08", selectedChassis?.chassis?.id)
            assertEquals(26, pinion)
            assertEquals(90, spur)
            assertEquals(7500, kv)
            assertEquals(3, cells)
            assertEquals(62, tireMm)
            assertNotNull("復元後に計算結果が無い", result)
        }
    }

    @Test
    fun `init_シャーシ未選択なら計算結果は出ない`() = runTest {
        val vm = viewModel(prefs = UserPreferences(lastSelectedChassisId = null))
        advanceUntilIdle()

        assertNull(vm.uiState.value.result)
        assertTrue(vm.uiState.value.makers.isNotEmpty())
    }

    // ----- clamp_: BUG-1 / BUG-2 -----

    @Test
    fun `clamp_範囲外の前回値が保存されていてもクラッシュせず丸められる`() = runTest {
        // DataStore を手で書き換えた / 旧バージョンの値が残っている等で起こりうる。
        // 丸めずに GearCalculationInput へ渡すと init の require で落ちる。
        val vm = viewModel(
            prefs = UserPreferences(
                lastSelectedChassisId = "tamiya_tt02",
                lastPinion = 5,
                lastSpur = 999,
                lastKv = 99_999,
                lastCells = 0,
                lastTireMm = 500
            )
        )
        advanceUntilIdle()

        with(vm.uiState.value) {
            assertEquals(GearCalculationInput.MIN_PINION, pinion)
            assertEquals(GearCalculationInput.MAX_SPUR, spur)
            assertEquals(GearCalculationInput.MAX_KV, kv)
            assertEquals(GearCalculationInput.MIN_CELLS, cells)
            assertEquals(GearCalculationInput.MAX_TIRE_MM, tireMm)
            assertNotNull("クランプ後は計算できるはず", result)
        }
    }

    @Test
    fun `clamp_内部減速比が 0 以下の上書きがあっても落ちず結果を伏せる`() = runTest {
        // BUG-2。internalRatio は「丸める」性質の値ではないので、
        // クランプではなく結果を出さないのが正しい振る舞い。
        val chassis = FakeChassisRepository(
            initialOverrides = listOf(
                ChassisOverride(
                    chassisId = "tamiya_tt02",
                    internalRatio = 0.0,
                    defaultTireMm = null,
                    note = null,
                    updatedAt = 0L
                )
            )
        )
        val vm = viewModel(
            chassisRepository = chassis,
            prefs = UserPreferences(lastSelectedChassisId = "tamiya_tt02")
        )
        advanceUntilIdle()

        assertEquals("tamiya_tt02", vm.uiState.value.selectedChassis?.chassis?.id)
        assertNull("不正な減速比で計算してしまっている", vm.uiState.value.result)
    }

    // ----- chassis_ -----

    @Test
    fun `chassis_選択するとタイヤ径がそのシャーシの既定値になる`() = runTest {
        val vm = viewModel(prefs = UserPreferences(lastSelectedChassisId = "tamiya_tt02"))
        advanceUntilIdle()

        vm.onChassisSelected("tamiya_ta08")
        advanceUntilIdle()

        assertEquals("tamiya_ta08", vm.uiState.value.selectedChassis?.chassis?.id)
        assertEquals(FakeChassisRepository.TA08.defaultTireMm, vm.uiState.value.tireMm)
    }

    @Test
    fun `chassis_選択は前回値として即座に永続化される`() = runTest {
        val prefs = FakePreferencesRepository()
        val vm = viewModel(preferencesRepository = prefs)
        advanceUntilIdle()

        vm.onChassisSelected("tamiya_ta08")
        advanceUntilIdle()

        assertEquals("tamiya_ta08", prefs.current.lastSelectedChassisId)
    }

    // ----- slider_ -----

    @Test
    fun `slider_ピニオンを変えると FDR が下がる`() = runTest {
        val vm = viewModel(prefs = UserPreferences(lastSelectedChassisId = "tamiya_tt02"))
        advanceUntilIdle()
        val before = vm.uiState.value.result!!.finalDriveRatio

        vm.onPinionChange(30)

        assertTrue("ピニオンを増やしたのに FDR が下がっていない", vm.uiState.value.result!!.finalDriveRatio < before)
    }

    @Test
    fun `slider_操作中は永続化せず確定時に書く`() = runTest {
        // onValueChange のたびに DataStore を叩くと書き込みが溢れる。
        val prefs = FakePreferencesRepository()
        val vm = viewModel(preferencesRepository = prefs)
        advanceUntilIdle()

        vm.onPinionChange(30)
        advanceUntilIdle()
        assertEquals(GearCalculationInput.DEFAULT_PINION, prefs.current.lastPinion)

        vm.onSliderChangeFinished()
        advanceUntilIdle()
        assertEquals(30, prefs.current.lastPinion)
    }

    @Test
    fun `slider_同一 tick で複数回更新しても値と計算結果がズレない`() = runTest {
        // U-4 / DEBT-10。以前は MutableStateFlow.update {} の中で再計算していた。
        // update はリトライでラムダを何度も呼ぶ契約なので、
        // 「入力は新しいのに結果は古い」状態を観測する余地が残っていた。
        // スライダーのドラッグは 1 フレーム内に複数回 onValueChange を撃つため、
        // ここでは advanceUntilIdle を挟まずに連続更新する。
        val vm = viewModel(prefs = UserPreferences(lastSelectedChassisId = "tamiya_tt02"))
        advanceUntilIdle()

        vm.onPinionChange(20)
        vm.onSpurChange(80)
        vm.onTireMmChange(70)

        with(vm.uiState.value) {
            assertEquals(20, pinion)
            assertEquals(80, spur)
            assertEquals(70, tireMm)
            // FDR = スパー / ピニオン × 内部減速比 = 80 / 20 × 2.6
            assertEquals(
                80.0 / 20.0 * FakeChassisRepository.TT02.internalRatio,
                result!!.finalDriveRatio,
                1e-9
            )
        }
    }

    @Test
    fun `slider_範囲外の値はクランプされて結果も丸めた値で計算される`() = runTest {
        // クランプは setState の 1 箇所に集約されている（REF-1）。
        val vm = viewModel(prefs = UserPreferences(lastSelectedChassisId = "tamiya_tt02"))
        advanceUntilIdle()

        vm.onSpurChange(999)

        with(vm.uiState.value) {
            assertEquals(GearCalculationInput.MAX_SPUR, spur)
            assertEquals(
                GearCalculationInput.MAX_SPUR.toDouble() / pinion *
                    FakeChassisRepository.TT02.internalRatio,
                result!!.finalDriveRatio,
                1e-9
            )
        }
    }

    // ----- prefs_ -----

    @Test
    fun `prefs_基準FDR を変えるとバーの値が再計算される`() = runTest {
        val prefs = FakePreferencesRepository(
            UserPreferences(lastSelectedChassisId = "tamiya_tt02", balanceFdr = 7.0)
        )
        val vm = viewModel(preferencesRepository = prefs)
        advanceUntilIdle()
        val before = vm.uiState.value.result!!.balanceIndicatorPct

        prefs.emit { it.copy(balanceFdr = 5.0) }
        advanceUntilIdle()

        assertEquals(5.0, vm.uiState.value.balanceFdr, 1e-9)
        assertTrue("基準を変えたのにバーが動かない", vm.uiState.value.result!!.balanceIndicatorPct != before)
    }

    // ----- feed_: シート → CALC（G-5） -----

    @Test
    fun `feed_シートの値と車のシャーシが載る`() = runTest {
        val sheets = FakeSetupSheetRepository()
        val sheetId = sheets.createSheet(
            carId = "car-1",
            name = "Rd1",
            values = SetupValues.of(
                "pinion" to SetupValue.IntV(30),
                "spur" to SetupValue.IntV(90),
                "motorKv" to SetupValue.IntV(7500),
                "cells" to SetupValue.IntV(3),
                "tireMm" to SetupValue.IntV(60),
                "internalRatio" to SetupValue.DecimalV(2.6)
            )
        )
        val vm = viewModel(sheetRepository = sheets, sheetId = sheetId)
        advanceUntilIdle()

        with(vm.uiState.value) {
            assertEquals(30, pinion)
            assertEquals(90, spur)
            assertEquals(7500, kv)
            assertEquals(3, cells)
            assertEquals(60, tireMm)
            assertEquals("tamiya_tt02", selectedChassis?.chassis?.id)
            assertEquals("Rd1", sheetContext?.sheetName)
            assertTrue(sheetContext?.isComplete == true)
            assertNotNull(result)
        }
    }

    @Test
    fun `feed_シートに無い項目は前回値のまま残る`() = runTest {
        // 0 で埋めると「シートに 0T と書いてある」ように見えてしまう
        val sheets = FakeSetupSheetRepository()
        val sheetId = sheets.createSheet(
            carId = "car-1",
            name = "Rd1",
            values = SetupValues.of("pinion" to SetupValue.IntV(30))
        )
        val vm = viewModel(
            prefs = UserPreferences(lastSpur = 88),
            sheetRepository = sheets,
            sheetId = sheetId
        )
        advanceUntilIdle()

        assertEquals(30, vm.uiState.value.pinion)
        assertEquals(88, vm.uiState.value.spur)
        assertEquals("値が欠けている", false, vm.uiState.value.sheetContext?.isComplete)
    }

    @Test
    fun `feed_引数が無ければ素のスクラッチパッドとして開く`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertNull(vm.uiState.value.sheetContext)
    }

    // ----- apply_: CALC → シート（G-5） -----

    @Test
    fun `apply_ギアの 5 項目だけ書き戻す`() = runTest {
        val sheets = FakeSetupSheetRepository()
        val sheetId = sheets.createSheet(
            carId = "car-1",
            name = "Rd1",
            values = SetupValues.of(
                "pinion" to SetupValue.IntV(22),
                "internalRatio" to SetupValue.DecimalV(2.6),
                "front.camberDeg" to SetupValue.DecimalV(-2.0)
            )
        )
        val vm = viewModel(sheetRepository = sheets, sheetId = sheetId)
        advanceUntilIdle()

        vm.onPinionChange(34)
        vm.onApplyToSheet()
        advanceUntilIdle()

        val saved = sheets.stored.single().values
        assertEquals(34, saved.intOf("pinion"))
        // CALC が触らない値は残る（内部減速比はシャーシDB由来の焼き込み値）
        assertEquals(2.6, saved.decimalOf("internalRatio")!!, 1e-9)
        assertEquals(-2.0, saved.decimalOf("front.camberDeg")!!, 1e-9)
        assertEquals(
            UiText.Res(R.string.calc_applied_to_sheet, listOf("Rd1")),
            vm.uiState.value.message
        )
    }

    // ----- BUG-6: 流し込み元のシートが消えた -----

    @Test
    fun `feed_流し込み元のシートが消えたらバナーが畳まれる`() = runTest {
        // CALC はタブなので開いたまま GARAGE / CONFIG からシートを消せる。
        // バナーが残っていると「存在しないシートに反映」が押せてしまい、
        // 本物の DB では外部キー違反でアプリごと落ちた（BUG-6）。
        val sheets = FakeSetupSheetRepository()
        val sheetId = sheets.createSheet("car-1", "Rd1", SetupValues.EMPTY)
        val vm = viewModel(sheetRepository = sheets, sheetId = sheetId)
        advanceUntilIdle()
        assertNotNull("前提が崩れている", vm.uiState.value.sheetContext)

        sheets.deleteSheet(sheetId)
        advanceUntilIdle()

        assertNull("消えたシートのバナーが残っている", vm.uiState.value.sheetContext)
    }

    @Test
    fun `feed_全データ削除でもバナーが畳まれる`() = runTest {
        // CONFIG の「全データ削除」経由。deleteSheet とは別の経路で消える
        val sheets = FakeSetupSheetRepository()
        val sheetId = sheets.createSheet("car-1", "Rd1", SetupValues.EMPTY)
        val vm = viewModel(sheetRepository = sheets, sheetId = sheetId)
        advanceUntilIdle()

        sheets.deleteAll()
        advanceUntilIdle()

        assertNull(vm.uiState.value.sheetContext)
    }

    @Test
    fun `apply_シートが消えていれば書かずに理由を出す`() = runTest {
        // バナーを畳む前に押し込まれた場合の最後の関所。
        // 本物の Repository は例外ではなく false を返す契約になっている
        val sheets = FakeSetupSheetRepository()
        val sheetId = sheets.createSheet("car-1", "Rd1", SetupValues.EMPTY)
        val vm = viewModel(sheetRepository = sheets, sheetId = sheetId)
        advanceUntilIdle()
        // 購読が畳む前に押された状況を作るため、Flow を回さずに消す
        sheets.deleteSheet(sheetId)

        vm.onApplyToSheet()
        advanceUntilIdle()

        assertTrue("消えたシートに書き戻している", sheets.stored.isEmpty())
        assertNull(vm.uiState.value.sheetContext)
        assertEquals(UiText.Res(R.string.calc_sheet_gone), vm.uiState.value.message)
    }

    @Test
    fun `apply_シート文脈が無ければ何もしない`() = runTest {
        val sheets = FakeSetupSheetRepository()
        sheets.createSheet(carId = "car-1", name = "Rd1", values = SetupValues.EMPTY)
        val vm = viewModel(sheetRepository = sheets)
        advanceUntilIdle()

        vm.onApplyToSheet()
        advanceUntilIdle()

        assertTrue(sheets.stored.single().values.isEmpty())
        assertNull(vm.uiState.value.message)
    }

    // ----- ヘルパー -----

    private fun viewModel(
        chassisRepository: FakeChassisRepository = this.chassisRepository,
        preferencesRepository: FakePreferencesRepository = FakePreferencesRepository(),
        prefs: UserPreferences? = null,
        sheetRepository: FakeSetupSheetRepository = FakeSetupSheetRepository(),
        carRepository: FakeCarRepository = FakeCarRepository(initial = listOf(CAR)),
        sheetId: String? = null
    ): CalcViewModel {
        val preferences = prefs?.let { FakePreferencesRepository(it) } ?: preferencesRepository
        return CalcViewModel(
            savedStateHandle = SavedStateHandle(
                if (sheetId == null) emptyMap() else mapOf("sheetId" to sheetId)
            ),
            chassisRepository = chassisRepository,
            preferencesRepository = preferences,
            sheetRepository = sheetRepository,
            carRepository = carRepository
        )
    }

    private companion object {
        val CAR = Car(
            id = "car-1",
            name = "TT-02 #1",
            chassisId = "tamiya_tt02",
            createdAt = 1_000L,
            updatedAt = 1_000L
        )
    }
}
