package io.github.taskengineer.rcgear.data.local.file

import io.github.taskengineer.rcgear.data.local.file.dto.ExportDataDto
import io.github.taskengineer.rcgear.domain.backup.BackupCodec
import io.github.taskengineer.rcgear.domain.backup.BackupData
import io.github.taskengineer.rcgear.domain.model.Car
import io.github.taskengineer.rcgear.domain.model.ChassisOverride
import io.github.taskengineer.rcgear.domain.model.SessionConditions
import io.github.taskengineer.rcgear.domain.model.SetupSheet
import io.github.taskengineer.rcgear.domain.model.SetupSheetWithValues
import io.github.taskengineer.rcgear.domain.model.SetupValue
import io.github.taskengineer.rcgear.domain.model.SetupValues
import io.github.taskengineer.rcgear.fake.FakeIdGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [JsonBackupCodec] のワイヤ形式テスト（Phase 0 レビュー指摘 / M-6 で v2 化）。
 *
 * エクスポート JSON は AGENTS.md §4 で互換性を約束している対外形式なので、
 * 形をここで固定する。
 *
 * とくに [legacyJsonWithoutSchemaVersion] は S-6 より前に実機で書き出した実ファイルの形
 * （`schemaVersion` キーが無い）で、これが読めなくなる変更を検出するためのゴールデンデータ。
 * 手で書き換えないこと。
 */
class JsonBackupCodecTest {

    private val codec = JsonBackupCodec(FakeIdGenerator())

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

    // ----- v1 の読み込み（永久に残す） -----

    @Test
    fun `schemaVersion の無い旧ファイルは v1 として読める`() {
        val result = codec.decode(legacyJsonWithoutSchemaVersion)

        assertTrue("旧形式が読めなくなっている: $result", result is BackupCodec.DecodeResult.Success)
        val data = (result as BackupCodec.DecodeResult.Success).data
        assertEquals(1727000000000L, data.exportedAt)
        assertEquals(1, data.overrides.size)
        assertEquals(2.7, data.overrides[0].internalRatio ?: 0.0, 1e-9)
        // 省略された任意フィールドは null（= 上書きしない）
        assertEquals(null, data.overrides[0].defaultTireMm)
    }

    @Test
    fun `v1 の保存セッティングは車とシートに変換されて届く`() {
        val data = decoded(legacyJsonWithoutSchemaVersion)

        assertEquals(1, data.cars.size)
        assertEquals("tamiya_tt02", data.cars.single().chassisId)
        assertEquals(1, data.sheets.size)
        val sheet = data.sheets.single()
        assertEquals("Rd1", sheet.sheet.name)
        assertEquals(data.cars.single().id, sheet.sheet.carId)
        // 6 項目が bag に移る。internalRatioSnapshot は internalRatio になる
        assertEquals(22, sheet.values.intOf("pinion"))
        assertEquals(2.6, sheet.values.decimalOf("internalRatio")!!, 1e-9)
        assertEquals(6500, sheet.values.intOf("motorKv"))
    }

    /**
     * 版の省略は **常に v1**。CURRENT_SCHEMA_VERSION に連動させると、
     * v2 を出した瞬間に旧ファイルが v2 と誤認される。
     */
    @Test
    fun `省略時の版は CURRENT ではなく固定値 1 である`() {
        assertEquals(1, ExportDataDto.OMITTED_SCHEMA_VERSION)
        assertEquals(2, ExportDataDto.CURRENT_SCHEMA_VERSION)
    }

    // ----- v2 の書き出し -----

    @Test
    fun `書き出した JSON には schemaVersion が含まれる`() {
        val json = codec.encode(BackupData(exportedAt = 1L))

        // S-6 の再発防止（encodeDefaults = true が消えると落ちる）
        assertTrue("schemaVersion が出ていない:\n$json", json.contains("\"schemaVersion\": 2"))
        assertTrue("空の cars が省かれている:\n$json", json.contains("\"cars\""))
        assertTrue("空の sheets が省かれている:\n$json", json.contains("\"sheets\""))
        assertTrue("空の overrides が省かれている:\n$json", json.contains("\"overrides\""))
    }

    @Test
    fun `どのスキーマ版で書いたかを記録する`() {
        val json = codec.encode(BackupData(exportedAt = 1L))

        assertTrue(json.contains("\"schemaId\": \"touring\""))
    }

    @Test
    fun `v1 の保存セッティングは書き出さない`() {
        // M-3 でテーブルごと無くなったので、書き出す側がこれを埋めることはない。
        // 読む側は AGENTS.md §4 に従って永久に残す
        val json = codec.encode(BackupData(exportedAt = 1L))
        assertTrue(json.contains("\"setups\": []"))
    }

