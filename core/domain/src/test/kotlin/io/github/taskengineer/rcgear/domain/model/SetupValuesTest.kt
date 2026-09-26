package io.github.taskengineer.rcgear.domain.model

import io.github.taskengineer.rcgear.domain.schema.TouringSetupSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 値の束（M-2）。読み出しの型安全性と、`toGearInput()` という
 * bag ↔ 計算機の唯一の接続点を見る。
 */
class SetupValuesTest {

    private fun gearValues(
        pinion: Int = 22,
        spur: Int = 84,
        internalRatio: Double = 2.6,
        kv: Int = 6500,
        cells: Int = 2,
        tireMm: Int = 63
    ) = SetupValues.of(
        "pinion" to SetupValue.IntV(pinion),
        "spur" to SetupValue.IntV(spur),
        "internalRatio" to SetupValue.DecimalV(internalRatio),
        "motorKv" to SetupValue.IntV(kv),
        "cells" to SetupValue.IntV(cells),
        "tireMm" to SetupValue.IntV(tireMm)
    )

    // ----- 読み出し -----

    @Test
    fun `型が合わない読み出しは null を返す`() {
        val values = SetupValues.of("pinion" to SetupValue.TextV("22"))
        assertNull(values.intOf("pinion"))
        assertNull(values.decimalOf("pinion"))
        assertEquals("22", values.textOf("pinion"))
    }

    @Test
    fun `decimalOf は整数値も読める`() {
        // step 1.0 の項目を後から小数化したときに、過去のデータが読めなくなるのを避ける
        val values = SetupValues.of("front.droopMm" to SetupValue.IntV(4))
        assertEquals(4.0, values.decimalOf("front.droopMm")!!, 0.0)
        // 逆向きは許さない。小数を黙って切り捨てると記録した値が変わってしまう
        val decimal = SetupValues.of("front.droopMm" to SetupValue.DecimalV(4.5))
        assertNull(decimal.intOf("front.droopMm"))
    }

    @Test
    fun `無い項目は null`() {
        assertNull(SetupValues.EMPTY.intOf("pinion"))
        assertFalse("pinion" in SetupValues.EMPTY)
        assertTrue(SetupValues.EMPTY.isEmpty())
    }

    // ----- 更新 -----

    @Test
    fun `with は元の束を壊さず 1 項目だけ差し替える`() {
        val before = gearValues()
        val after = before.with("pinion", SetupValue.IntV(30))
        assertEquals(22, before.intOf("pinion"))
        assertEquals(30, after.intOf("pinion"))
        assertEquals(before.size, after.size)
    }

    @Test
    fun `without はキーごと消す`() {
        val after = gearValues().without("pinion")
        assertFalse("pinion" in after)
        assertNull(after.intOf("pinion"))
    }

    @Test
    fun `plus は右側で上書きする`() {
        val merged = gearValues() + SetupValues.of("pinion" to SetupValue.IntV(35))
        assertEquals(35, merged.intOf("pinion"))
        assertEquals(84, merged.intOf("spur"))
    }

    @Test
    fun `未知のキーも保持される`() {
        val values = gearValues().with("future.newField", SetupValue.IntV(1))
        assertEquals(setOf("future.newField"), values.unknownKeys())
        assertEquals(1, values.intOf("future.newField"))
    }

    // ----- 初期値 -----

    @Test
    fun `初期値はシャーシ DB の値を焼き込む`() {
        val values = TouringSetupSchema.initialValues(
            ChassisDefaults(internalRatio = 2.6, defaultTireMm = 63)
        )
        assertEquals(2.6, values.decimalOf("internalRatio")!!, 1e-9)
        assertEquals(63, values.intOf("tireMm"))
        // defaultValue を持つ項目はそちらが入る
        assertEquals(22, values.intOf("pinion"))
        assertEquals(84, values.intOf("spur"))
    }

    @Test
    fun `既定値を持たない項目は空欄で始まる`() {
        val values = TouringSetupSchema.initialValues(ChassisDefaults.NONE)
        // 0 で埋めない。「空欄」と「0 が入っている」は意味が違う
        assertFalse("front.camberDeg" in values)
        assertFalse("internalRatio" in values)
        assertFalse("tireMm" in values)
    }

    @Test
    fun `初期値はレジストリに無いキーを作らない`() {
        val values = TouringSetupSchema.initialValues(ChassisDefaults(2.6, 63))
        assertEquals(emptySet<String>(), values.unknownKeys())
    }

    // ----- toGearInput -----

    @Test
    fun `toGearInput はギアセクションの値を計算入力に変換する`() {
        val input = gearValues().toGearInput()!!
        assertEquals(22, input.pinion)
        assertEquals(84, input.spur)
        assertEquals(2.6, input.internalRatio, 1e-9)
        assertEquals(6500, input.kv)
        assertEquals(2, input.cells)
        assertEquals(63, input.tireMm)
    }

    @Test
    fun `toGearInput は 1 項目でも欠けていたら null`() {
        for (key in listOf("pinion", "spur", "internalRatio", "motorKv", "cells", "tireMm")) {
            assertNull("$key が無いのに変換できてしまう", gearValues().without(key).toGearInput())
        }
    }

    @Test
    fun `toGearInput はゼロ除算になる値を null にする`() {
        assertNull(gearValues(pinion = 0).toGearInput())
        assertNull(gearValues(spur = 0).toGearInput())
        assertNull(gearValues(internalRatio = 0.0).toGearInput())
        assertNull(gearValues(pinion = -1).toGearInput())
    }

    @Test
    fun `toGearInput は範囲外でも計算が成立するなら通す`() {
        // 範囲の判断は FieldValidator の仕事。ここで二重に弾くと
        // 「シートには入っているのに計算だけ黙って出ない」状態になる
        val input = gearValues(pinion = 50, tireMm = 200).toGearInput()
        assertEquals(50, input?.pinion)
        assertEquals(200, input?.tireMm)
    }

    @Test
    fun `toGearInput は整数として保存された内部減速比も読む`() {
        val values = gearValues().with("internalRatio", SetupValue.IntV(2))
        assertEquals(2.0, values.toGearInput()!!.internalRatio, 0.0)
    }
}
