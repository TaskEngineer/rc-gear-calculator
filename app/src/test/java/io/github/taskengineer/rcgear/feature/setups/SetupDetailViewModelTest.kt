package io.github.taskengineer.rcgear.feature.setups

import androidx.lifecycle.SavedStateHandle
import io.github.taskengineer.rcgear.core.ui.ScreenEvent
import io.github.taskengineer.rcgear.domain.model.SavedSetup
import io.github.taskengineer.rcgear.fake.FakeChassisRepository
import io.github.taskengineer.rcgear.fake.FakePreferencesRepository
import io.github.taskengineer.rcgear.fake.FakeSetupRepository
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
 * [SetupDetailViewModel] のテスト（U-2）。
 *
 * 見たいのは **画面を閉じる経路がイベントとして流れること**。
 * 以前は `isDeleted` / `notFound` という Boolean を UiState に置いていたため、
 * 「戻る」が状態として残り続けた。イベント化したので「一度だけ流れる」ことを
 * ここで固定する。`init` で即 emit するケース（対象が無い）も、
 * バッファ付き Channel なので購読が後でも取りこぼさない。
 */
class SetupDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val setupRepository = FakeSetupRepository()
    private val chassisRepository = FakeChassisRepository()

    @Test
    fun `保存値とシャーシが読み込まれる`() = runTest {
        val vm = viewModel(storeSetup())
        advanceUntilIdle()

        with(vm.uiState.value) {
            assertEquals("Rd1", setup?.name)
            assertEquals(FakeChassisRepository.TT02.name, chassis?.name)
            assertNotNull("保存時の値で計算できていない", snapshotResult)
            // 上書きが無いので「保存時 ≠ 現在」の差分は出ない
            assertNull(currentResult)
        }
    }

    @Test
    fun `内部減速比が上書きされていると現在値での計算も出る`() = runTest {
        // スナップショット凍結（PLAN 9.5）の肝。保存後に DB 側が変わったことを見せる
        chassisRepository.overrideChassis(
            chassisId = FakeChassisRepository.TT02.id,
            internalRatio = 2.9,
            defaultTireMm = null,
            note = null
        )
        val vm = viewModel(storeSetup())
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.currentResult)
    }

    @Test
    fun `削除を確定すると NavigateBack が流れる`() = runTest {
        val vm = viewModel(storeSetup())
        advanceUntilIdle()

        vm.onDeleteClick()
        vm.onDeleteConfirm()
        advanceUntilIdle()

        assertEquals(ScreenEvent.NavigateBack, vm.events.first())
        assertTrue("削除されていない", setupRepository.stored.isEmpty())
        assertTrue("確認ダイアログが開いたまま", !vm.uiState.value.showDeleteConfirm)
    }

    @Test
    fun `存在しない ID なら読み込み時に NavigateBack が流れる`() = runTest {
        // 削除済みのセッティングへ戻ってきた場合。購読より emit が先に起きるが、
        // Channel のバッファに残るので取りこぼさない
        val vm = viewModel(setupId = 999L)
        advanceUntilIdle()

        assertEquals(ScreenEvent.NavigateBack, vm.events.first())
    }

    // ----- ヘルパー -----

    private suspend fun storeSetup(): Long {
        val stored = SavedSetup(
            id = 0,
            name = "Rd1",
            chassisId = FakeChassisRepository.TT02.id,
            pinion = 28,
            spur = 92,
            internalRatioSnapshot = FakeChassisRepository.TT02.internalRatio,
            kv = 6500,
            cells = 2,
            tireMm = 63,
            createdAt = 0L,
            updatedAt = 0L
        )
        // Fake は Room と同じく id を採番し直すので、採番後の id を返す
        setupRepository.restoreAll(listOf(stored))
        return setupRepository.stored.single().id
    }

    private fun viewModel(setupId: Long) = SetupDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("setupId" to setupId)),
        setupRepository = setupRepository,
        chassisRepository = chassisRepository,
        preferencesRepository = FakePreferencesRepository()
    )
}
