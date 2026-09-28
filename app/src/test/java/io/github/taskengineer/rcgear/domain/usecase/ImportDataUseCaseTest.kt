package io.github.taskengineer.rcgear.domain.usecase

import io.github.taskengineer.rcgear.data.local.file.JsonBackupCodec
import io.github.taskengineer.rcgear.fake.FakeCarRepository
import io.github.taskengineer.rcgear.fake.FakeChassisRepository
import io.github.taskengineer.rcgear.fake.FakeIdGenerator
import io.github.taskengineer.rcgear.fake.FakeSetupSheetRepository
import io.github.taskengineer.rcgear.fake.FakeTransactionRunner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [ImportDataUseCase] のテスト（REF-1 / BUG-2 / BUG-3、M-6 で v2 化）。
 *
 * 守りたいこと:
 *  - 範囲外の値を Room に入れない（後から画面を開いた時に落ちるのを防ぐ）
 *  - 取り込みが一括（＝トランザクション）で行われる
 *  - 既存データを壊さない（不明シャーシ / 親のいないシートのスキップ）
 *  - **v1 ファイルが読め続ける**（AGENTS.md §4）
 *
 * [JsonBackupCodec] は本物を使う。デコードを差し替えると
 * 「手書き JSON が実際にどう解釈されるか」という主眼が消えるため、
 * ここは意図的にワイヤ形式まで通す。
 *
 * メソッド名のプレフィクスでカテゴリを表現 (v2_, v1_, invalid_, version_, batch_, atomic_)。
 */
class ImportDataUseCaseTest {

    private lateinit var sheets: FakeSetupSheetRepository
    private lateinit var cars: FakeCarRepository
    private lateinit var chassis: FakeChassisRepository
    private lateinit var transactions: FakeTransactionRunner
    private lateinit var useCase: ImportDataUseCase

    @Before
    fun setUp() {
        sheets = FakeSetupSheetRepository()
        cars = FakeCarRepository(sheetRepository = sheets)
        chassis = FakeChassisRepository()
        transactions = FakeTransactionRunner(cars, sheets, chassis)
        useCase = ImportDataUseCase(
            cars,
            sheets,
            chassis,
            JsonBackupCodec(FakeIdGenerator()),
            transactions
        )
    }

    // ----- v2_ -----

    @Test
    fun `v2_車とシートと上書きを取り込む`() = runTest {
        val result = useCase(
            v2Json(
                cars = listOf(carJson()),
                sheets = listOf(sheetJson()),
                overrides = listOf(overrideJson())
            )
        )

        result.assertSuccess(importedCars = 1, importedSheets = 1, importedOverrides = 1)
        assertEquals(listOf("TT-02 #1"), cars.getAllOnce().map { it.name })
        assertEquals(22, sheets.getSheet("sheet-1")!!.values.intOf("pinion"))
    }

    @Test
    fun `v2_createdAt と updatedAt は元データのまま保持される`() = runTest {
        useCase(v2Json(cars = listOf(carJson(createdAt = 111L, updatedAt = 222L))))

        val car = cars.getAllOnce().single()
        assertEquals(111L, car.createdAt)
        assertEquals(222L, car.updatedAt)
    }

    @Test
    fun `v2_標準DBに無いシャーシの車はスキップされる`() = runTest {
        val result = useCase(
            v2Json(cars = listOf(carJson(id = "car-1"), carJson(id = "car-2", chassisId = "未知")))
        )

        result.assertSuccess(importedCars = 1, skippedCars = 1)
        assertEquals(listOf("car-1"), cars.getAllOnce().map { it.id })
    }

    @Test
    fun `v2_ユーザー定義シャーシの車は通す`() = runTest {
        // 受け皿（F-5）はまだ無いが、ここで弾くと将来「車だけ消えたバックアップ」になる
        val result = useCase(v2Json(cars = listOf(carJson(chassisId = "user_abc"))))

        result.assertSuccess(importedCars = 1)
    }

