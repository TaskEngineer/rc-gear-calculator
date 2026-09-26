package io.github.taskengineer.rcgear.domain.schema

import io.github.taskengineer.rcgear.domain.model.GearCalculationInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * レジストリの健全性チェック（M-1 / 計画 §7.4）。
 *
 * キー名の打ち間違いや型の不整合はコンパイラが捕まえるので、ここで見るのは
 * 「コンパイルは通るが定義として壊れている」パターンだけ:
 * キーの重複・範囲の反転・UI と型の不整合・列とキー接頭辞のずれ、
 * そして `GearCalculationInput` の定数とギアセクションの範囲のずれ。
 */
class TouringSetupSchemaTest {

    @Test
    fun `フィールドキーはレジストリ全体で一意`() {
        val keys = TouringSetupSchema.allFields.map { it.key }
        val duplicated = keys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertEquals("重複したキー: $duplicated", emptySet<String>(), duplicated)
        assertEquals(keys.size, TouringSetupSchema.byKey.size)
    }

    @Test
    fun `セクションキーは一意`() {
        val keys = TouringSetupSchema.sections.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `数値項目の範囲と刻みが妥当`() {
        for (field in TouringSetupSchema.allFields.filterIsInstance<NumberFieldDef>()) {
            assertTrue("${field.key}: min < max であること", field.min < field.max)
            assertTrue("${field.key}: step > 0 であること", field.step > 0.0)
            assertTrue("${field.key}: decimals >= 0 であること", field.decimals >= 0)
            assertTrue(
                "${field.key}: step が範囲を超えている",
                field.step <= field.max - field.min
            )
        }
    }

    @Test
    fun `整数項目の範囲と刻みは整数値`() {
        val integerFields = TouringSetupSchema.allFields
            .filterIsInstance<NumberFieldDef>()
            .filter { it.isInteger }
        for (field in integerFields) {
            for ((name, value) in listOf("min" to field.min, "max" to field.max, "step" to field.step)) {
                assertEquals("${field.key}: $name は整数値であること", value, kotlin.math.floor(value), 0.0)
            }
        }
    }

    @Test
    fun `既定値は範囲内`() {
        for (field in TouringSetupSchema.allFields.filterIsInstance<NumberFieldDef>()) {
            val default = field.defaultValue ?: continue
            assertTrue(
                "${field.key}: 既定値 $default が ${field.min}..${field.max} の外",
                default in field.min..field.max
            )
        }
        for (field in TouringSetupSchema.allFields.filterIsInstance<ChoiceFieldDef>()) {
            val default = field.defaultValue ?: continue
            assertTrue(
                "${field.key}: 既定値 $default が選択肢に無い",
                field.choiceSet.contains(default)
            )
        }
    }

    @Test
    fun `UI の指定が型と整合している`() {
        for (field in TouringSetupSchema.allFields) {
            val allowed = when (field) {
                is NumberFieldDef -> setOf(FieldUi.SLIDER, FieldUi.STEPPER, FieldUi.NUMBER_FIELD)
                is ChoiceFieldDef -> setOf(FieldUi.SELECT)
                is TextFieldDef -> setOf(FieldUi.TEXT_FIELD, FieldUi.TEXT_AREA)
                is BoolFieldDef -> setOf(FieldUi.SWITCH)
            }
            assertTrue("${field.key}: ${field.ui} は許可されない", field.ui in allowed)
        }
    }

    @Test
    fun `選択肢は空でなくキーも一意`() {
        for (choiceSet in TouringSetupSchema.choiceSets) {
            assertTrue("${choiceSet.id}: 選択肢が空", choiceSet.options.isNotEmpty())
            val keys = choiceSet.options.map { it.key }
            assertEquals("${choiceSet.id}: 選択肢キーが重複", keys.size, keys.toSet().size)
        }
    }

    @Test
    fun `同じ id の選択肢集合は同じ中身`() {
        val byId = TouringSetupSchema.allFields
            .filterIsInstance<ChoiceFieldDef>()
            .map { it.choiceSet }
            .groupBy { it.id }
        for ((id, sets) in byId) {
            assertEquals("$id: 同じ id で中身の違う選択肢集合がある", 1, sets.distinct().size)
        }
    }

    @Test
    fun `列を持つセクションのキーは列名で始まる`() {
        for (section in TouringSetupSchema.sections) {
            val columns = section.columns ?: continue
            val columnKeys = columns.map { it.key }
            for (field in section.fields) {
                assertTrue(
                    "${field.key}: $columnKeys のいずれかで始まること",
                    columnKeys.any { field.key.startsWith("$it.") }
                )
            }
        }
    }

    @Test
    fun `列を持たないセクションは grid が空`() {
        assertTrue(TouringSetupSchema.GEAR.grid().isEmpty())
    }

    @Test
    fun `grid は行ごとに列をまとめ 欠けた列は入らない`() {
        val rows = TouringSetupSchema.SUSPENSION.grid()
        // 宣言順（camber → toe → caster → rideHeight → droop）がそのまま行順になる
        assertEquals(
            listOf("camberDeg", "toeDeg", "casterDeg", "rideHeightMm", "droopMm"),
            rows.map { it.rowKey }
        )
        val camber = rows.first { it.rowKey == "camberDeg" }
        assertEquals("front.camberDeg", camber.byColumn.getValue("front").key)
        assertEquals("rear.camberDeg", camber.byColumn.getValue("rear").key)
        // キャスターはフロントのみ
        val caster = rows.first { it.rowKey == "casterDeg" }
        assertNotNull(caster.byColumn["front"])
        assertNull(caster.byColumn["rear"])
    }

    @Test
    fun `grid に全フィールドが漏れなく入る`() {
        for (section in TouringSetupSchema.sections) {
            if (section.columns == null) continue
            val inGrid = section.grid().flatMap { it.byColumn.values }.map { it.key }.toSet()
            assertEquals(section.fields.map { it.key }.toSet(), inGrid)
        }
    }

    @Test
    fun `sectionOf が所属セクションを返し 未知キーには null`() {
        assertEquals("gear", TouringSetupSchema.sectionOf("pinion")?.key)
        assertEquals("damper", TouringSetupSchema.sectionOf("front.springRate")?.key)
        assertNull(TouringSetupSchema.sectionOf("存在しないキー"))
        assertTrue(TouringSetupSchema.isKnown("pinion"))
    }

    /**
     * ギアの範囲は `GearCalculationInput`（Web 版と同一の上下限）と一致していなければならない。
     * 片方だけ動かすと「シートでは入れられるが計算に渡すと弾かれる値」が生まれる。
     */
    @Test
    fun `ギアセクションの範囲が GearCalculationInput の定数と一致する`() {
        fun number(key: String) = TouringSetupSchema.byKey.getValue(key) as NumberFieldDef

        assertRange(number("pinion"), GearCalculationInput.PINION_RANGE)
        assertRange(number("spur"), GearCalculationInput.SPUR_RANGE)
        assertRange(number("motorKv"), GearCalculationInput.KV_RANGE)
        assertRange(number("cells"), GearCalculationInput.CELLS_RANGE)
        assertRange(number("tireMm"), GearCalculationInput.TIRE_MM_RANGE)

        assertEquals(GearCalculationInput.KV_STEP.toDouble(), number("motorKv").step, 0.0)

        assertDefault(number("pinion"), GearCalculationInput.DEFAULT_PINION)
        assertDefault(number("spur"), GearCalculationInput.DEFAULT_SPUR)
        assertDefault(number("motorKv"), GearCalculationInput.DEFAULT_KV)
        assertDefault(number("cells"), GearCalculationInput.DEFAULT_CELLS)
    }

    /** 内部減速比は同梱シャーシ DB の実値（1.0〜5.0）を全て含む範囲であること */
    @Test
    fun `内部減速比の範囲が実在するシャーシを弾かない`() {
        val internalRatio = TouringSetupSchema.byKey.getValue("internalRatio") as NumberFieldDef
        assertTrue(internalRatio.min <= 1.0)
        assertTrue(internalRatio.max >= 5.0)
        assertEquals(ChassisDefault.INTERNAL_RATIO, internalRatio.defaultFrom)
    }

    private fun assertRange(field: NumberFieldDef, range: IntRange) {
        assertEquals("${field.key}: min", range.first.toDouble(), field.min, 0.0)
        assertEquals("${field.key}: max", range.last.toDouble(), field.max, 0.0)
    }

    private fun assertDefault(field: NumberFieldDef, expected: Int) {
        val actual = field.defaultValue
        assertNotNull("${field.key}: 既定値が無い", actual)
        assertEquals("${field.key}: 既定値", expected.toDouble(), actual!!, 0.0)
    }
}
