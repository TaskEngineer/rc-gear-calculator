package io.github.taskengineer.rcgear.core.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * 表示整形のテスト（REF-6 / S-7）。
 *
 * 一番大事なのは **端末ロケールに影響されないこと**。
 * ドイツ語圏のように小数点がカンマになる地域で "2,60" と表示されると、
 * 桁区切りのカンマと区別が付かなくなる。各テストで既定ロケールを
 * わざと Locale.GERMANY に差し替え、それでも出力が変わらないことを見る。
 *
 * メソッド名のプレフィクスでカテゴリを表現 (ratio_, speed_, rpm_, locale_)。
 */
class FormatTest {

    private lateinit var original: Locale

    @Before
    fun setUp() {
        original = Locale.getDefault()
    }

    @After
    fun tearDown() {
        Locale.setDefault(original)
    }

    // ----- ratio_ -----

    @Test
    fun `ratio_小数2桁で整形する`() {
        assertEquals("2.60", 2.6.formatRatio())
        assertEquals("8.27", 8.2714.formatRatio())
        assertEquals("1.00", 1.0.formatRatio())
    }

    @Test
    fun `ratio_3桁目は四捨五入される`() {
        assertEquals("2.61", 2.606.formatRatio())
        assertEquals("2.60", 2.604.formatRatio())
    }

    // ----- speed_ -----

    @Test
    fun `speed_小数1桁で整形する`() {
        assertEquals("57.5", 57.54.formatSpeed())
        assertEquals("0.0", 0.0.formatSpeed())
    }

    @Test
    fun `speed_Float 版も Double 版と同じ結果になる`() {
        // Compose の animateFloatAsState が Float を返すためのオーバーロード
        assertEquals(57.5.formatSpeed(), 57.5f.formatSpeed())
    }

    @Test
    fun `speed_電圧も小数1桁`() {
        assertEquals("7.4", 7.4.formatVoltage())
    }

    // ----- rpm_ -----

    @Test
    fun `rpm_整数に丸めて3桁区切りにする`() {
        assertEquals("48,100", 48100.0.formatRpm())
        assertEquals("5,817", 5817.4.formatRpm())
        assertEquals("5,818", 5817.6.formatRpm())
        assertEquals("999", 999.0.formatRpm())
    }

    // ----- locale_: これが本題 -----

    @Test
    fun `locale_小数点がカンマの地域でもピリオドのまま出力する`() {
        Locale.setDefault(Locale.GERMANY)

        assertEquals("2.60", 2.6.formatRatio())
        assertEquals("57.5", 57.5.formatSpeed())
        assertEquals("7.4", 7.4.formatVoltage())
    }

    @Test
    fun `locale_桁区切りも地域に影響されない`() {
        Locale.setDefault(Locale.GERMANY)

        // ドイツ語ロケールの既定なら "48.100" になってしまう
        assertEquals("48,100", 48100.0.formatRpm())
    }
}
