package io.github.taskengineer.rcgear.feature.calc

import io.github.taskengineer.rcgear.core.common.CalcRequestBus
import io.github.taskengineer.rcgear.domain.model.ChassisOverride
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput
import io.github.taskengineer.rcgear.domain.model.SavedSetup
import io.github.taskengineer.rcgear.domain.model.UserPreferences
import io.github.taskengineer.rcgear.domain.usecase.SaveSetupUseCase
import io.github.taskengineer.rcgear.fake.FakeCalculationHistoryRepository
import io.github.taskengineer.rcgear.fake.FakeChassisRepository
import io.github.taskengineer.rcgear.fake.FakePreferencesRepository
import io.github.taskengineer.rcgear.fake.FakeSetupRepository
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
 * `GearCalculationInput` は範囲外で例外を投げる設計なので、
 * 状態を変える全経路（前回値の復元・シャーシ選択・スライダー・流し込み）が
 * `recalculate()` のクランプを通っていないと、画面を開いた瞬間に落ちる。
 * この「全経路が 1 点に集まっている」という性質はコードを読まないと分からず、
 * 壊れても気づきにくいので、経路ごとにテストを置く。
 *
 * メソッド名のプレフィクスでカテゴリを表現
 * (init_, chassis_, slider_, clamp_, prefs_, bus_, save_)。
 */
class CalcViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val chassisRepository = FakeChassisRepository()
    private val setupRepository = FakeSetupRepository()
    private val historyRepository = FakeCalculationHistoryRepository()
    private val calcRequestBus = CalcRequestBus()

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

    // ----- bus_ -----

    @Test
    fun `bus_SETUPS から流し込むと値とシャーシが反映される`() = runTest {
        val vm = viewModel(prefs = UserPreferences(lastSelectedChassisId = "tamiya_tt02"))
        advanceUntilIdle()

        calcRequestBus.send(savedSetup(chassisId = "tamiya_ta08", pinion = 28, spur = 92))
        advanceUntilIdle()

        with(vm.uiState.value) {
            assertEquals("tamiya_ta08", selectedChassis?.chassis?.id)
            assertEquals(28, pinion)
            assertEquals(92, spur)
        }
        assertNull("受け取り後にバーが空になっていない", calcRequestBus.pendingSetup.value)
    }

    @Test
    fun `bus_DB ロード前に流し込まれても取りこぼさない`() = runTest {
        // CALC 初回表示と同時に流し込まれるケース。
        calcRequestBus.send(savedSetup(chassisId = "tamiya_ta08", pinion = 28, spur = 92))

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("tamiya_ta08", vm.uiState.value.selectedChassis?.chassis?.id)
        assertEquals(28, vm.uiState.value.pinion)
    }

    @Test
    fun `bus_範囲外の値を持つ古いセッティングを流し込んでも落ちない`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        calcRequestBus.send(savedSetup(chassisId = "tamiya_tt02", pinion = 5, spur = 999))
        advanceUntilIdle()

        assertEquals(GearCalculationInput.MIN_PINION, vm.uiState.value.pinion)
        assertEquals(GearCalculationInput.MAX_SPUR, vm.uiState.value.spur)
    }

    // ----- save_ -----

    @Test
    fun `save_シャーシ未選択では保存ダイアログを開かない`() = runTest {
        val vm = viewModel(prefs = UserPreferences(lastSelectedChassisId = null))
        advanceUntilIdle()

        vm.onSaveClick()

        assertNull(vm.uiState.value.saveDialog)
    }

    @Test
    fun `save_名前を入れて確定すると保存されダイアログが閉じる`() = runTest {
        val vm = viewModel(prefs = UserPreferences(lastSelectedChassisId = "tamiya_tt02"))
        advanceUntilIdle()

        vm.onSaveClick()
        vm.onSaveDialogNameChange("Rd1")
        vm.onSaveDialogConfirm()
        advanceUntilIdle()

        assertNull(vm.uiState.value.saveDialog)
        assertNotNull(vm.uiState.value.savedMessage)
        assertEquals("Rd1", setupRepository.stored.single().name)
    }

    @Test
    fun `save_同名があるとダイアログにエラーが出て開いたままになる`() = runTest {
        val vm = viewModel(prefs = UserPreferences(lastSelectedChassisId = "tamiya_tt02"))
        advanceUntilIdle()
        vm.onSaveClick()
        vm.onSaveDialogNameChange("Rd1")
        vm.onSaveDialogConfirm()
        advanceUntilIdle()

        vm.onSaveClick()
        vm.onSaveDialogNameChange("Rd1")
        vm.onSaveDialogConfirm()
        advanceUntilIdle()

        val dialog = vm.uiState.value.saveDialog
        assertNotNull("エラー時はダイアログを閉じてはいけない", dialog)
        assertNotNull(dialog?.errorMessage)
        assertTrue("多重タップ防止の isSaving が戻っていない", dialog?.isSaving == false)
        assertEquals(1, setupRepository.stored.size)
    }

    // ----- ヘルパー -----

    private fun viewModel(
        chassisRepository: FakeChassisRepository = this.chassisRepository,
        preferencesRepository: FakePreferencesRepository = FakePreferencesRepository(),
        prefs: UserPreferences? = null
    ): CalcViewModel {
        val preferences = prefs?.let { FakePreferencesRepository(it) } ?: preferencesRepository
        return CalcViewModel(
            chassisRepository = chassisRepository,
            preferencesRepository = preferences,
            saveSetupUseCase = SaveSetupUseCase(setupRepository, historyRepository),
            calcRequestBus = calcRequestBus
        )
    }

    private fun savedSetup(
        chassisId: String,
        pinion: Int,
        spur: Int
    ) = SavedSetup(
        id = 1,
        name = "Rd1",
        chassisId = chassisId,
        pinion = pinion,
        spur = spur,
        internalRatioSnapshot = 2.6,
        kv = 6500,
        cells = 2,
        tireMm = 63,
        createdAt = 0L,
        updatedAt = 0L
    )
}
