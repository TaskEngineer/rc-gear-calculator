package io.github.taskengineer.rcgear.domain.validation

import io.github.taskengineer.rcgear.domain.model.ChassisDefaults
import io.github.taskengineer.rcgear.domain.model.SetupValue
import io.github.taskengineer.rcgear.domain.model.SetupValues
import io.github.taskengineer.rcgear.domain.model.initialValues
import io.github.taskengineer.rcgear.domain.schema.NumberFieldDef
import io.github.taskengineer.rcgear.domain.schema.TouringSetupSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * レジストリ駆動の検証（M-2）。
 *
 * 旧 `GearCalculationInput.init` の `require` が持っていた役目を引き継ぐ。
 * 大事なのは **例外を投げないこと** — BUG-1 / BUG-2 はどちらも
 * 「範囲外の値が例外になって画面ごと落ちる」形で出ていた。
 */
class FieldValidatorTest {

    private fun field(key: String) = TouringSetupSchema.byKey.getValue(key)
    private fun numberField(key: String) = field(key) as NumberFieldDef

    // ----- 範囲 -----

    @Test
    fun `範囲内の値は違反なし`() {
        assertNull(FieldValidator.validate(field("pinion"), SetupValue.IntV(22)))
        assertNull(FieldValidator.validate(field("front.camberDeg"), SetupValue.DecimalV(-1.5)))
    }

    @Test
    fun `境界値ちょうどは有効`() {
        val pinion = numberField("pinion")
        assertNull(FieldValidator.validate(pinion, SetupValue.IntV(pinion.min.toInt())))
        assertNull(FieldValidator.validate(pinion, SetupValue.IntV(pinion.max.toInt())))
    }

    @Test
    fun `範囲外は OutOfRange を返す 例外は投げない`() {
        val violation = FieldValidator.validate(field("tireMm"), SetupValue.IntV(200))
        assertTrue(violation is FieldViolation.OutOfRange)
        violation as FieldViolation.OutOfRange
        assertEquals("tireMm", violation.fieldKey)
        assertEquals(200.0, violation.actual, 0.0)
        assertEquals(120.0, violation.max, 0.0)
    }

    @Test
    fun `NaN と無限大は範囲外`() {
        assertTrue(
            FieldValidator.validate(field("front.camberDeg"), SetupValue.DecimalV(Double.NaN))
                is FieldViolation.OutOfRange
        )
        assertTrue(
            FieldValidator.validate(field("internalRatio"), SetupValue.DecimalV(Double.POSITIVE_INFINITY))
                is FieldViolation.OutOfRange
        )
    }

    // ----- 型 -----

    @Test
    fun `型違いは TypeMismatch`() {
        val violation = FieldValidator.validate(field("pinion"), SetupValue.TextV("22"))
        assertTrue(violation is FieldViolation.TypeMismatch)
        assertEquals("number", (violation as FieldViolation.TypeMismatch).expected)
        assertEquals("text", violation.actual)
    }

    @Test
    fun `整数項目に端数のある小数は入れられない`() {
        assertTrue(
            FieldValidator.validate(field("pinion"), SetupValue.DecimalV(22.5))
                is FieldViolation.TypeMismatch
        )
        // 端数が無ければ通す（JSON 経由だと 22 が 22.0 になるため）
        assertNull(FieldValidator.validate(field("pinion"), SetupValue.DecimalV(22.0)))
    }

    // ----- 選択肢・テキスト -----

    @Test
    fun `選択肢にないキーは UnknownChoice`() {
        assertNull(FieldValidator.validate(field("front.springRate"), SetupValue.ChoiceV("soft")))
        val violation = FieldValidator.validate(field("front.springRate"), SetupValue.ChoiceV("むらさき"))
        assertTrue(violation is FieldViolation.UnknownChoice)
        assertEquals("むらさき", (violation as FieldViolation.UnknownChoice).optionKey)
    }

