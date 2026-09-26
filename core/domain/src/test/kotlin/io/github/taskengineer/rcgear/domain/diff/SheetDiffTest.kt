package io.github.taskengineer.rcgear.domain.diff

import io.github.taskengineer.rcgear.domain.model.ChassisDefaults
import io.github.taskengineer.rcgear.domain.model.SetupValue
import io.github.taskengineer.rcgear.domain.model.SetupValues
import io.github.taskengineer.rcgear.domain.model.initialValues
import io.github.taskengineer.rcgear.domain.schema.TouringSetupSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 差分（M-5）。3 つの比較軸が 1 つの純粋関数に落ちていることの確認。
 *
 * 見ているのは 追加 / 削除 / 変更 / 同値 の 4 通り、未知キーの扱い、並び順、
 * そして「エクスポートして読み戻しただけで全項目が変更扱いになる」事故の防止。
 */
class SheetDiffTest {

    @Test
    fun `値が違えば CHANGED`() {
        val left = SetupValues.of("pinion" to SetupValue.IntV(24))
        val right = SetupValues.of("pinion" to SetupValue.IntV(22))
        val diff = SheetDiff.compare(left, right).single()
        assertEquals("pinion", diff.fieldKey)
        assertEquals(DiffKind.CHANGED, diff.kind)
        assertEquals(SetupValue.IntV(24), diff.left)
        assertEquals(SetupValue.IntV(22), diff.right)
        assertFalse(diff.isUnknownField)
    }

    @Test
    fun `主役にだけある項目は ADDED`() {
        val diff = SheetDiff.compare(
            SetupValues.of("front.casterDeg" to SetupValue.DecimalV(6.0)),
            SetupValues.EMPTY
        ).single()
        assertEquals(DiffKind.ADDED, diff.kind)
        assertNull(diff.right)
    }

    @Test
    fun `相手にだけある項目は REMOVED`() {
        val diff = SheetDiff.compare(
            SetupValues.EMPTY,
            SetupValues.of("front.casterDeg" to SetupValue.DecimalV(6.0))
        ).single()
        assertEquals(DiffKind.REMOVED, diff.kind)
        assertNull(diff.left)
    }

    @Test
    fun `同値は既定では返らない`() {
        val values = SetupValues.of("pinion" to SetupValue.IntV(22))
        assertTrue(SheetDiff.compare(values, values).isEmpty())
        assertFalse(SheetDiff.hasDifference(values, values))
    }

    @Test
    fun `includeSame なら同値も SAME として返る`() {
        val values = SetupValues.of("pinion" to SetupValue.IntV(22))
        val diff = SheetDiff.compare(values, values, includeSame = true).single()
        assertEquals(DiffKind.SAME, diff.kind)
    }

    @Test
    fun `どちらにも無い項目は結果に入らない`() {
        assertTrue(SheetDiff.compare(SetupValues.EMPTY, SetupValues.EMPTY, includeSame = true).isEmpty())
    }

    @Test
    fun `整数と小数で同じ値なら差分にしない`() {
        // JSON を往復すると 22 が 22.0 になる。ここを差分にすると
        // 「書き出して読み戻しただけで全項目が変更扱い」になる
        val left = SetupValues.of("pinion" to SetupValue.IntV(22))
        val right = SetupValues.of("pinion" to SetupValue.DecimalV(22.0))
        assertTrue(SheetDiff.compare(left, right).isEmpty())
    }

    @Test
    fun `型が違い数値でもなければ差分になる`() {
        val left = SetupValues.of("front.springRate" to SetupValue.ChoiceV("soft"))
        val right = SetupValues.of("front.springRate" to SetupValue.TextV("soft"))
        assertEquals(DiffKind.CHANGED, SheetDiff.compare(left, right).single().kind)
    }

    @Test
    fun `選択肢はキーで比較する`() {
        val left = SetupValues.of("front.springRate" to SetupValue.ChoiceV("soft"))
        val right = SetupValues.of("front.springRate" to SetupValue.ChoiceV("hard"))
        val diff = SheetDiff.compare(left, right).single()
        assertEquals(DiffKind.CHANGED, diff.kind)
        assertEquals("soft", (diff.left as SetupValue.ChoiceV).key)
    }

