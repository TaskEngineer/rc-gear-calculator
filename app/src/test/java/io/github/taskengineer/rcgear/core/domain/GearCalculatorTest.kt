package io.github.taskengineer.rcgear.core.domain

import io.github.taskengineer.rcgear.domain.model.GearCalculationInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GearCalculator の単体テスト (JUnit 4)。
 *
 * テスト方針:
 *   - 純粋関数なので入出力のみで検証する。
 *   - 浮動小数点比較は delta（許容誤差）を指定して assertEquals(expected, actual, delta) で行う。
 *   - 代表ケースは Web 版で実際に動作確認された値を用いる。
 *   - メソッド名のプレフィクスでカテゴリを表現 (basic_, rpm_, speed_, balance_, validation_)。
 */
class GearCalculatorTest {

    // 浮動小数点比較の許容誤差。表示桁（小数1〜2桁）の範囲では十分。
    private val delta = 1e-6

    // ===============================================================
    // 基本的なギア比計算
    // ===============================================================

    @Test
    fun `basic_TT-02 キット標準で FDR が約 8 dot 27`() {
        // Web 版 index.html L9（PLAN.md にも記載のキット標準）の値:
        //   primary = 70 / 22 = 3.1818...
        //   fdr     = 3.1818... × 2.6 = 8.2727...
        val input = GearCalculationInput(
            pinion = 22,
            spur = 70,
            internalRatio = 2.6,
            kv = 6500,
            cells = 2,
            tireMm = 63
        )

        val result = GearCalculator.calculate(input)

        assertEquals(70.0 / 22.0, result.primaryRatio, delta)
        assertEquals(8.2727, result.finalDriveRatio, 1e-3)
    }

    @Test
    fun `basic_1次減速比はスパー割るピニオン`() {
        val input = GearCalculationInput(
            pinion = 20, spur = 80, internalRatio = 1.0,
            kv = 4000, cells = 2, tireMm = 63
        )
        val result = GearCalculator.calculate(input)
        // 80 / 20 = 4.0 ちょうど
        assertEquals(4.0, result.primaryRatio, delta)
    }

    @Test
    fun `basic_ベルト直結シャーシでは FDR と 1次減速が一致`() {
        // TA-08 / TC6 等の直結シャーシ
        val input = GearCalculationInput(
            pinion = 25, spur = 100, internalRatio = 1.0,
            kv = 5500, cells = 2, tireMm = 63
        )
        val result = GearCalculator.calculate(input)
        assertEquals(result.primaryRatio, result.finalDriveRatio, delta)
    }

    // ===============================================================
    // 回転数・電圧計算
    // ===============================================================

    @Test
    fun `rpm_電圧はセル数掛ける 3 dot 7`() {
        val input = GearCalculationInput(
            pinion = 22, spur = 84, internalRatio = 2.6,
            kv = 6500, cells = 2, tireMm = 63
        )
        val result = GearCalculator.calculate(input)
        // 2 × 3.7 = 7.4V
        assertEquals(7.4, result.voltage, delta)
    }

    @Test
    fun `rpm_3S バッテリーでも電圧計算は正しい`() {
        val input = GearCalculationInput(
            pinion = 22, spur = 84, internalRatio = 2.6,
            kv = 6500, cells = 3, tireMm = 63
        )
        val result = GearCalculator.calculate(input)
        // 3 × 3.7 = 11.1V
        assertEquals(11.1, result.voltage, delta)
    }

    @Test
    fun `rpm_モーター RPM は KV 掛ける電圧`() {
        val input = GearCalculationInput(
            pinion = 22, spur = 84, internalRatio = 2.6,
            kv = 6500, cells = 2, tireMm = 63
        )
        val result = GearCalculator.calculate(input)
        // 6500 × 7.4 = 48100 rpm
        assertEquals(48100.0, result.motorRpm, delta)
    }

    @Test
    fun `rpm_ホイール RPM はモーター RPM 割る FDR`() {
        val input = GearCalculationInput(
            pinion = 22, spur = 84, internalRatio = 2.6,
            kv = 6500, cells = 2, tireMm = 63
        )
        val result = GearCalculator.calculate(input)
        // primary = 84/22 = 3.8181...
        // fdr     = 3.8181... × 2.6 = 9.9272...
        // wheelRpm = 48100 / 9.9272... ≒ 4845.4
        val expectedFdr = (84.0 / 22.0) * 2.6
        assertEquals(48100.0 / expectedFdr, result.wheelRpm, delta)
    }

    // ===============================================================
    // 最高速度計算
    // ===============================================================

    @Test
    fun `speed_Web 版デフォルト値で最高速度が約 57 dot 5 kmh`() {
        // index.html のスライダー初期値:
        //   pinion=22, spur=84, internal=2.6（TT-02デフォルト）,
        //   kv=6500, cells=2, tire=63mm
        val input = GearCalculationInput(
            pinion = 22, spur = 84, internalRatio = 2.6,
            kv = 6500, cells = 2, tireMm = 63
        )

        val result = GearCalculator.calculate(input)

        // 手計算:
        //   fdr      = (84/22) × 2.6        = 9.92727...
        //   voltage  = 2 × 3.7              = 7.4
        //   motorRpm = 6500 × 7.4           = 48100
        //   wheelRpm = 48100 / 9.92727...   ≒ 4845.4
        //   speed    = π × 0.063 × 4845.4 × 60 / 1000 ≒ 57.53 km/h
        assertEquals(57.53, result.topSpeedKmh, 0.05)
    }

