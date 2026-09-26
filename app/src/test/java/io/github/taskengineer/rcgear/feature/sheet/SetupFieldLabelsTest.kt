package io.github.taskengineer.rcgear.feature.sheet

import io.github.taskengineer.rcgear.domain.schema.ChoiceFieldDef
import io.github.taskengineer.rcgear.domain.schema.ColumnDef
import io.github.taskengineer.rcgear.domain.schema.FieldUnit
import io.github.taskengineer.rcgear.domain.schema.TouringSetupSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「定義は `:core:domain`、文言は `:app`」という分担（HANDOFF §5.3）の担保。
 *
 * レジストリに項目を足して `strings.xml` と `SetupFieldLabels` への追加を忘れると、
 * その項目は画面上で無言の空欄になる。コンパイラはそれを捕まえられないので、
 * 網羅をここで機械的に検証する。
 *
 * Robolectric は入れていないので文言そのものは解決できない。
 * 見るのは「`@StringRes` が 0 でない ＝ 対応表に行がある」ことまで。
 */
class SetupFieldLabelsTest {

    @Test
    fun `全フィールドがラベルを持つ`() {
        val missing = TouringSetupSchema.allFields
            .map { it.key }
            .filterNot { SetupFieldLabels.hasFieldLabel(it) }
        assertEquals("ラベル未定義のフィールド: $missing", emptyList<String>(), missing)
    }

    @Test
    fun `全フィールドの labelRes が解決する`() {
        for (field in TouringSetupSchema.allFields) {
            assertNotEquals("${field.key} のラベルが 0", SetupFieldLabels.NO_LABEL, field.labelRes)
        }
    }

    @Test
    fun `全セクションがラベルを持つ`() {
        for (section in TouringSetupSchema.sections) {
            assertNotEquals(
                "セクション ${section.key} のラベルが 0",
                SetupFieldLabels.NO_LABEL,
                section.labelRes
            )
        }
    }

    @Test
    fun `使われている全ての列がラベルを持つ`() {
        val columns = TouringSetupSchema.sections.flatMap { it.columns.orEmpty() }.distinct()
        assertTrue("列を持つセクションが 1 つも無い", columns.isNotEmpty())
        for (column in columns) {
            assertNotEquals("列 ${column.key} のラベルが 0", SetupFieldLabels.NO_LABEL, column.labelRes)
        }
    }

    @Test
    fun `全ての選択肢がラベルを持つ`() {
        val missing = mutableListOf<String>()
        for (choiceSet in TouringSetupSchema.choiceSets) {
            for (option in choiceSet.options) {
                if (choiceSet.labelResOf(option) == SetupFieldLabels.NO_LABEL) {
                    missing += "${choiceSet.id}/${option.key}"
                }
            }
        }
        assertEquals("ラベル未定義の選択肢: $missing", emptyList<String>(), missing)
    }

    @Test
    fun `NONE 以外の全ての単位がラベルを持つ`() {
        assertEquals(
            "NONE は単位を描かない",
            SetupFieldLabels.NO_LABEL,
            SetupFieldLabels.unitLabelRes(FieldUnit.NONE)
        )
        for (unit in FieldUnit.entries - FieldUnit.NONE) {
            assertNotEquals(
                "単位 $unit のラベルが 0",
                SetupFieldLabels.NO_LABEL,
                SetupFieldLabels.unitLabelRes(unit)
            )
        }
    }

    @Test
    fun `未知のキーには 0 を返す`() {
        assertEquals(SetupFieldLabels.NO_LABEL, SetupFieldLabels.fieldLabelRes("存在しないキー"))
        assertEquals(SetupFieldLabels.NO_LABEL, SetupFieldLabels.sectionLabelRes("存在しない"))
        assertEquals(SetupFieldLabels.NO_LABEL, SetupFieldLabels.choiceLabelRes("springRate", "存在しない"))
    }

    @Test
    fun `前後で同じ行の項目は同じ文言を共有する`() {
        val front = TouringSetupSchema.byKey.getValue("front.camberDeg")
        val rear = TouringSetupSchema.byKey.getValue("rear.camberDeg")
        assertEquals(front.labelRes, rear.labelRes)
    }

    @Test
    fun `グリッドの行ラベルが解決する`() {
        for (section in TouringSetupSchema.sections) {
            for (row in section.grid()) {
                assertNotEquals("行 ${row.rowKey} のラベルが 0", SetupFieldLabels.NO_LABEL, row.labelRes)
            }
        }
    }

    @Test
    fun `選択肢フィールドからも直接ラベルを引ける`() {
        val field = TouringSetupSchema.byKey.getValue("front.springRate") as ChoiceFieldDef
        assertNotEquals(SetupFieldLabels.NO_LABEL, field.labelResOf("soft"))
        assertEquals(SetupFieldLabels.NO_LABEL, field.labelResOf("存在しない"))
    }

    @Test
    fun `センター列は先に文言だけ用意してある`() {
        // M-7 で駆動系セクション（デフ）を足すときに使う。列の文言だけ先にある状態を固定する
        assertNotEquals(SetupFieldLabels.NO_LABEL, ColumnDef.CENTER.labelRes)
    }
}
