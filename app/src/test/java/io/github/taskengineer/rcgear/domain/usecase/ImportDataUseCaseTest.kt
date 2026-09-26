package io.github.taskengineer.rcgear.domain.usecase

import io.github.taskengineer.rcgear.data.local.file.JsonBackupCodec
import io.github.taskengineer.rcgear.fake.FakeChassisRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [ImportDataUseCase] のテスト（REF-1 / BUG-2 / BUG-3）。
 *
 * 守りたいこと:
 *  - 範囲外の値を持つ行を Room に入れない（後から画面を開いた時に落ちるのを防ぐ）
 *  - 取り込みが一括（＝トランザクション）で行われる
 *  - 既存データを壊さない（不明シャーシスキップ）
 *
 * [JsonBackupCodec] は本物を使う。デコードを差し替えると
 * 「手書き JSON が実際にどう解釈されるか」という主眼が消えるため、
 * ここは意図的にワイヤ形式まで通す。
 *
 * **M-3 時点でセッティングの取り込みは無い。** v1 ファイルの `setups` は
 * 件数だけ報告し、車 + シートへの変換は M-6 で入る。
 *
 * メソッド名のプレフィクスでカテゴリを表現 (valid_, invalid_, duplicate_, version_, batch_, legacy_)。
 */
class ImportDataUseCaseTest {

    private lateinit var chassisRepository: FakeChassisRepository
    private lateinit var useCase: ImportDataUseCase

    @Before
    fun setUp() {
        chassisRepository = FakeChassisRepository()
        useCase = ImportDataUseCase(chassisRepository, JsonBackupCodec())
    }

    // ----- valid_ -----

    @Test
    fun `valid_正常な JSON は上書きを取り込む`() = runTest {
        val result = useCase(json(overrides = listOf(overrideJson())))

        result.assertSuccess(importedOverrides = 1)
        assertEquals(listOf("tamiya_tt02"), chassisRepository.storedOverrides.map { it.chassisId })
    }

    @Test
    fun `valid_updatedAt は元データのまま保持される`() = runTest {
        useCase(json(overrides = listOf(overrideJson(updatedAt = 222L))))

        assertEquals(222L, chassisRepository.storedOverrides.single().updatedAt)
    }

    // ----- invalid_: BUG-2 -----

    @Test
    fun `invalid_内部減速比が 0 以下の上書きは棄却される`() = runTest {
        val result = importOverrides(listOf(overrideJson(internalRatio = 0.0)))

        result.assertSuccess(importedOverrides = 0, invalidOverrides = 1)
        assertTrue(chassisRepository.storedOverrides.isEmpty())
    }

    @Test
    fun `invalid_範囲外のタイヤ径を持つ上書きは棄却される`() = runTest {
        // BUG-1 と同じ値が JSON 経由で入ってくるケース
        val result = importOverrides(listOf(overrideJson(defaultTireMm = 200)))

        result.assertSuccess(importedOverrides = 0, invalidOverrides = 1)
    }

    @Test
    fun `invalid_正常な行と不正な行が混在しても正常な行だけ取り込む`() = runTest {
        val result = importOverrides(
            listOf(
                overrideJson(chassisId = "tamiya_tt02"),
                overrideJson(chassisId = "tamiya_ta08", defaultTireMm = 200)
            )
        )

        result.assertSuccess(importedOverrides = 1, invalidOverrides = 1)
        assertEquals(listOf("tamiya_tt02"), chassisRepository.storedOverrides.map { it.chassisId })
    }

    @Test
    fun `invalid_上書きの null フィールドは「上書きなし」として有効`() = runTest {
        val result = importOverrides(listOf(overrideJson(internalRatio = null, defaultTireMm = null)))

        result.assertSuccess(importedOverrides = 1)
    }

    // ----- duplicate_ -----

    @Test
    fun `duplicate_標準DBに無いシャーシの上書きはスキップされる`() = runTest {
        val result = importOverrides(
            listOf(
                overrideJson(chassisId = "tamiya_tt02"),
                overrideJson(chassisId = "unknown_chassis")
            )
        )

        result.assertSuccess(importedOverrides = 1, skippedOverrides = 1)
        assertEquals(listOf("tamiya_tt02"), chassisRepository.storedOverrides.map { it.chassisId })
    }

    @Test
    fun `duplicate_ファイル内で同じシャーシの上書きが重複したら後勝ちで1件になる`() = runTest {
        val result = importOverrides(
            listOf(overrideJson(internalRatio = 2.7), overrideJson(internalRatio = 2.8))
        )

        result.assertSuccess(importedOverrides = 1)
        assertEquals(2.8, chassisRepository.storedOverrides.single().internalRatio!!, 1e-9)
    }