    @Test
    fun `v2_親の車がいないシートはスキップされる`() = runTest {
        // 外部キーで弾かれるので、先に落として件数で報告する
        val result = useCase(v2Json(sheets = listOf(sheetJson(carId = "居ない車"))))

        result.assertSuccess(skippedSheets = 1)
        assertTrue(sheets.getAllOnce().isEmpty())
    }

    @Test
    fun `v2_取り込まれないベースライン参照は外される`() = runTest {
        val result = useCase(
            v2Json(
                cars = listOf(carJson()),
                sheets = listOf(sheetJson(baselineId = "居ないシート"))
            )
        )

        result.assertSuccess(importedCars = 1, importedSheets = 1)
        assertNull(sheets.getSheet("sheet-1")!!.sheet.baselineId)
    }

    // ----- invalid_: BUG-2 -----

    @Test
    fun `invalid_範囲外の項目だけを落としシートは取り込む`() = runTest {
        // 上書きと違いシートは数十項目の集まり。1 項目の範囲外で 1 セッション分を
        // 丸ごと捨てるのは損が大きいので、項目単位で落として件数で報告する
        val result = useCase(
            v2Json(
                cars = listOf(carJson()),
                sheets = listOf(sheetJson(values = """{"pinion": 22, "tireMm": 200}"""))
            )
        )

        result.assertSuccess(importedCars = 1, importedSheets = 1, droppedValues = 1)
        val values = sheets.getSheet("sheet-1")!!.values
        assertEquals(22, values.intOf("pinion"))
        assertTrue("範囲外の値が入っている", "tireMm" !in values)
    }

    @Test
    fun `invalid_未知キーは検証対象外でそのまま残る`() = runTest {
        useCase(
            v2Json(
                cars = listOf(carJson()),
                sheets = listOf(sheetJson(values = """{"future.newField": 99999}"""))
            )
        )

        assertEquals(99999.0, sheets.getSheet("sheet-1")!!.values.decimalOf("future.newField")!!, 1e-9)
    }

    @Test
    fun `invalid_内部減速比が 0 以下の上書きは棄却される`() = runTest {
        val result = useCase(v2Json(overrides = listOf(overrideJson(internalRatio = 0.0))))

        result.assertSuccess(invalidOverrides = 1)
        assertTrue(chassis.storedOverrides.isEmpty())
    }

    @Test
    fun `invalid_範囲外のタイヤ径を持つ上書きは棄却される`() = runTest {
        // BUG-1 と同じ値が JSON 経由で入ってくるケース
        val result = useCase(v2Json(overrides = listOf(overrideJson(defaultTireMm = 200))))

        result.assertSuccess(invalidOverrides = 1)
    }

    @Test
    fun `invalid_標準DBに無いシャーシの上書きはスキップされる`() = runTest {
        val result = useCase(
            v2Json(
                overrides = listOf(
                    overrideJson(chassisId = "tamiya_tt02"),
                    overrideJson(chassisId = "unknown_chassis")
                )
            )
        )

        result.assertSuccess(importedOverrides = 1, skippedOverrides = 1)
    }

    @Test
    fun `invalid_ファイル内で同じシャーシの上書きが重複したら後勝ちで1件になる`() = runTest {
        val result = useCase(
            v2Json(overrides = listOf(overrideJson(internalRatio = 2.7), overrideJson(internalRatio = 2.8)))
        )

        result.assertSuccess(importedOverrides = 1)
        assertEquals(2.8, chassis.storedOverrides.single().internalRatio!!, 1e-9)
    }

    // ----- v1_: 旧バージョンの読み込みは永久に残す -----

    @Test
    fun `v1_保存セッティングが車とシートになる`() = runTest {
        val result = useCase(v1Json(listOf(setupJson(name = "Rd1"))))

        result.assertSuccess(importedCars = 1, importedSheets = 1)
        assertEquals("tamiya_tt02", cars.getAllOnce().single().chassisId)
        assertEquals("Rd1", sheets.getAllOnce().single().sheet.name)
    }