    @Test
    fun `シートの値は配列ではなくオブジェクトで書かれる`() {
        // EAV の行は内部表現であってワイヤ形式ではない。
        // 人が読めて git で差分が取れることを優先する
        val json = codec.encode(BackupData(exportedAt = 1L, cars = listOf(car()), sheets = listOf(sheet())))

        assertTrue(json, json.contains("\"values\": {"))
        assertTrue(json, json.contains("\"pinion\": 22"))
        assertTrue(json, json.contains("\"front.springRate\": \"soft\""))
    }

    // ----- 往復 -----

    @Test
    fun `エンコードとデコードは往復する`() {
        val original = BackupData(
            exportedAt = 1727000000000L,
            cars = listOf(car()),
            sheets = listOf(sheet()),
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

        assertEquals(original, decoded(codec.encode(original)))
    }

    @Test
    fun `2 回書き出した JSON は同一になる`() {
        // 値の並び順が安定していないと git で差分が読めない
        val data = BackupData(exportedAt = 1L, cars = listOf(car()), sheets = listOf(sheet()))

        assertEquals(codec.encode(data), codec.encode(data))
    }

    @Test
    fun `整数項目は 22 でも 22_0 でも整数として読み戻る`() {
        // 型を決めるのは JSON のリテラルではなくレジストリ
        val json = v2Json("""{"pinion": 22.0, "spur": 84}""")

        val values = decoded(json).sheets.single().values
        assertEquals(SetupValue.IntV(22), values["pinion"])
        assertEquals(SetupValue.IntV(84), values["spur"])
    }

    @Test
    fun `レジストリに無いキーも保全される`() {
        // スキーマを安全に進化させられることの担保（計画 §7.2）
        val json = v2Json("""{"pinion": 22, "future.newField": "将来の値", "future.num": 1.5}""")

        val values = decoded(json).sheets.single().values
        assertEquals("将来の値", values.textOf("future.newField"))
        assertEquals(1.5, values.decimalOf("future.num")!!, 1e-9)
    }

    @Test
    fun `未知キーは書き出しでも落ちない`() {
        val withUnknown = sheet().let {
            it.copy(values = it.values.with("future.newField", SetupValue.TextV("将来の値")))
        }
        val data = BackupData(exportedAt = 1L, cars = listOf(car()), sheets = listOf(withUnknown))

        assertEquals(data, decoded(codec.encode(data)))
    }

    @Test
    fun `null の値は空欄として読み飛ばす`() {
        val json = v2Json("""{"pinion": 22, "spur": null}""")

        val values = decoded(json).sheets.single().values
        assertEquals(1, values.size)
        assertEquals(22, values.intOf("pinion"))
    }

    // ----- エラー -----

    @Test
    fun `未知のバージョンは UnsupportedVersion`() {
        val future = """{"schemaVersion":99,"exportedAt":1,"cars":[],"sheets":[]}"""

        assertEquals(BackupCodec.DecodeResult.UnsupportedVersion, codec.decode(future))
    }

    @Test
    fun `壊れた JSON は InvalidFormat`() {
        assertEquals(BackupCodec.DecodeResult.InvalidFormat, codec.decode("これはJSONではない"))
        assertEquals(BackupCodec.DecodeResult.InvalidFormat, codec.decode("""{"cars":[]}"""))
    }

    @Test
    fun `知らないキーがあっても読める`() {
        val json = """
            {"schemaVersion":2,"exportedAt":1,"cars":[],"sheets":[],"overrides":[],"futureField":"x"}
        """.trimIndent()

        assertTrue(codec.decode(json) is BackupCodec.DecodeResult.Success)
    }

    // ----- ヘルパー -----

    private fun decoded(json: String): BackupData =
        (codec.decode(json) as BackupCodec.DecodeResult.Success).data

    private fun car() = Car(
        id = "car-uuid",
        name = "TT-02 #1",
        chassisId = "tamiya_tt02",
        note = "メモ",
        isArchived = false,
        createdAt = 1L,
        updatedAt = 2L
    )

    private fun sheet() = SetupSheetWithValues(
        sheet = SetupSheet(
            id = "sheet-uuid",
            carId = "car-uuid",
            name = "Rd1",
            baselineId = null,
            conditions = SessionConditions(
                sessionDate = 1726000000000L,
                trackName = "サーキット",
                airTempC = 21.5
            ),
            note = null,
            isFavorite = true,
            createdAt = 1L,
            updatedAt = 2L
        ),
        values = SetupValues.of(
            "pinion" to SetupValue.IntV(22),
            "front.camberDeg" to SetupValue.DecimalV(-1.5),
            "front.springRate" to SetupValue.ChoiceV("soft"),
            "front.damperOilBrand" to SetupValue.TextV("タミヤ")
        )
    )

    private fun v2Json(values: String): String = """
        {
          "schemaVersion": 2,
          "exportedAt": 1,
          "cars": [],
          "sheets": [
            {
              "id": "sheet-uuid", "carId": "car-uuid", "name": "Rd1",
              "schemaId": "touring", "values": $values,
              "createdAt": 1, "updatedAt": 2
            }
          ],
          "overrides": []
        }
    """.trimIndent()
}
