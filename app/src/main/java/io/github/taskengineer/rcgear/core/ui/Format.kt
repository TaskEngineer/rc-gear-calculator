package io.github.taskengineer.rcgear.core.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

/**
 * 数値の表示整形（REF-6）。
 *
 * ドメイン層（[io.github.taskengineer.rcgear.domain.calculator.GearCalculator]）は
 * 丸めを行わず生の Double を返す。桁数をどう見せるかは表示の都合なので、
 * ここに集約する。同じ値が画面ごとに違う桁数で出るのを防ぐのが目的。
 *
 * **Locale は必ず [Locale.US] で固定する。** 端末ロケールに任せると、
 * 小数点がカンマになる地域（ドイツ・フランス等）で "2,60" と表示され、
 * 桁区切りのカンマと区別が付かなくなる。
 *
 * 単位（km/h, V, T, mm 等）は付けない。文言は S-11 で strings.xml に移すため、
 * ここは数値部分だけを返し、単位の連結は呼び出し側に任せる。
 */

/** 減速比。小数 2 桁（例: 2.60 / 8.27） */
fun Double.formatRatio(): String = String.format(Locale.US, "%.2f", this)

/** 速度。小数 1 桁（例: 57.5） */
fun Double.formatSpeed(): String = String.format(Locale.US, "%.1f", this)

/** 電圧。小数 1 桁（例: 7.4） */
fun Double.formatVoltage(): String = String.format(Locale.US, "%.1f", this)

/** 回転数。整数に丸めて 3 桁区切り（例: 48,100） */
fun Double.formatRpm(): String = String.format(Locale.US, "%,d", this.roundToLong())

/**
 * 小数桁を指定して整形する（G-3）。
 *
 * 桁数はセッティングシートの項目定義（`NumberFieldDef.decimals`）が持っている。
 * 「キャンバーは 0.1 刻みだから 1 桁」という判断はレジストリ側にあり、
 * ここは渡された桁数で書くだけ。
 */
fun Double.formatDecimals(decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f", this)

/**
 * ラップタイム[ms] を「12.345」/「1:02.345」に整形する（G-3）。
 *
 * 1 分未満は分を出さない。ツーリングのラップは十数秒なので、
 * 常に "0:12.345" と書くと先頭の 0 が読む邪魔になる。
 */
fun Int.formatLapTime(): String {
    val minutes = this / 60_000
    val seconds = (this % 60_000) / 1000.0
    return if (minutes > 0) {
        String.format(Locale.US, "%d:%06.3f", minutes, seconds)
    } else {
        String.format(Locale.US, "%.3f", seconds)
    }
}

// ----- 日付 -----

/**
 * epoch millis を「2026/09/28」に整形する（G-2）。
 *
 * **ここだけ [Locale.US] に固定しない。** 小数点と違い、日付は端末のロケール・
 * タイムゾーンで読むのが自然だから。並びを `yyyy/MM/dd` に固定しているのは、
 * シート一覧で縦に並んだときに桁が揃うようにするため
 * （`DateFormat.getDateInstance()` はロケールで桁数が変わる）。
 */
fun Long.formatDate(): String =
    SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(this))

// ----- Float 版 -----
// Compose のアニメーション（animateFloatAsState）は Float を返すため、
// 表示直前に toDouble() を挟まずに済むようオーバーロードを用意する。

/** 速度。小数 1 桁（例: 57.5） */
fun Float.formatSpeed(): String = this.toDouble().formatSpeed()
