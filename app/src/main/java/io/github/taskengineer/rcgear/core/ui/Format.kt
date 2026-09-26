package io.github.taskengineer.rcgear.core.ui

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

// ----- Float 版 -----
// Compose のアニメーション（animateFloatAsState）は Float を返すため、
// 表示直前に toDouble() を挟まずに済むようオーバーロードを用意する。

/** 速度。小数 1 桁（例: 57.5） */
fun Float.formatSpeed(): String = this.toDouble().formatSpeed()
