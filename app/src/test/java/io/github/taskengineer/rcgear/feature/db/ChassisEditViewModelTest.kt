package io.github.taskengineer.rcgear.feature.db

import androidx.lifecycle.SavedStateHandle
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.ui.UiText
import io.github.taskengineer.rcgear.domain.model.ChassisOverride
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput
import io.github.taskengineer.rcgear.fake.FakeChassisRepository
import io.github.taskengineer.rcgear.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * [ChassisEditViewModel] のテスト（BUG-7）。
 *
 * ここで守りたいのは **「入るはずのない値を入れさせてから弾く」に戻らないこと**。
 * 以前は保存を押すまで何も言わず、押した瞬間に違反を 1 件ずつ出していた。
 * 入力のたびに検証する形になっているかを、経路ごとに押さえる。
 *
 * 併せて、テストがそもそも 1 本も無かった画面なので、
 * 「標準値と同じ値は上書きにしない」という元からの仕様も固定する。
 */
class ChassisEditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // ----- init_ -----

    @Test
    fun `init_現在の有効値が入力欄に入る`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        with(vm.uiState.value) {
            assertEquals("2.60", ratioInput)
            assertEquals("63", tireInput)
            assertTrue("初期値は有効なので保存できるはず", canSave)
        }
    }

    @Test
    fun `init_読み込んだ上書きが範囲外なら開いた時点でエラーが出る`() = runTest {
        // 古いデータや手で書いた JSON 由来。ここで検証していないと
        // 「触っていないのに保存できる」状態が残る
        val chassis = FakeChassisRepository(
            initialOverrides = listOf(
                ChassisOverride(
                    chassisId = "tamiya_tt02",
                    internalRatio = null,
                    defaultTireMm = 200,
                    note = null,
                    updatedAt = 0L
                )
            )
        )
        val vm = viewModel(chassis)
        advanceUntilIdle()

        assertEquals("200", vm.uiState.value.tireInput)
        assertFalse(vm.uiState.value.canSave)
    }

    // ----- input_: 入力のたびの検証（BUG-7 本体） -----

    @Test
    fun `input_範囲外のタイヤ径は入力した時点でエラーになり保存できない`() = runTest {
        // BUG-1 の再発（不正値が DB に入る）には至らないが、
        // 200 まで入力させてから保存時に弾くのは SheetEditScreen と挙動が違う
        val vm = viewModel()
        advanceUntilIdle()

        vm.onTireChange("200")

        with(vm.uiState.value) {
            assertEquals(
                UiText.Res(
                    R.string.chassis_edit_error_tire_mm,
                    listOf(GearCalculationInput.MIN_TIRE_MM, GearCalculationInput.MAX_TIRE_MM)
                ),
                tireError
            )
            assertFalse("エラーが出ているのに保存できてしまう", canSave)
        }
    }

    @Test
    fun `input_内部減速比が 0 なら入力した時点でエラーになる`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onRatioChange("0")

        assertEquals(
            UiText.Res(R.string.chassis_edit_error_internal_ratio),
            vm.uiState.value.ratioError
        )
        assertFalse(vm.uiState.value.canSave)
    }

    @Test
    fun `input_数値でない文字列もエラーになる`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onTireChange("ろくじゅうさん")

        assertEquals(
            UiText.Res(
                R.string.chassis_edit_error_tire_mm,
                listOf(GearCalculationInput.MIN_TIRE_MM, GearCalculationInput.MAX_TIRE_MM)
            ),
            vm.uiState.value.tireError
        )
    }

    @Test
    fun `input_直すとエラーが消えて保存できるようになる`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onTireChange("200")

        vm.onTireChange("65")

        assertNull(vm.uiState.value.tireError)
        assertTrue(vm.uiState.value.canSave)
    }

    @Test
    fun `input_空欄はエラーにしないが保存もできない`() = runTest {
        // 消して打ち直している最中に赤くなるのは煩わしい。
        // ただし空のまま保存されると「上書きを外す」操作と区別が付かなくなる
        val vm = viewModel()
        advanceUntilIdle()

        vm.onTireChange("")

        assertNull("打ち直しの途中で赤くなっている", vm.uiState.value.tireError)
        assertFalse("空欄のまま保存できてしまう", vm.uiState.value.canSave)
    }

    @Test
    fun `input_備考はいくら書いても保存を止めない`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onNoteChange("リヤ ワンウェイ")

        assertTrue(vm.uiState.value.canSave)
    }

    // ----- save_ -----

    @Test
    fun `save_エラーが残っている間は書き込まない`() = runTest {
        // 画面側はボタンを無効にするが、ViewModel 単体でも止まること
        val chassis = FakeChassisRepository()
        val vm = viewModel(chassis)
        advanceUntilIdle()
        vm.onTireChange("200")

        vm.onSave()
        advanceUntilIdle()

        assertTrue("不正な値が上書きとして入っている", chassis.storedOverrides.isEmpty())
    }

    @Test
    fun `save_範囲内の値は上書きとして保存される`() = runTest {
        val chassis = FakeChassisRepository()
        val vm = viewModel(chassis)
        advanceUntilIdle()

        vm.onTireChange("65")
        vm.onSave()
        advanceUntilIdle()

        val stored = chassis.storedOverrides.single()
        assertEquals(65, stored.defaultTireMm)
        // 触っていない項目は標準値と同じなので上書きにしない
        assertNull(stored.internalRatio)
    }

    @Test
    fun `save_標準値と同じ値なら上書きを作らない`() = runTest {
        val chassis = FakeChassisRepository()
        val vm = viewModel(chassis)
        advanceUntilIdle()

        // 初期値は標準値そのもの。何も変えずに保存する
        vm.onSave()
        advanceUntilIdle()

        assertTrue(chassis.storedOverrides.isEmpty())
    }

    // ----- ヘルパー -----

    private fun viewModel(
        chassisRepository: FakeChassisRepository = FakeChassisRepository()
    ) = ChassisEditViewModel(
        savedStateHandle = SavedStateHandle(mapOf("chassisId" to "tamiya_tt02")),
        chassisRepository = chassisRepository
    )
}
