package io.github.taskengineer.rcgear.domain.model

import io.github.taskengineer.rcgear.domain.schema.ChassisTrait
import io.github.taskengineer.rcgear.domain.schema.FieldUi
import io.github.taskengineer.rcgear.domain.schema.NumberFieldDef
import io.github.taskengineer.rcgear.domain.schema.SectionDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 項目の出し分け（M-7）。
 *
 * 一番守りたいのは **「分からないから隠す」をしない**こと。
 * 同梱シャーシ DB にはまだ裏の取れていない素性があり、不明を理由に隠すと
 * 実際には設定できる欄が無言で消える。隠すのは「その車には確実に存在しない」
 * と分かっている場合だけ。
 */
class ChassisTraitsTest {

    private val centerDiffOil = NumberFieldDef(
        key = "center.diffOilCst",
        ui = FieldUi.NUMBER_FIELD,
        min = 500.0, max = 1_000_000.0, step = 100.0,
        requires = ChassisTrait.HAS_CENTER_DIFF
    )
    private val beltTension = NumberFieldDef(
        key = "beltTension",
        ui = FieldUi.STEPPER,
        min = 0.0, max = 10.0, step = 1.0,
        requires = ChassisTrait.BELT_DRIVE
    )
    private val pinion = NumberFieldDef(
        key = "pinion",
        ui = FieldUi.SLIDER,
        min = 14.0, max = 40.0, step = 1.0
    )

    @Test
    fun `条件が無い項目は常に出る`() {
        assertTrue(ChassisTraits.UNKNOWN.satisfies(null))
        assertTrue(ChassisTraits(drive = ChassisDrive.SHAFT_4WD).satisfies(null))
    }

    @Test
    fun `素性が不明なら出す`() {
        assertTrue(ChassisTraits.UNKNOWN.satisfies(ChassisTrait.HAS_CENTER_DIFF))
        assertTrue(ChassisTraits.UNKNOWN.satisfies(ChassisTrait.BELT_DRIVE))
    }

    @Test
    fun `センターデフが無いと分かっている車では隠す`() {
        val noDiff = ChassisTraits(hasCenterDiff = false)
        assertFalse(noDiff.satisfies(ChassisTrait.HAS_CENTER_DIFF))

        val hasDiff = ChassisTraits(hasCenterDiff = true)
        assertTrue(hasDiff.satisfies(ChassisTrait.HAS_CENTER_DIFF))
    }

    @Test
    fun `シャフト車ではベルト前提の項目を隠す`() {
        assertFalse(ChassisTraits(drive = ChassisDrive.SHAFT_4WD).satisfies(ChassisTrait.BELT_DRIVE))
        assertTrue(ChassisTraits(drive = ChassisDrive.BELT_4WD).satisfies(ChassisTrait.BELT_DRIVE))
        assertTrue(ChassisTraits(drive = ChassisDrive.HYBRID_4WD).satisfies(ChassisTrait.BELT_DRIVE))
    }

    @Test
    fun `セクションが素性に応じて項目を絞る`() {
        val section = SectionDef(key = "drivetrain", fields = listOf(pinion, centerDiffOil, beltTension))

        assertEquals(
            listOf("pinion", "center.diffOilCst", "beltTension"),
            section.visibleFields(ChassisTraits.UNKNOWN).map { it.key }
        )
        assertEquals(
            listOf("pinion"),
            section.visibleFields(
                ChassisTraits(drive = ChassisDrive.SHAFT_4WD, hasCenterDiff = false)
            ).map { it.key }
        )
    }

    @Test
    fun `JSON の文字列を解釈できる`() {
        assertEquals(ChassisCategory.TOURING, ChassisCategory.fromKey("touring"))
        assertEquals(ChassisCategory.BUGGY, ChassisCategory.fromKey("BUGGY"))
        // 知らない値で読み込みを落とさない
        assertEquals(ChassisCategory.OTHER, ChassisCategory.fromKey("まだ無い分類"))
        assertEquals(ChassisCategory.OTHER, ChassisCategory.fromKey(null))

        assertEquals(ChassisDrive.SHAFT_4WD, ChassisDrive.fromKey("shaft_4wd"))
        assertEquals(null, ChassisDrive.fromKey("まだ無い駆動方式"))
        assertEquals(null, ChassisDrive.fromKey(null))
    }
}
