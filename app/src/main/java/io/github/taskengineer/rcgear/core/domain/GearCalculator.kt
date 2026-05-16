package io.github.taskengineer.rcgear.core.domain

import io.github.taskengineer.rcgear.domain.model.GearCalculationInput
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput.Companion.LIPO_CELL_VOLTAGE
import io.github.taskengineer.rcgear.domain.model.GearCalculationResult
import kotlin.math.PI

/**
 * ギア比・最高速度の計算ロジック。
 *
 * Web 版 index.html の `update()` 関数（L661 付近）を Kotlin の純粋関数として移植。
 * 副作用なし・状態なし（object シングルトン）なので、ViewModel から自由に呼び出せる。
 *
 * 計算式の出典:
 *   - 周速[m/min] = π × タイヤ径[m] × ホイールRPM
 *   - km/h 換算: 周速[m/min] × 60 / 1000
 *   - mph 換算定数: 0.621371 (Web 版と同一)
 */
object GearCalculator {

    /** km/h → mph 変換係数。Web 版 index.html L686 と同じ値 */
    private const val KMH_TO_MPH = 0.621371

    /**
     * セッティング傾向バーで「振り切り」と見なす FDR 差。
     * Web 版 index.html L696 の `const range = 3.0` に対応。
     * 例: balanceFdr=7.0 のとき、FDR=10.0 で +100%、FDR=4.0 で -100%。
     */
    private const val BALANCE_RANGE = 3.0

    /**
     * ギア比計算の入口。入力から全メトリックを算出して結果を返す。
     *
     * @param input     計算に必要な入力値一式
     * @param balanceFdr セッティング傾向バーの基準 FDR。デフォルト 7.0 は
     *                   Web 版の `const neutralFdr = 7.0` (L695) に合わせている。
     *                   ユーザー設定（DataStore）から渡せるよう引数として外出し。
     * @return 計算結果。表示用の丸めは行わず、生の Double を返す。
     */
    fun calculate(
        input: GearCalculationInput,
        balanceFdr: Double = DEFAULT_BALANCE_FDR
    ): GearCalculationResult {
        // ---- ギア比 ----
        // 1次減速比 = スパー ÷ ピニオン
        // (pinion >= 14 が GearCalculationInput の init で保証されているのでゼロ除算しない)
        val primary = input.spur.toDouble() / input.pinion.toDouble()

        // 最終減速比 (FDR) = 1次 × 内部減速
        val fdr = primary * input.internalRatio

        // ---- 回転数 ----
        // LiPo 公称電圧で計算。KV × V = 無負荷回転数[rpm]
        val voltage = input.cells * LIPO_CELL_VOLTAGE
        val motorRpm = input.kv * voltage
        val wheelRpm = motorRpm / fdr

        // ---- 最高速度 ----
        // タイヤ径[mm]を[m]に変換してから周速計算。
        // π × D[m] × rpm = 周速[m/min] → × 60 ÷ 1000 で km/h
        val tireDiameterM = input.tireMm / 1000.0
        val speedKmh = PI * tireDiameterM * wheelRpm * 60.0 / 1000.0
        val speedMph = speedKmh * KMH_TO_MPH

        // ---- セッティング傾向 ----
        val balancePct = calculateBalanceIndicator(fdr, balanceFdr)

        return GearCalculationResult(
            primaryRatio = primary,
            finalDriveRatio = fdr,
            voltage = voltage,
            motorRpm = motorRpm,
            wheelRpm = wheelRpm,
            topSpeedKmh = speedKmh,
            topSpeedMph = speedMph,
            balanceIndicatorPct = balancePct
        )
    }

    /**
     * セッティング傾向インジケータの計算。
     *
     * Web 版 index.html L694-699 のロジックをそのまま移植:
     *   offset = fdr - neutralFdr
     *   pct = clamp(offset / range, -1, 1) * 50
     *
     * ただし Web 版では描画都合で `* 50`（バー全幅 100% の片側 50%）にしているが、
     * ドメイン層では「-100 〜 +100 のパーセンテージ」として表現するほうが
     * 自然なため `* 100` に変更している。UI 層で「振り切りの片側 = 50%」と
     * 描画する際は値を 2 で割って使う。
     *
     * @return -100.0（最高速側に振り切り）〜 +100.0（トルク側に振り切り）。
     *         0.0 が基準 FDR と一致する中央点。
     */
    private fun calculateBalanceIndicator(fdr: Double, balanceFdr: Double): Double {
        val offset = fdr - balanceFdr
        // -1.0 〜 +1.0 にクランプしてから 100 倍
        val normalized = (offset / BALANCE_RANGE).coerceIn(-1.0, 1.0)
        return normalized * 100.0
    }

    /** 基準 FDR のデフォルト値。Web 版の `neutralFdr = 7.0` と同じ */
    const val DEFAULT_BALANCE_FDR = 7.0
}