package io.github.taskengineer.rcgear.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `num` / `text` の 2 列と [SetupValue] の相互変換（M-4）。
 *
 * どちらの型として読むかは値ではなく**レジストリの型定義**が決める、という
 * 判断がここ 1 箇所に閉じていることを確かめる。
 */
class SetupValueCodecTest {

    @Test
    fun `整数項目は IntV として読み戻る`() {
        val stored = SetupValueCodec.encode(SetupValue.IntV(22))
        assertEquals(22.0, stored.num!!, 0.0)
        assertNull(stored.text)
        assertEquals(SetupValue.IntV(22), SetupValueCodec.decode("pinion", stored.num, stored.text))
    }

    @Test
    fun `小数項目は DecimalV として読み戻る`() {
        val stored = SetupValueCodec.encode(SetupValue.DecimalV(-1.5))
        assertEquals(
            SetupValue.DecimalV(-1.5),
            SetupValueCodec.decode("front.camberDeg", stored.num, stored.text)
        )
    }

    @Test
    fun `整数項目に小数が入っていても整数に戻る`() {
        // 型を決めるのは保存された値ではなくレジストリ
        assertEquals(SetupValue.IntV(22), SetupValueCodec.decode("pinion", 22.0, null))
    }

    @Test
    fun `選択肢はキー文字列で往復する`() {
        val stored = SetupValueCodec.encode(SetupValue.ChoiceV("soft"))
        assertEquals("soft", stored.text)
        assertNull(stored.num)
        assertEquals(
            SetupValue.ChoiceV("soft"),
            SetupValueCodec.decode("front.springRate", stored.num, stored.text)
        )
    }

    @Test
    fun `自由入力は TextV として往復する`() {
        val stored = SetupValueCodec.encode(SetupValue.TextV("タミヤ"))
        assertEquals(
            SetupValue.TextV("タミヤ"),
            SetupValueCodec.decode("front.damperOilBrand", stored.num, stored.text)
        )
    }

    @Test
    fun `真偽値は 0 と 1 で保存される`() {
        assertEquals(1.0, SetupValueCodec.encode(SetupValue.BoolV(true)).num!!, 0.0)
        assertEquals(0.0, SetupValueCodec.encode(SetupValue.BoolV(false)).num!!, 0.0)
    }

    @Test
    fun `未知キーは入っている列から素直に読む`() {
        assertEquals(
            SetupValue.DecimalV(1.0),
            SetupValueCodec.decode("future.newField", 1.0, null)
        )
        assertEquals(
            SetupValue.TextV("x"),
            SetupValueCodec.decode("future.newField", null, "x")
        )
    }

    @Test
    fun `両方 null の行は値にならない`() {
        assertNull(SetupValueCodec.decode("pinion", null, null))
        assertNull(SetupValueCodec.decode("future.newField", null, null))
    }

    @Test
    fun `型と列の食い違いは読み捨てる`() {
        // 数値項目なのに text しか無い、という壊れた行。例外にせず「値なし」にする
        assertNull(SetupValueCodec.decode("pinion", null, "にじゅうに"))
        assertNull(SetupValueCodec.decode("front.springRate", 1.0, null))
    }
}