    @Test
    fun `speed_mph 換算は kmh 掛ける 0 dot 621371`() {
        val input = GearCalculationInput(
            pinion = 22, spur = 84, internalRatio = 2.6,
            kv = 6500, cells = 2, tireMm = 63
        )
        val result = GearCalculator.calculate(input)
        assertEquals(result.topSpeedKmh * 0.621371, result.topSpeedMph, delta)
    }

    @Test
    fun `speed_タイヤ径が大きいほど最高速度が上がる`() {
        val small = GearCalculator.calculate(
            GearCalculationInput(
                pinion = 22, spur = 84, internalRatio = 2.6,
                kv = 6500, cells = 2, tireMm = 50
            )
        )
        val large = GearCalculator.calculate(
            GearCalculationInput(
                pinion = 22, spur = 84, internalRatio = 2.6,
                kv = 6500, cells = 2, tireMm = 100
            )
        )
        // 他の条件が同じならタイヤ径に正比例（厳密に2倍になる）
        assertEquals(small.topSpeedKmh * 2.0, large.topSpeedKmh, delta)
    }

    @Test
    fun `speed_ピニオンを大きくするとハイギヤで最高速が上がる`() {
        val low = GearCalculator.calculate(
            GearCalculationInput(
                pinion = 20, spur = 84, internalRatio = 2.6,
                kv = 6500, cells = 2, tireMm = 63
            )
        )
        val high = GearCalculator.calculate(
            GearCalculationInput(
                pinion = 30, spur = 84, internalRatio = 2.6,
                kv = 6500, cells = 2, tireMm = 63
            )
        )
        // ピニオン大 → primary 小 → FDR 小 → ホイールRPM 大 → 速い
        assertTrue(
            "Expected high pinion to be faster, but low=${low.topSpeedKmh}, high=${high.topSpeedKmh}",
            high.topSpeedKmh > low.topSpeedKmh
        )
    }

    // ===============================================================
    // セッティング傾向インジケータ
    // ===============================================================

    @Test
    fun `balance_基準 FDR と一致すればゼロ`() {
        // primary=3.5, internal=2.0 → fdr=7.0
        val input = GearCalculationInput(
            pinion = 20, spur = 70, internalRatio = 2.0,
            kv = 6500, cells = 2, tireMm = 63
        )
        val result = GearCalculator.calculate(input, balanceFdr = 7.0)
        assertEquals(0.0, result.balanceIndicatorPct, delta)
    }

    @Test
    fun `balance_FDR が基準より大きければ正の値でトルク寄り`() {
        // fdr=10.0 (= baseline+3.0) で振り切り +100
        val input = GearCalculationInput(
            pinion = 20, spur = 100, internalRatio = 2.0,
            kv = 6500, cells = 2, tireMm = 63
        )
        val result = GearCalculator.calculate(input, balanceFdr = 7.0)
        assertEquals(100.0, result.balanceIndicatorPct, delta)
    }

    @Test
    fun `balance_FDR が基準より小さければ負の値で最高速寄り`() {
        // fdr=4.0 (= baseline-3.0) で振り切り -100
        val input = GearCalculationInput(
            pinion = 40, spur = 80, internalRatio = 2.0,
            kv = 6500, cells = 2, tireMm = 63
        )
        val result = GearCalculator.calculate(input, balanceFdr = 7.0)
        assertEquals(-100.0, result.balanceIndicatorPct, delta)
    }

    @Test
    fun `balance_振り切り以上の差でもプラスマイナス 100 にクランプ`() {
        // fdr が極端に大きいケース
        val input = GearCalculationInput(
            pinion = 14, spur = 120, internalRatio = 4.45,  // TL-01
            kv = 6500, cells = 2, tireMm = 63
        )
        val result = GearCalculator.calculate(input, balanceFdr = 7.0)
        // fdr = 120/14 × 4.45 ≒ 38.1 → かなり振り切り
        assertEquals(100.0, result.balanceIndicatorPct, delta)
    }

    @Test
    fun `balance_基準 FDR を変更すると中央点がずれる`() {
        // 同じ fdr=8.0 でも baseline によって符号が変わる
        val input = GearCalculationInput(
            pinion = 20, spur = 80, internalRatio = 2.0,  // fdr=8.0
            kv = 6500, cells = 2, tireMm = 63
        )
        val higher = GearCalculator.calculate(input, balanceFdr = 7.0)
        val lower = GearCalculator.calculate(input, balanceFdr = 9.0)
        // baseline=7.0 なら +33.3%（トルク寄り）
        assertEquals((8.0 - 7.0) / 3.0 * 100.0, higher.balanceIndicatorPct, delta)
        // baseline=9.0 なら -33.3%（最高速寄り）
        assertEquals((8.0 - 9.0) / 3.0 * 100.0, lower.balanceIndicatorPct, delta)
    }

    // ===============================================================
    // 入力バリデーション
    // ===============================================================

    @Test
    fun `validation_ピニオンが下限未満なら IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            GearCalculationInput(
                pinion = 13,  // MIN_PINION=14 未満
                spur = 84, internalRatio = 2.6,
                kv = 6500, cells = 2, tireMm = 63
            )
        }
    }

    @Test
    fun `validation_セル数が範囲外なら IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            GearCalculationInput(
                pinion = 22, spur = 84, internalRatio = 2.6,
                kv = 6500,
                cells = 5,  // MAX_CELLS=4 を超える
                tireMm = 63
            )
        }
    }

    @Test
    fun `validation_内部減速比がゼロ以下なら IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            GearCalculationInput(
                pinion = 22, spur = 84,
                internalRatio = 0.0,  // ゼロ除算防止
                kv = 6500, cells = 2, tireMm = 63
            )
        }
    }
}