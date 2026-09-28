package io.github.taskengineer.rcgear.feature.sheet

import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.ui.Strings
import io.github.taskengineer.rcgear.domain.model.ChassisTraits
import io.github.taskengineer.rcgear.domain.model.SessionConditions
import io.github.taskengineer.rcgear.domain.model.SetupValue
import io.github.taskengineer.rcgear.domain.model.SetupValues
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SheetTextFormatter] のテスト（F-4）。
 *
 * Robolectric を入れていないので文言は解決できない。[Strings] を Fake に差し替え、
 * **構造**（どの行が出て、どの行が出ないか）だけを見る。
 * 見たいのは「空欄を出さない」「値が 1 つも無いセクションは見出しごと出さない」で、
 * どちらも守られていないと 40 項目のうち 30 行が「—」の本文になる。
 *
 * Fake は既知のリソース ID だけ読みやすい名前に置き換え、残りは `R<id>` を返す。
 * 書式（`%1$s: ` など）もここで再現する — 実物の strings.xml と役割が同じ形にしておく。
 */
class SheetTextFormatterTest {

    private val strings = Strings { id, args ->
        when (id) {
            R.string.sheet_share_title -> "${args[0]} / ${args[1]}"
            R.string.sheet_share_section -> "[${args[0]}]"
            R.string.sheet_share_line -> "${args[0]}: "
            R.string.sheet_share_cell -> "${args[0]} ${args[1]}"
            R.string.sheet_value_with_unit -> "${args[0]}${args[1]}"
            R.string.sheet_value_empty -> "—"
            R.string.sheet_section_gear -> "ギア"
            R.string.sheet_section_suspension -> "サスペンション"
            R.string.sheet_section_damper -> "ダンパー"
            R.string.field_pinion -> "ピニオン"
            R.string.field_spur -> "スパー"
            R.string.field_camber -> "キャンバー"
            R.string.sheet_column_front -> "F"
            R.string.sheet_column_rear -> "R"
            R.string.unit_teeth -> "T"
            R.string.unit_degree -> "°"
            R.string.list_separator -> "・"
            else -> "R$id"
        }
    }

    @Test
    fun `入っている値だけが行になる`() {
        val text = format(
            values = SetupValues.of(
                "pinion" to SetupValue.IntV(29),
                "spur" to SetupValue.IntV(84)
            )
        )

        assertTrue(text.contains("ピニオン: 29T"))
        assertTrue(text.contains("スパー: 84T"))
        assertFalse("空欄の項目まで出ている", text.contains("—"))
    }

    @Test
    fun `値が 1 つも無いセクションは見出しごと出ない`() {
        val text = format(values = SetupValues.of("pinion" to SetupValue.IntV(29)))

        assertTrue(text.contains("[ギア]"))
        assertFalse(text.contains("[ダンパー]"))
    }

    @Test
    fun `グリッドのセクションは列名を値に添える`() {
        // 等幅で読まれる保証が無いので、桁揃えではなく列名で区別する
        val text = format(
            values = SetupValues.of(
                "front.camberDeg" to SetupValue.DecimalV(-2.0),
                "rear.camberDeg" to SetupValue.DecimalV(-1.5)
            )
        )

        assertTrue(text, text.contains("キャンバー: F -2.0°  R -1.5°"))
    }

    @Test
    fun `片側しか入っていない行はその列だけ出す`() {
        val text = format(values = SetupValues.of("front.camberDeg" to SetupValue.DecimalV(-2.0)))

        assertTrue(text, text.contains("キャンバー: F -2.0°"))
        assertFalse(text.contains("R "))
    }

    @Test
    fun `先頭は車名とシート名`() {
        val text = format(values = SetupValues.EMPTY)

        assertTrue(text.startsWith("TT-02 #1 / Rd1"))
    }

    @Test
    fun `シャーシの素性で消える項目は出ない`() {
        // センターデフを持たない車のデフオイルは、値が入っていても出さない
        val text = format(
            values = SetupValues.of("center.diffOilCst" to SetupValue.IntV(5000)),
            traits = ChassisTraits(hasCenterDiff = false)
        )

        assertFalse(text, text.contains("5000"))
    }

    @Test
    fun `走行条件は 1 行にまとまる`() {
        val text = format(
            values = SetupValues.EMPTY,
            conditions = SessionConditions(trackName = "パルス", surface = "アスファルト")
        )

        assertTrue(text, text.contains("パルス・アスファルト"))
    }

    // ----- helpers -----

    private fun format(
        values: SetupValues,
        traits: ChassisTraits = ChassisTraits.UNKNOWN,
        conditions: SessionConditions = SessionConditions()
    ): String = SheetTextFormatter.format(
        strings = strings,
        sheetName = "Rd1",
        carName = "TT-02 #1",
        chassisName = null,
        conditions = conditions,
        note = null,
        values = values,
        traits = traits,
        gearResult = null
    )
}
