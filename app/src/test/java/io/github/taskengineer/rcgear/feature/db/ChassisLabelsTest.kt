package io.github.taskengineer.rcgear.feature.db

import io.github.taskengineer.rcgear.domain.model.ChassisCategory
import io.github.taskengineer.rcgear.domain.model.ChassisDrive
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 「定義は `:core:domain`、文言は `:app`」の担保（F-5 / HANDOFF §5.3）。
 *
 * enum に値を足して `ChassisLabels` への追加を忘れると、画面の選択肢が
 * 空欄になる。`when` が網羅を強制するのでコンパイルは止まるが、
 * `R.string` の行を他の値と取り違える事故までは止められないので、
 * 「0 ではない」ことだけ機械的に見る（Robolectric が無いので文言そのものは解決できない）。
 */
class ChassisLabelsTest {

    @Test
    fun `全ての車種がラベルを持つ`() {
        for (category in ChassisCategory.entries) {
            assertNotEquals("車種 $category のラベルが 0", 0, category.labelRes)
        }
    }

    @Test
    fun `全ての駆動方式がラベルを持つ`() {
        for (drive in ChassisDrive.entries) {
            assertNotEquals("駆動方式 $drive のラベルが 0", 0, drive.labelRes)
        }
    }

    @Test
    fun `車種と駆動方式のラベルは値ごとに違う`() {
        // 同じ R.string を貼り間違えると、選択肢が全部同じ名前になる
        val categories = ChassisCategory.entries.map { it.labelRes }
        assertNotEquals(1, categories.toSet().size)
        val drives = ChassisDrive.entries.map { it.labelRes }
        assertNotEquals(1, drives.toSet().size)
    }
}