    // ----- legacy_: v1 の保存セッティング -----

    @Test
    fun `legacy_v1 の保存セッティングは件数だけ報告される`() = runTest {
        // AGENTS.md §4「旧バージョンの読み込みは残す」。読めてはいるが、
        // 受け皿になる車 / シートの Repository が無いので取り込みは M-6 まで保留
        val result = useCase(json(setups = listOf(setupJson(), setupJson(name = "Rd2"))))

        result.assertSuccess(pendingLegacySetups = 2)
    }

    @Test
    fun `legacy_保存セッティングが範囲外でも上書きの取り込みは止まらない`() = runTest {
        val result = useCase(
            json(
                setups = listOf(setupJson(pinion = 5)),
                overrides = listOf(overrideJson())
            )
        )

        result.assertSuccess(pendingLegacySetups = 1, importedOverrides = 1)
    }

    // ----- version_ / format_ -----

    @Test
    fun `version_未来の schemaVersion は UnsupportedVersion を返す`() = runTest {
        val result = useCase("""{"schemaVersion":99,"exportedAt":0,"setups":[],"overrides":[]}""")

        assertEquals(ImportDataUseCase.Result.UnsupportedVersion, result)
        assertTrue("拒否したのに書き込んでいる", chassisRepository.storedOverrides.isEmpty())
    }

    @Test
    fun `version_壊れた JSON は InvalidFormat を返す`() = runTest {
        assertEquals(ImportDataUseCase.Result.InvalidFormat, useCase("これはJSONではない"))
        assertEquals(ImportDataUseCase.Result.InvalidFormat, useCase("""{"schemaVersion":1}"""))
    }

    // ----- batch_: BUG-3 -----

    @Test
    fun `batch_取り込みは一括メソッドで1回だけ呼ばれる`() = runTest {
        // 1 件ずつ insert すると途中失敗で半端に取り込まれる（BUG-3）。
        // Room はコレクションを受ける @Insert を 1 トランザクションで実行するため、
        // 一括メソッドに 1 回で渡していることを保証する。
        importOverrides(
            listOf(
                overrideJson(chassisId = "tamiya_tt02"),
                overrideJson(chassisId = "tamiya_ta08")
            )
        )

        assertEquals(1, chassisRepository.restoreAllOverridesCallCount)
        assertEquals(2, chassisRepository.storedOverrides.size)
    }

    // ----- ヘルパー -----

    private suspend fun importOverrides(overrides: List<String>) =
        useCase(json(overrides = overrides))

    private fun ImportDataUseCase.Result.assertSuccess(
        pendingLegacySetups: Int = 0,
        importedOverrides: Int = 0,
        skippedOverrides: Int = 0,
        invalidOverrides: Int = 0
    ) {
        val actual = this as? ImportDataUseCase.Result.Success
            ?: throw AssertionError("Success を期待したが $this だった")
        assertEquals(
            ImportDataUseCase.Result.Success(
                pendingLegacySetups = pendingLegacySetups,
                importedOverrides = importedOverrides,
                skippedOverrides = skippedOverrides,
                invalidOverrides = invalidOverrides
            ),
            actual
        )
    }

    private fun json(
        setups: List<String> = emptyList(),
        overrides: List<String> = emptyList(),
        schemaVersion: Int = 1
    ): String = """
        {
          "schemaVersion": $schemaVersion,
          "exportedAt": 1750000000000,
          "setups": [${setups.joinToString(",")}],
          "overrides": [${overrides.joinToString(",")}]
        }
    """.trimIndent()

    private fun setupJson(
        name: String = "Rd1",
        chassisId: String = "tamiya_tt02",
        pinion: Int = 22,
        spur: Int = 84,
        internalRatioSnapshot: Double = 2.6,
        kv: Int = 6500,
        cells: Int = 2,
        tireMm: Int = 63,
        createdAt: Long = 1L,
        updatedAt: Long = 2L
    ): String = """
        {
          "name": "$name", "chassisId": "$chassisId",
          "pinion": $pinion, "spur": $spur, "internalRatioSnapshot": $internalRatioSnapshot,
          "kv": $kv, "cells": $cells, "tireMm": $tireMm,
          "createdAt": $createdAt, "updatedAt": $updatedAt
        }
    """.trimIndent()

    private fun overrideJson(
        chassisId: String = "tamiya_tt02",
        internalRatio: Double? = 2.7,
        defaultTireMm: Int? = 64,
        updatedAt: Long = 3L
    ): String = """
        {
          "chassisId": "$chassisId",
          "internalRatio": ${internalRatio ?: "null"},
          "defaultTireMm": ${defaultTireMm ?: "null"},
          "note": null,
          "updatedAt": $updatedAt
        }
    """.trimIndent()
}
