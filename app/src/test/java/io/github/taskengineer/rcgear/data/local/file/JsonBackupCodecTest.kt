package io.github.taskengineer.rcgear.data.local.file

import io.github.taskengineer.rcgear.data.local.file.dto.ExportDataDto
import io.github.taskengineer.rcgear.domain.backup.BackupCodec
import io.github.taskengineer.rcgear.domain.backup.BackupData
import io.github.taskengineer.rcgear.domain.model.ChassisOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [JsonBackupCodec] のワイヤ形式テスト（Phase 0 レビュー指摘）。
 *
 * これまで codec は UseCase のテストで Fake に差し替えられており、
 * **実際の JSON 形式を検証するテストが 1 つも無かった**。
 * エクスポート JSON は AGENTS.md §4 で互換性を約束している対外形式なので、
 * ここで固定する。
 *
 * とくに [legacyJsonWithoutSchemaVersion] は S-6 より前に実機で書き出した実ファイルの形（`schemaVersion`
 * キーが無い）で、これが読めなくなる変更を検出するためのゴールデンデータ。
 * 手で書き換えないこと。
 */
class JsonBackupCodecTest {

    private val codec = JsonBackupCodec()

    /**
     * S-6 以前に実機が書き出した形式。`schemaVersion` キーが存在しない。
     * kotlinx.serialization が既定値と一致するフィールドを省いた結果であり、
     * 空の `setups` / `overrides` も同様に落ちていた。
     */
    private val legacyJsonWithoutSchemaVersion = """
        {
            "exportedAt": 1727000000000,
            "setups": [
                {
                    "name": "Rd1",
                    "chassisId": "tamiya_tt02",
                    "pinion": 22,
                    "spur": 84,
                    "internalRatioSnapshot": 2.6,
                    "kv": 6500,
                    "cells": 2,
                    "tireMm": 63,
                    "createdAt": 1726000000000,
                    "updatedAt": 1727000000000
                }
            ],
            "overrides": [
                {
                    "chassisId": "tamiya_tt02",
                    "internalRatio": 2.7,
                    "updatedAt": 1727000000000
                }
            ]
        }
    """.trimIndent()

    @Test
    fun `schemaVersion の無い旧ファイルは v1 として読める`() {
        val result = codec.decode(legacyJsonWithoutSchemaVersion)

        assertTrue("旧形式が読めなくなっている: $result", result is BackupCodec.DecodeResult.Success)
        val data = (result as BackupCodec.DecodeResult.Success).data
        assertEquals(1727000000000L, data.exportedAt)
        // v1 の保存セッティングは legacySetups に入る（M-3 で本体のテーブルは消えた）。
        // v2 の形への変換は M-6 の v1 -> v2 インポータが行う
        assertEquals(1, data.legacySetups.size)
        assertEquals("Rd1", data.legacySetups[0].name)
        assertEquals(22, data.legacySetups[0].pinion)
        // id は端末固有なので JSON に無い。未採番の 0 で埋まる
        assertEquals(0L, data.legacySetups[0].id)
        assertEquals(1, data.overrides.size)
        assertEquals(2.7, data.overrides[0].internalRatio ?: 0.0, 1e-9)
        // 省略された任意フィールドは null（= 上書きしない）
        assertEquals(null, data.overrides[0].defaultTireMm)
    }

    /**
     * 版の省略は **常に v1**。CURRENT_SCHEMA_VERSION に連動させると、
     * v2 を出した瞬間に旧ファイルが v2 と誤認される。
     */
    @Test
    fun `省略時の版は CURRENT ではなく固定値 1 である`() {
        assertEquals(1, ExportDataDto.OMITTED_SCHEMA_VERSION)
    }

    @Test
    fun `書き出した JSON には schemaVersion が含まれる`() {
        val json = codec.encode(BackupData(exportedAt = 1L))

        // S-6 の再発防止（encodeDefaults = true が消えると落ちる）
        assertTrue("schemaVersion が出ていない:\n$json", json.contains("\"schemaVersion\""))
        assertTrue("空の setups が省かれている:\n$json", json.contains("\"setups\""))
        assertTrue("空の overrides が省かれている:\n$json", json.contains("\"overrides\""))
    }

    @Test
    fun `エンコードとデコードは往復する`() {
        val original = BackupData(
            exportedAt = 1727000000000L,
            overrides = listOf(
                ChassisOverride(
                    chassisId = "tamiya_tt02",
                    internalRatio = 2.7,
                    defaultTireMm = null,
                    note = "メモ",
                    updatedAt = 1727000000000L
                )
            )
        )

        val result = codec.decode(codec.encode(original))

        assertTrue(result is BackupCodec.DecodeResult.Success)
        assertEquals(original, (result as BackupCodec.DecodeResult.Success).data)
    }

    @Test
    fun `v1 の保存セッティングは書き出さない`() {
        // M-3 でテーブルごと無くなったので、書き出す側がこれを埋めることはない。
        // 読む側（legacySetups）は AGENTS.md §4 に従って永久に残す
        val json = codec.encode(BackupData(exportedAt = 1L))
        assertTrue(json.contains("\"setups\": []"))
    }

    @Test
    fun `未知のバージョンは UnsupportedVersion`() {
        val future = """{"schemaVersion":99,"exportedAt":1,"setups":[],"overrides":[]}"""

        assertEquals(BackupCodec.DecodeResult.UnsupportedVersion, codec.decode(future))
    }

    @Test
    fun `JSON として壊れていれば InvalidFormat`() {
        assertEquals(BackupCodec.DecodeResult.InvalidFormat, codec.decode("not json"))
    }

    @Test
    fun `必須フィールドが欠けていれば InvalidFormat`() {
        // exportedAt は既定値を持たないので欠落は形式不正
        assertEquals(BackupCodec.DecodeResult.InvalidFormat, codec.decode("""{"setups":[]}"""))
    }

    @Test
    fun `知らないキーは無視して読み進める`() {
        val withExtra = """
            {"schemaVersion":1,"exportedAt":1,"setups":[],"overrides":[],"futureField":"x"}
        """.trimIndent()

        assertTrue(codec.decode(withExtra) is BackupCodec.DecodeResult.Success)
    }
}