    @Test
    fun `v1_同じシャーシのセッティングは 1 台の車にまとまる`() = runTest {
        // 1 セッティング = 1 台にすると、同じ車の履歴がバラバラの車として並び
        // ベースライン差分（軸 B）が使えなくなる
        val result = useCase(
            v1Json(
                listOf(
                    setupJson(name = "Rd1", chassisId = "tamiya_tt02"),
                    setupJson(name = "Rd2", chassisId = "tamiya_tt02"),
                    setupJson(name = "練習", chassisId = "tamiya_ta08")
                )
            )
        )

        result.assertSuccess(importedCars = 2, importedSheets = 3)
        val tt02 = cars.getAllOnce().single { it.chassisId == "tamiya_tt02" }
        assertEquals(2, sheets.getAllOnce().count { it.sheet.carId == tt02.id })
    }

    @Test
    fun `v1_範囲外の値は項目ごと落ちるがシートは残る`() = runTest {
        // BUG-2 の再現手順（pinion を 5 に書き換えた JSON）
        val result = useCase(v1Json(listOf(setupJson(name = "壊れた行", pinion = 5))))

        result.assertSuccess(importedCars = 1, importedSheets = 1, droppedValues = 1)
        val values = sheets.getAllOnce().single().values
        assertTrue("範囲外の値が入っている", "pinion" !in values)
        assertEquals(84, values.intOf("spur"))
    }

    @Test
    fun `v1_上書きも同時に取り込まれる`() = runTest {
        val result = useCase(v1Json(listOf(setupJson()), overrides = listOf(overrideJson())))

        result.assertSuccess(importedCars = 1, importedSheets = 1, importedOverrides = 1)
    }

    // ----- version_ / format_ -----

    @Test
    fun `version_未来の schemaVersion は UnsupportedVersion を返す`() = runTest {
        val result = useCase("""{"schemaVersion":99,"exportedAt":0,"cars":[],"sheets":[]}""")

        assertEquals(ImportDataUseCase.Result.UnsupportedVersion, result)
        assertTrue("拒否したのに書き込んでいる", cars.getAllOnce().isEmpty())
    }

    @Test
    fun `version_壊れた JSON は InvalidFormat を返す`() = runTest {
        assertEquals(ImportDataUseCase.Result.InvalidFormat, useCase("これはJSONではない"))
        assertEquals(ImportDataUseCase.Result.InvalidFormat, useCase("""{"schemaVersion":2}"""))
    }

    // ----- batch_: BUG-3 -----

    @Test
    fun `batch_取り込みは一括メソッドで1回だけ呼ばれる`() = runTest {
        // 1 件ずつ insert すると途中失敗で半端に取り込まれる（BUG-3）。
        // Room はコレクションを受ける @Insert を 1 トランザクションで実行するため、
        // 一括メソッドに 1 回で渡していることを保証する。
        useCase(
            v2Json(
                cars = listOf(carJson(id = "car-1"), carJson(id = "car-2")),
                sheets = listOf(sheetJson(id = "sheet-1"), sheetJson(id = "sheet-2"))
            )
        )

        assertEquals(1, cars.restoreAllCallCount)
        assertEquals(1, sheets.restoreAllCallCount)
        assertEquals(2, cars.getAllOnce().size)
        assertEquals(2, sheets.getAllOnce().size)
    }

    // ----- atomic_: M-8（ファイル全体で 1 トランザクション） -----

