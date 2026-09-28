package io.github.taskengineer.rcgear.feature.db

import androidx.lifecycle.SavedStateHandle
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.ui.ScreenEvent
import io.github.taskengineer.rcgear.core.ui.UiText
import io.github.taskengineer.rcgear.domain.model.ChassisDrive
import io.github.taskengineer.rcgear.domain.model.UserChassis
import io.github.taskengineer.rcgear.fake.FakeChassisRepository
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
 * [UserChassisEditViewModel] のテスト（F-5）。
 *
 * 守りたいのは **id の規約**（`user_` 接頭辞）と **範囲検証**。
 * 前者が崩れると同梱 DB のエントリと衝突し、インポートの判定まで狂う。
 */
class UserChassisEditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `create_入力した内容で追加され id は user 接頭辞になる`() = runTest {
        val chassis = FakeChassisRepository()
        val vm = viewModel(chassis)
        advanceUntilIdle()

        vm.onMakerChange("XRAY")
        vm.onNameChange("X4")
        vm.onRatioChange("1.90")
        vm.onTireChange("62")
        vm.onDriveChange(ChassisDrive.BELT_4WD)
        vm.onCenterDiffChange(false)
        vm.onSave()
        advanceUntilIdle()

        val saved = chassis.getAllUserChassisOnce().single()
        assertTrue("id が規約に反している: ${saved.id}", UserChassis.isUserDefined(saved.id))
        assertEquals("XRAY", saved.makerName)
        assertEquals("X4", saved.name)
        assertEquals(1.9, saved.internalRatio, 1e-9)
        assertEquals(62, saved.defaultTireMm)
        assertEquals(ChassisDrive.BELT_4WD, saved.traits.drive)
        assertEquals(false, saved.traits.hasCenterDiff)
        assertEquals(ScreenEvent.NavigateBack, vm.events.first())
    }

    @Test
    fun `create_素性は不明のままにできる`() = runTest {
        // 「分からないから隠す」をしないので、不明なら全項目が出る（HANDOFF §5.7）
        val chassis = FakeChassisRepository()
        val vm = viewModel(chassis)
        advanceUntilIdle()

        vm.onMakerChange("自作")
        vm.onNameChange("試作1号")
        vm.onRatioChange("2.0")
        vm.onTireChange("63")
        vm.onSave()
        advanceUntilIdle()

        val saved = chassis.getAllUserChassisOnce().single()
        assertNull(saved.traits.drive)
        assertNull(saved.traits.hasCenterDiff)
    }

    @Test
    fun `create_名前が空なら保存しない`() = runTest {
        val chassis = FakeChassisRepository()
        val vm = viewModel(chassis)
        advanceUntilIdle()

        vm.onMakerChange("XRAY")
        vm.onSave()
        advanceUntilIdle()

        assertTrue(chassis.getAllUserChassisOnce().isEmpty())
        assertEquals(UiText.Res(R.string.user_chassis_error_name), vm.uiState.value.errorMessage)
    }

    @Test
    fun `create_内部減速比が範囲外なら保存しない`() = runTest {
        val chassis = FakeChassisRepository()
        val vm = viewModel(chassis)
        advanceUntilIdle()

        vm.onMakerChange("XRAY")
        vm.onNameChange("X4")
        vm.onRatioChange("0")
        vm.onTireChange("62")
        vm.onSave()
        advanceUntilIdle()

        assertTrue(chassis.getAllUserChassisOnce().isEmpty())
        assertEquals(
            UiText.Res(R.string.chassis_edit_error_internal_ratio),
            vm.uiState.value.errorMessage
        )
    }

    @Test
    fun `edit_既存の値が載り更新しても作成日時は動かない`() = runTest {
        val chassis = FakeChassisRepository()
        chassis.now = 1_000L
        val id = chassis.addUserChassis(sample())
        chassis.now = 9_999L
        val vm = viewModel(chassis, chassisId = id)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isNew)
        assertEquals("X4", vm.uiState.value.nameInput)

        vm.onNameChange("X4 2026")
        vm.onSave()
        advanceUntilIdle()

        val saved = chassis.getAllUserChassisOnce().single()
        assertEquals("X4 2026", saved.name)
        assertEquals(1_000L, saved.createdAt)
        assertEquals(9_999L, saved.updatedAt)
    }

    @Test
    fun `edit_対象が消えていたらすぐ戻る`() = runTest {
        val vm = viewModel(FakeChassisRepository(), chassisId = "user_missing")
        advanceUntilIdle()

        assertEquals(ScreenEvent.NavigateBack, vm.events.first())
    }

    @Test
    fun `delete_確認してから削除する`() = runTest {
        val chassis = FakeChassisRepository()
        val id = chassis.addUserChassis(sample())
        val vm = viewModel(chassis, chassisId = id)
        advanceUntilIdle()

        vm.onDeleteClick()
        assertTrue(vm.uiState.value.showDeleteConfirm)
        vm.onDeleteConfirm()
        advanceUntilIdle()

        assertTrue(chassis.getAllUserChassisOnce().isEmpty())
        assertEquals(ScreenEvent.NavigateBack, vm.events.first())
    }

    @Test
    fun `list_追加した自作シャーシが一覧に混ざる`() = runTest {
        // 同じメーカー名なら既存の見出しに合流する（「タミヤ」が 2 つ並ばない）
        val chassis = FakeChassisRepository()
        chassis.addUserChassis(sample().copy(makerName = "タミヤ", name = "自作TT"))
        advanceUntilIdle()

        val makers = chassis.getAllMakers().first()
        val tamiya = makers.single { it.name == "タミヤ" }
        assertTrue(tamiya.chassis.any { it.name == "自作TT" })
        assertEquals(1, makers.count { it.name == "タミヤ" })
    }

    // ----- helpers -----

    private fun sample() = UserChassis(
        id = "",
        makerName = "XRAY",
        name = "X4",
        internalRatio = 1.9,
        defaultTireMm = 62,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun viewModel(
        chassis: FakeChassisRepository,
        chassisId: String? = null
    ) = UserChassisEditViewModel(
        savedStateHandle = SavedStateHandle(
            if (chassisId == null) emptyMap() else mapOf("chassisId" to chassisId)
        ),
        chassisRepository = chassis
    )
}