    @Test
    fun `並び順はレジストリの宣言順 未知キーは末尾`() {
        val left = SetupValues.of(
            "zzz.unknown" to SetupValue.IntV(1),
            "front.camberDeg" to SetupValue.DecimalV(-1.0),
            "aaa.unknown" to SetupValue.IntV(1),
            "pinion" to SetupValue.IntV(24)
        )
        val keys = SheetDiff.compare(left, SetupValues.EMPTY).map { it.fieldKey }
        assertEquals(listOf("pinion", "front.camberDeg", "aaa.unknown", "zzz.unknown"), keys)
    }

    @Test
    fun `未知キーも差分に出るが定義は持たない`() {
        val diff = SheetDiff.compare(
            SetupValues.of("future.newField" to SetupValue.IntV(1)),
            SetupValues.of("future.newField" to SetupValue.IntV(2))
        ).single()
        assertTrue(diff.isUnknownField)
        assertNull(diff.field)
        assertEquals(DiffKind.CHANGED, diff.kind)
    }

    @Test
    fun `既知の項目には定義が付く`() {
        val diff = SheetDiff.compare(
            SetupValues.of("pinion" to SetupValue.IntV(24)),
            SetupValues.EMPTY
        ).single()
        assertEquals(TouringSetupSchema.byKey["pinion"], diff.field)
    }

    // ----- 軸 A: シャーシ標準との比較（旧 SnapshotDiffCard の後継） -----

    @Test
    fun `シャーシ標準と同じなら差分なし`() {
        val defaults = ChassisDefaults(internalRatio = 2.6, defaultTireMm = 63)
        val values = TouringSetupSchema.initialValues(defaults)
        assertTrue(SheetDiff.compareToChassisDefault(values, defaults).isEmpty())
    }

    @Test
    fun `シャーシ標準から変えた所だけが出る`() {
        val defaults = ChassisDefaults(internalRatio = 2.6, defaultTireMm = 63)
        val values = TouringSetupSchema.initialValues(defaults)
            .with("pinion", SetupValue.IntV(28))
            .with("front.camberDeg", SetupValue.DecimalV(-1.5))
        val diff = SheetDiff.compareToChassisDefault(values, defaults)
        assertEquals(listOf("pinion", "front.camberDeg"), diff.map { it.fieldKey })
        assertEquals(DiffKind.CHANGED, diff[0].kind)
        // 標準では空欄なのでキャンバーは ADDED になる
        assertEquals(DiffKind.ADDED, diff[1].kind)
    }

    @Test
    fun `内部減速比を上書きしたシャーシとの差分が出る`() {
        // 旧 internalRatioSnapshot の役目。シートは保存時の絶対値を持ち続ける
        val sheet = TouringSetupSchema.initialValues(ChassisDefaults(2.6, 63))
        val afterDbEdit = ChassisDefaults(internalRatio = 2.7, defaultTireMm = 63)
        val diff = SheetDiff.compareToChassisDefault(sheet, afterDbEdit).single()
        assertEquals("internalRatio", diff.fieldKey)
        assertEquals(2.6, (diff.left as SetupValue.DecimalV).value, 1e-9)
        assertEquals(2.7, (diff.right as SetupValue.DecimalV).value, 1e-9)
    }

    // ----- 軸 B: ベースラインシートとの比較 -----

    @Test
    fun `前回のセットから変えた所が一覧で出る`() {
        val baseline = TouringSetupSchema.initialValues(ChassisDefaults(2.6, 63))
            .with("front.damperOilValue", SetupValue.DecimalV(400.0))
            .with("front.springRate", SetupValue.ChoiceV("medium"))
        val current = baseline
            .with("front.damperOilValue", SetupValue.DecimalV(450.0))
            .without("front.springRate")
            .with("rear.springRate", SetupValue.ChoiceV("soft"))

        val diff = SheetDiff.compare(current, baseline)
        assertEquals(
            mapOf(
                "front.damperOilValue" to DiffKind.CHANGED,
                "front.springRate" to DiffKind.REMOVED,
                "rear.springRate" to DiffKind.ADDED
            ),
            diff.associate { it.fieldKey to it.kind }
        )
    }
}