    @Test
    fun `atomic_途中で失敗したら 1 行も入らない`() = runTest {
        // 車・シート・上書きは別々の Repository なので、囲まないと
        // 「車とシートは入ったが上書きで失敗」という半端な状態が残る
        chassis.failOnRestoreOverrides = true

        var thrown: Throwable? = null
        try {
            useCase(
                v2Json(
                    cars = listOf(carJson()),
                    sheets = listOf(sheetJson()),
                    overrides = listOf(overrideJson())
                )
            )
        } catch (e: IllegalStateException) {
            thrown = e
        }

        assertTrue("書き込みの失敗が握りつぶされている", thrown != null)
        assertEquals(1, transactions.rollbackCount)
        assertTrue("車が半端に入っている", cars.getAllOnce().isEmpty())
        assertTrue("シートが半端に入っている", sheets.getAllOnce().isEmpty())
    }

    @Test
    fun `atomic_失敗しても既存データは残る`() = runTest {
        val existing = cars.createCar("既存の車", "tamiya_tt02")
        chassis.failOnRestoreOverrides = true

        try {
            useCase(v2Json(cars = listOf(carJson()), overrides = listOf(overrideJson())))
        } catch (e: IllegalStateException) {
            // 期待どおり
        }

        assertEquals(listOf(existing), cars.getAllOnce().map { it.id })
    }

    @Test
    fun `atomic_成功時はロールバックしない`() = runTest {
        useCase(v2Json(cars = listOf(carJson())))

        assertEquals(0, transactions.rollbackCount)
        assertEquals(1, cars.getAllOnce().size)
    }

    // ----- userChassis_（F-5） -----

    @Test
    fun `userChassis_自作シャーシが取り込まれる`() = runTest {
        val result = useCase(
            v2Json(userChassis = listOf(userChassisJson()))
        )

        result.assertSuccess(importedUserChassis = 1)
        assertEquals("X4", chassis.getAllUserChassisOnce().single().name)
    }

    @Test
    fun `userChassis_自作シャーシを使う車も一緒に取り込まれる`() = runTest {
        // 定義より先に車を入れると外部キーは無くても「不明なシャーシ」になる。
        // UseCase が自作シャーシを先に入れているかを見る
        val result = useCase(
            v2Json(
                userChassis = listOf(userChassisJson()),
                cars = listOf(carJson(chassisId = "user_x4"))
            )
        )

        result.assertSuccess(importedUserChassis = 1, importedCars = 1)
        assertEquals("user_x4", cars.getAllOnce().single().chassisId)
    }

    @Test
    fun `userChassis_定義が無くてもそれを指す車は取り込む`() = runTest {
        // 定義だけ落ちたファイルで車ごと消えるより、「不明なシャーシ」で残るほうが復旧できる
        val result = useCase(v2Json(cars = listOf(carJson(chassisId = "user_missing"))))

        result.assertSuccess(importedCars = 1)
    }

    @Test
    fun `userChassis_id が規約に反するものは棄却する`() = runTest {
        // user_ 接頭辞は同梱 DB と衝突させないための規約。破られると
        // 同梱エントリを名前で乗っ取る id が作れてしまう
        val result = useCase(
            v2Json(userChassis = listOf(userChassisJson(id = "tamiya_tt02")))
        )

        result.assertSuccess(skippedUserChassis = 1)
        assertTrue(chassis.getAllUserChassisOnce().isEmpty())
    }

    @Test
    fun `userChassis_範囲外の値を持つものは棄却する`() = runTest {
        val result = useCase(
            v2Json(userChassis = listOf(userChassisJson(internalRatio = 0.0)))
        )

        result.assertSuccess(skippedUserChassis = 1)
    }

    @Test
    fun `userChassis_同じファイルを 2 回読んでも増えない`() = runTest {
        val json = v2Json(userChassis = listOf(userChassisJson()))

        useCase(json)
        useCase(json)

        assertEquals(1, chassis.getAllUserChassisOnce().size)
    }

    // ----- ヘルパー -----

