package io.github.taskengineer.rcgear.domain.model

/**
 * ギア比計算の出力結果。
 *
 * Web版（index.html）の `update()` 関数で計算・表示されていた値に対応する。
 * 表示時の桁丸めは UI 層（Composable）で行う前提で、ここでは生の値を保持する。
 */
data class GearCalculationResult(
    /** 1次減速比 = スパー ÷ ピニオン。表示桁: 小数2桁 */
    val primaryRatio: Double,

    /** 最終減速比（FDR）= 1次減速比 × 内部減速比。表示桁: 小数2桁 */
    val finalDriveRatio: Double,

    /** バッテリー公称電圧[V] = セル数 × 3.7 */
    val voltage: Double,

    /** モーター無負荷回転数[rpm] = KV × 電圧 */
    val motorRpm: Double,

    /** ホイール回転数[rpm] = モーターRPM ÷ FDR。表示桁: 整数 */
    val wheelRpm: Double,

    /**
     * 理論最高速度[km/h]
     * 計算式: π × タイヤD[m] × ホイールRPM × 60 / 1000
     * 表示桁: 小数1桁
     */
    val topSpeedKmh: Double,

    /** 理論最高速度[mph]。topSpeedKmh × 0.621371。表示桁: 小数1桁 */
    val topSpeedMph: Double,

    /**
     * セッティング傾向インジケータ[-100, +100]。
     *
     *   正の値（+方向）: トルク寄り（加速重視。FDR が基準より大きい）
     *   負の値（-方向）: 最高速寄り（FDR が基準より小さい）
     *   ±100 が描画上の振り切り、0 が中央（基準 FDR と一致）
     *
     * 算出ロジックは Web 版 index.html L694-699 を参照:
     *   offset = fdr - balanceFdr            // 基準 FDR との差
     *   normalized = offset / range          // range は通常 3.0
     *   clamped = clamp(normalized, -1, 1)
     *   pct = clamped * 100
     */
    val balanceIndicatorPct: Double
)