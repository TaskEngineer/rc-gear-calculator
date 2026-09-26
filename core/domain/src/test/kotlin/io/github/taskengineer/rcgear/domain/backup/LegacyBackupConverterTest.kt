package io.github.taskengineer.rcgear.domain.backup

import io.github.taskengineer.rcgear.domain.common.IdGenerator
import io.github.taskengineer.rcgear.domain.model.SetupValue
import io.github.taskengineer.rcgear.domain.model.toGearInput
import io.github.taskengineer.rcgear.domain.schema.TouringSetupSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1 → v2 の変換（M-6）。この経路は AGENTS.md §4 に従って永久に残す。
 *
 * 純粋関数なので、採番だけ連番の [IdGenerator] に差し替えれば結果を固定できる。
 */
class LegacyBackupConverterTest {

    private fun ids() = object : IdGenerator {
        private var next = 1
        override fun newId(): String = "id-${next++}"
    }

    @Test
    fun `空の入力は空を返す`() {
        val converted = LegacyBackupConverter.toV2(emptyList(), ids())

        assertTrue(converted.cars.isEmpty())
        assertTrue(converted.sheets.isEmpty())
    }

    @Test
    fun `1 セッティングは 1 台 + 1 シートになる`() {
        val converted = LegacyBackupConverter.toV2(listOf(setup(name = "Rd1")), ids())

        assertEquals(1, converted.cars.size)
        assertEquals(1, converted.sheets.size)
        assertEquals(converted.cars.single().id, converted.sheets.single().sheet.carId)
        assertEquals("Rd1", converted.sheets.single().sheet.name)
    }

    @Test
    fun `同じシャーシのセッティングは 1 台にまとまる`() {
        // 1 セッティング = 1 台にすると、同じ車の履歴がバラバラの車として並び、
        // ベースライン差分（軸 B）が使えなくなる
        val converted = LegacyBackupConverter.toV2(
            listOf(
                setup(name = "Rd1", chassisId = "tamiya_tt02"),
                setup(name = "Rd2", chassisId = "tamiya_tt02"),
                setup(name = "練習", chassisId = "tamiya_ta08")
            ),
            ids()
        )

        assertEquals(2, converted.cars.size)
        assertEquals(3, converted.sheets.size)
        val tt02 = converted.cars.single { it.chassisId == "tamiya_tt02" }
        assertEquals(2, converted.sheets.count { it.sheet.carId == tt02.id })
    }

    @Test
    fun `車の名前はシャーシ id をそのまま使う`() {
        // v1 はシャーシの表示名を持っておらず、シャーシ DB は :core:domain から見えない。
        // 取り込んだあと改名すればよい種類の情報なので推測しない
        val converted = LegacyBackupConverter.toV2(listOf(setup(chassisId = "tamiya_tt02")), ids())

        assertEquals("tamiya_tt02", converted.cars.single().name)
    }

    @Test
    fun `車の更新日時は一番新しいシートに合わせる`() {
        val converted = LegacyBackupConverter.toV2(
            listOf(
                setup(name = "古い", createdAt = 100L, updatedAt = 100L),
                setup(name = "新しい", createdAt = 200L, updatedAt = 300L)
            ),
            ids()
        )

        val car = converted.cars.single()
        assertEquals(100L, car.createdAt)
        assertEquals(300L, car.updatedAt)
    }

    @Test
    fun `シートの作成日時は元データのまま`() {
        val converted = LegacyBackupConverter.toV2(
            listOf(setup(createdAt = 111L, updatedAt = 222L)),
            ids()
        )

        val sheet = converted.sheets.single().sheet
        assertEquals(111L, sheet.createdAt)
        assertEquals(222L, sheet.updatedAt)
        assertEquals(TouringSetupSchema.SCHEMA_ID, sheet.schemaId)
        assertNull("v1 にベースラインの概念は無い", sheet.baselineId)
    }

    @Test
    fun `6 項目が bag に移り 計算入力に戻せる`() {
        val converted = LegacyBackupConverter.toV2(listOf(setup()), ids())

        val values = converted.sheets.single().values
        assertEquals(22, values.intOf("pinion"))
        assertEquals(84, values.intOf("spur"))
        // internalRatioSnapshot は internalRatio になる。v2 では bag が常に絶対値なので
        // 「保存時の値」という特別扱いそのものが消える
        assertEquals(2.6, values.decimalOf("internalRatio")!!, 1e-9)
        assertEquals(6500, values.intOf("motorKv"))
        assertEquals(2, values.intOf("cells"))
        assertEquals(63, values.intOf("tireMm"))

        assertNotNull("ギア計算に戻せない", values.toGearInput())
    }

    @Test
    fun `範囲外の値もそのまま入る`() {
        // 棄却するかどうかは取り込みポリシー（UseCase）の判断で、変換の責務ではない
        val converted = LegacyBackupConverter.toV2(listOf(setup(pinion = 5)), ids())

        assertEquals(SetupValue.IntV(5), converted.sheets.single().values["pinion"])
    }

    @Test
    fun `車とシートの id は重複しない`() {
        val converted = LegacyBackupConverter.toV2(
            listOf(setup(name = "Rd1"), setup(name = "Rd2")),
            ids()
        )

        val allIds = converted.cars.map { it.id } + converted.sheets.map { it.id }
        assertEquals(allIds.size, allIds.toSet().size)
    }

    private fun setup(
        name: String = "Rd1",
        chassisId: String = "tamiya_tt02",
        pinion: Int = 22,
        createdAt: Long = 1L,
        updatedAt: Long = 2L
    ) = LegacySavedSetup(
        id = 0,
        name = name,
        chassisId = chassisId,
        pinion = pinion,
        spur = 84,
        internalRatioSnapshot = 2.6,
        kv = 6500,
        cells = 2,
        tireMm = 63,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