    private fun ImportDataUseCase.Result.assertSuccess(
        importedCars: Int = 0,
        skippedCars: Int = 0,
        importedSheets: Int = 0,
        skippedSheets: Int = 0,
        droppedValues: Int = 0,
        importedOverrides: Int = 0,
        skippedOverrides: Int = 0,
        invalidOverrides: Int = 0,
        importedUserChassis: Int = 0,
        skippedUserChassis: Int = 0
    ) {
        val actual = this as? ImportDataUseCase.Result.Success
            ?: throw AssertionError("Success を期待したが $this だった")
        assertEquals(
            ImportDataUseCase.Result.Success(
                importedCars = importedCars,
                skippedCars = skippedCars,
                importedSheets = importedSheets,
                skippedSheets = skippedSheets,
                droppedValues = droppedValues,
                importedOverrides = importedOverrides,
                skippedOverrides = skippedOverrides,
                invalidOverrides = invalidOverrides,
                importedUserChassis = importedUserChassis,
                skippedUserChassis = skippedUserChassis
            ),
            actual
        )
    }

    private fun v2Json(
        cars: List<String> = emptyList(),
        sheets: List<String> = emptyList(),
        overrides: List<String> = emptyList(),
        userChassis: List<String> = emptyList()
    ): String = """
        {
          "schemaVersion": 2,
          "exportedAt": 1750000000000,
          "cars": [${cars.joinToString(",")}],
          "sheets": [${sheets.joinToString(",")}],
          "overrides": [${overrides.joinToString(",")}],
          "userChassis": [${userChassis.joinToString(",")}]
        }
    """.trimIndent()

    private fun userChassisJson(
        id: String = "user_x4",
        name: String = "X4",
        internalRatio: Double = 1.9
    ): String = """
        {
          "id": "$id", "makerName": "XRAY", "name": "$name",
          "internalRatio": $internalRatio, "defaultTireMm": 62,
          "category": "TOURING", "drive": "BELT_4WD", "hasCenterDiff": false,
          "note": null, "createdAt": 1, "updatedAt": 2
        }
    """.trimIndent()

    private fun v1Json(
        setups: List<String>,
        overrides: List<String> = emptyList()
    ): String = """
        {
          "schemaVersion": 1,
          "exportedAt": 1750000000000,
          "setups": [${setups.joinToString(",")}],
          "overrides": [${overrides.joinToString(",")}]
        }
    """.trimIndent()

    private fun carJson(
        id: String = "car-1",
        name: String = "TT-02 #1",
        chassisId: String = "tamiya_tt02",
        createdAt: Long = 1L,
        updatedAt: Long = 2L
    ): String = """
        {
          "id": "$id", "name": "$name", "chassisId": "$chassisId",
          "createdAt": $createdAt, "updatedAt": $updatedAt
        }
    """.trimIndent()

    private fun sheetJson(
        id: String = "sheet-1",
        carId: String = "car-1",
        name: String = "Rd1",
        baselineId: String? = null,
        values: String = """{"pinion": 22}"""
    ): String = """
        {
          "id": "$id", "carId": "$carId", "name": "$name",
          "baselineId": ${baselineId?.let { "\"$it\"" } ?: "null"},
          "schemaId": "touring", "values": $values,
          "createdAt": 1, "updatedAt": 2
        }
    """.trimIndent()

    private fun setupJson(
        name: String = "Rd1",
        chassisId: String = "tamiya_tt02",
        pinion: Int = 22,
        spur: Int = 84
    ): String = """
        {
          "name": "$name", "chassisId": "$chassisId",
          "pinion": $pinion, "spur": $spur, "internalRatioSnapshot": 2.6,
          "kv": 6500, "cells": 2, "tireMm": 63,
          "createdAt": 1, "updatedAt": 2
        }
    """.trimIndent()

    private fun overrideJson(
        chassisId: String = "tamiya_tt02",
        internalRatio: Double? = 2.7,
        defaultTireMm: Int? = 64
    ): String = """
        {
          "chassisId": "$chassisId",
          "internalRatio": ${internalRatio ?: "null"},
          "defaultTireMm": ${defaultTireMm ?: "null"},
          "note": null,
          "updatedAt": 3
        }
    """.trimIndent()
}
