package io.github.taskengineer.rcgear.domain.usecase

import io.github.taskengineer.rcgear.data.local.file.JsonBackupCodec
import io.github.taskengineer.rcgear.domain.model.SavedSetup
import io.github.taskengineer.rcgear.fake.FakeChassisRepository
import io.github.taskengineer.rcgear.fake.FakeSetupRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [ImportDataUseCase] のテスト（REF-1 / BUG-2 / BUG-3）。
 *
 * 守りたいこと:
 *  - 範囲外の値を持つ行を Room に入れない（後から SETUPS 詳細で落ちるのを防ぐ）
 *  - 取り込みが一括（＝トランザクション）で行われる
 *  - 既存データを壊さない（同名スキップ・不明シャーシスキップ）
 *
 * S-6 で MockK から Fake に移行した。Fake は `saved_setups.name` の
 * ユニーク制約を再現するので、**重複を畳み損ねると本物と同じように落ちる**。
 * MockK の `returns` ではこの制約が消え、取り込み全滅のバグを素通りさせてしまう。
 *
 * [JsonBackupCodec] も本物を使う。デコードを差し替えると
 * 「手書き JSON が実際にどう解釈されるか」という主眼が消えるため、
 * ここは意図的にワイヤ形式まで通す。
 *
 * メソッド名のプレフィクスでカテゴリを表現 (valid_, invalid_, duplicate_, version_, batch_)。
 */
class ImportDataUseCaseTest {

    private lateinit var setupRepository: FakeSetupRepository
    private lateinit var chassisRepository: FakeChassisRepository
    private lateinit var useCase: ImportDataUseCase

    /** 取り込まれたセッティング名（登録順） */
    private val importedNames: List<String> get() = setupRepository.stored.map { it.name }

    @Before
    fun setUp() {
        setupRepository = FakeSetupRepository()
        chassisRepository = FakeChassisRepository()
        useCase = ImportDataUseCase(setupRepository, chassisRepository, JsonBackupCodec())
    }

    // ----- valid_ -----

    @Test
    fun `valid_正常な JSON はセッティングと上書きを取り込む`() = runTest {
        val result = useCase(json(setups = listOf(setupJson()), overrides = listOf(overrideJson())))

        result.assertSuccess(importedSetups = 1, importedOverrides = 1)
        assertEquals(listOf("Rd1"), importedNames)
        assertEquals(listOf("tamiya_tt02"), chassisRepository.storedOverrides.map { it.chassisId })
    }

    @Test
    fun `valid_createdAt と updatedAt は元データのまま保持される`() = runTest {
        useCase(json(setups = listOf(setupJson(createdAt = 111L, updatedAt = 222L))))

        val imported = setupRepository.stored.single()
        assertEquals(111L, imported.createdAt)
        assertEquals(222L, imported.updatedAt)
    }

    // ----- invalid_: BUG-2 -----

    @Test
    fun `invalid_範囲外のピニオンを持つ行は棄却され Room に渡らない`() = runTest {
        // HANDOFF BUG-2 の再現手順: pinion を 5 に書き換えた JSON
        val result = useCase(json(setups = listOf(setupJson(name = "壊れた行", pinion = 5))))

        result.assertSuccess(importedSetups = 0, invalidSetups = 1)
        assertTrue("不正な行が Room に入っている", setupRepository.stored.isEmpty())
    }

    @Test
    fun `invalid_正常な行と不正な行が混在しても正常な行だけ取り込む`() = runTest {
        val result = useCase(
            json(
                setups = listOf(
                    setupJson(name = "OK1"),
                    setupJson(name = "NG-tire", tireMm = 200),
                    setupJson(name = "OK2"),
                    setupJson(name = "NG-cells", cells = 9)
                )
            )
        )

        result.assertSuccess(importedSetups = 2, invalidSetups = 2)
        assertEquals(listOf("OK1", "OK2"), importedNames)
    }

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
    fun `invalid_上書きの null フィールドは「上書きなし」として有効`() = runTest {
        val result = importOverrides(listOf(overrideJson(internalRatio = null, defaultTireMm = null)))

        result.assertSuccess(importedOverrides = 1)
    }

    // ----- duplicate_ -----

    @Test
    fun `duplicate_既存と同名のセッティングはスキップされる`() = runTest {
        setupRepository = FakeSetupRepository(initial = listOf(existingSetup("Rd1")))
        useCase = ImportDataUseCase(setupRepository, chassisRepository, JsonBackupCodec())

        val result = useCase(json(setups = listOf(setupJson(name = "Rd1"), setupJson(name = "Rd2"))))

        result.assertSuccess(importedSetups = 1, skippedSetups = 1)
        assertEquals(listOf("Rd1", "Rd2"), importedNames)
    }

    @Test
    fun `duplicate_ファイル内で同名が重複していても一括登録が全滅しない`() = runTest {
        // name にユニークインデックスがあるため、重複を渡すと一括 insert が
        // 制約違反で全件ロールバックされてしまう。事前に畳んでおく。
        // Fake もこの制約を再現するので、畳み忘れるとこのテストは例外で落ちる。
        val result = useCase(json(setups = listOf(setupJson(name = "同じ"), setupJson(name = "同じ"))))

        result.assertSuccess(importedSetups = 1, skippedSetups = 1)
        assertEquals(listOf("同じ"), importedNames)
    }

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

    // ----- version_ / format_ -----

    @Test
    fun `version_未来の schemaVersion は UnsupportedVersion を返す`() = runTest {
        val result = useCase("""{"schemaVersion":99,"exportedAt":0,"setups":[],"overrides":[]}""")

        assertEquals(ImportDataUseCase.Result.UnsupportedVersion, result)
        assertTrue("拒否したのに書き込んでいる", setupRepository.stored.isEmpty())
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
        useCase(json(setups = List(3) { setupJson(name = "S$it") }))

        assertEquals(1, setupRepository.restoreAllCallCount)
        assertEquals(3, setupRepository.stored.size)
    }

    // ----- ヘルパー -----

    private suspend fun importOverrides(overrides: List<String>) =
        useCase(json(overrides = overrides))

    private fun existingSetup(name: String) = SavedSetup(
        id = 1,
        name = name,
        chassisId = "tamiya_tt02",
        pinion = 22,
        spur = 84,
        internalRatioSnapshot = 2.6,
        kv = 6500,
        cells = 2,
        tireMm = 63,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun ImportDataUseCase.Result.assertSuccess(
        importedSetups: Int = 0,
        skippedSetups: Int = 0,
        invalidSetups: Int = 0,
        importedOverrides: Int = 0,
        skippedOverrides: Int = 0,
        invalidOverrides: Int = 0
    ) {
        val actual = this as? ImportDataUseCase.Result.Success
            ?: throw AssertionError("Success を期待したが $this だった")
        assertEquals(
            ImportDataUseCase.Result.Success(
                importedSetups = importedSetups,
                skippedSetups = skippedSetups,
                invalidSetups = invalidSetups,
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