    @Test
    fun `長すぎる自由入力は TooLong`() {
        val brand = field("front.damperOilBrand")
        assertNull(FieldValidator.validate(brand, SetupValue.TextV("タミヤ")))
        val violation = FieldValidator.validate(brand, SetupValue.TextV("あ".repeat(31)))
        assertTrue(violation is FieldViolation.TooLong)
        assertEquals(30, (violation as FieldViolation.TooLong).maxLength)
    }

    // ----- 束ごとの検証 -----

    @Test
    fun `空欄は違反にしない`() {
        assertTrue(FieldValidator.validateAll(SetupValues.EMPTY).isEmpty())
        assertTrue(FieldValidator.isValid(SetupValues.EMPTY))
    }

    @Test
    fun `未知キーは違反にしない 保全が仕様`() {
        val values = SetupValues.of("future.newField" to SetupValue.IntV(999))
        assertTrue(FieldValidator.validateAll(values).isEmpty())
        assertEquals(setOf("future.newField"), values.unknownKeys())
    }

    @Test
    fun `違反はレジストリの宣言順で返る`() {
        val values = SetupValues.of(
            "tireMm" to SetupValue.IntV(200),
            "pinion" to SetupValue.IntV(999)
        )
        val violations = FieldValidator.validateAll(values)
        assertEquals(listOf("pinion", "tireMm"), violations.map { it.fieldKey })
        assertFalse(FieldValidator.isValid(values))
    }

    // ----- 丸め -----

    @Test
    fun `coerce は範囲に丸める`() {
        assertEquals(
            SetupValue.IntV(40),
            FieldValidator.coerce(field("pinion"), SetupValue.IntV(999))
        )
        assertEquals(
            SetupValue.IntV(14),
            FieldValidator.coerce(field("pinion"), SetupValue.IntV(-5))
        )
    }

    @Test
    fun `coerce は項目の小数桁に合わせて型を決める`() {
        // 整数項目に小数が来たら整数に落ちる
        assertEquals(
            SetupValue.IntV(22),
            FieldValidator.coerce(field("pinion"), SetupValue.DecimalV(22.7))
        )
        assertEquals(
            SetupValue.DecimalV(-1.5),
            FieldValidator.coerce(field("front.camberDeg"), SetupValue.DecimalV(-1.5))
        )
    }

    @Test
    fun `coerce は丸めようがない値を捨てる`() {
        assertNull(FieldValidator.coerce(field("pinion"), SetupValue.TextV("にじゅうに")))
        assertNull(FieldValidator.coerce(field("pinion"), SetupValue.DecimalV(Double.NaN)))
        assertNull(FieldValidator.coerce(field("front.springRate"), SetupValue.ChoiceV("むらさき")))
    }

    @Test
    fun `coerce は自由入力を切り詰める`() {
        val coerced = FieldValidator.coerce(field("front.damperOilBrand"), SetupValue.TextV("あ".repeat(50)))
        assertEquals(30, (coerced as SetupValue.TextV).value.length)
    }

    @Test
    fun `coerceAll は未知キーを通し 不正な既知キーを落とす`() {
        val values = SetupValues.of(
            "pinion" to SetupValue.IntV(999),
            "spur" to SetupValue.TextV("こわれた値"),
            "future.newField" to SetupValue.IntV(1)
        )
        val coerced = FieldValidator.coerceAll(values)
        assertEquals(40, coerced.intOf("pinion"))
        assertFalse("spur" in coerced)
        assertEquals(1, coerced.intOf("future.newField"))
        assertTrue(FieldValidator.isValid(coerced))
    }

    @Test
    fun `レジストリの初期値は検証を通る`() {
        // 既定値の定義ミスをここでも押さえる（SchemaTest と別経路の確認）
        val values = TouringSetupSchema.initialValues(ChassisDefaults(2.6, 63))
        assertEquals(emptyList<FieldViolation>(), FieldValidator.validateAll(values))
    }
}
