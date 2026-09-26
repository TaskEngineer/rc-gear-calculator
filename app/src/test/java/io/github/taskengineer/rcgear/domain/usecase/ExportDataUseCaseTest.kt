package io.github.taskengineer.rcgear.domain.usecase

import io.github.taskengineer.rcgear.data.local.file.JsonBackupCodec
import io.github.taskengineer.rcgear.domain.common.TimeProvider
import io.github.taskengineer.rcgear.fake.FakeChassisRepository
import io.github.taskengineer.rcgear.fake.FakeSetupRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ExportDataUseCase] のテスト（REF-3 / S-6）。
 *
 * 一番守りたいのは **エクスポートとインポートが往復すること**。
 * 片方だけ直してもう片方を忘れる、という壊れ方が一番起きやすく、
 * しかもユーザーから見ると「バックアップしたのに戻せない」という最悪の症状になる。
 * そのため往復テストを 1 本置いて、両 UseCase を同時に縛る。
 *
 * メソッド名のプレフィクスでカテゴリを表現 (export_, roundtrip_)。
 */
class ExportDataUseCaseTest {

    private val codec = JsonBackupCodec()
    private val fixedTime = TimeProvider { EXPORTED_AT }

    // ----- export_ -----

    @Test
    fun `export_空のDBでも有効な JSON を書き出す`() = runTest {
        val json = exportFrom(FakeSetupRepository(), FakeChassisRepository())

        assertTrue(json.contains("\"schemaVersion\": 1"))
        assertTrue(json.contains("\"exportedAt\": $EXPORTED_AT"))
    }

    @Test
    fun `export_書き出し時刻は TimeProvider の値になる`() = runTest {
        val json = exportFrom(FakeSetupRepository(), FakeChassisRepository())

        // 壁時計を直接読んでいたらこの値にはならない
        assertTrue(json.contains("\"exportedAt\": $EXPORTED_AT"))
    }

    @Test
    fun `export_端末固有の id は書き出されない`() = runTest {
        val setups = FakeSetupRepository().apply { save("Rd1", "tamiya_tt02", 22, 84, 2.6, 6500, 2, 63) }

        val json = exportFrom(setups, FakeChassisRepository())

        assertTrue("Room の自動採番 id が漏れている", !json.contains("\"id\""))
    }

    // ----- roundtrip_ -----

    @Test
    fun `roundtrip_エクスポートした JSON をインポートすると同じ内容が復元される`() = runTest {
        val source = FakeSetupRepository().apply {
            now = 555L
            save("Rd1", "tamiya_tt02", 22, 84, 2.6, 6500, 2, 63)
            save("Rd2", "tamiya_ta08", 26, 90, 1.9, 7500, 2, 62)
        }
        val sourceChassis = FakeChassisRepository().apply {
            now = 666L
            overrideChassis("tamiya_tt02", internalRatio = 2.7, defaultTireMm = null, note = null)
        }

        val json = exportFrom(source, sourceChassis)

        // まっさらな DB に戻す
        val restored = FakeSetupRepository()
        val restoredChassis = FakeChassisRepository()
        val result = ImportDataUseCase(restored, restoredChassis, codec)(json)

        assertEquals(
            ImportDataUseCase.Result.Success(
                importedSetups = 2,
                skippedSetups = 0,
                invalidSetups = 0,
                importedOverrides = 1,
                skippedOverrides = 0,
                invalidOverrides = 0
            ),
            result
        )
        // id は端末固有なので一致しない。それ以外は一致する
        assertEquals(
            source.stored.map { it.copy(id = 0) }.toSet(),
            restored.stored.map { it.copy(id = 0) }.toSet()
        )
        assertEquals(sourceChassis.storedOverrides, restoredChassis.storedOverrides)
    }

    @Test
    fun `roundtrip_2回書き出した JSON は同一になる`() = runTest {
        val setups = FakeSetupRepository().apply { save("Rd1", "tamiya_tt02", 22, 84, 2.6, 6500, 2, 63) }
        val chassis = FakeChassisRepository()

        assertEquals(exportFrom(setups, chassis), exportFrom(setups, chassis))
    }

    // ----- ヘルパー -----

    private suspend fun exportFrom(
        setupRepository: FakeSetupRepository,
        chassisRepository: FakeChassisRepository
    ): String = ExportDataUseCase(setupRepository, chassisRepository, codec, fixedTime)()

    private companion object {
        const val EXPORTED_AT = 1_750_000_000_000L
    }
}
