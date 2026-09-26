package io.github.taskengineer.rcgear.domain.common

/**
 * 「今」を取る窓口（REF-3 / S-6）。
 *
 * `System.currentTimeMillis()` を直接呼ぶと、createdAt / updatedAt を検証する
 * テストが「実行した瞬間の時刻」に依存して書けなくなる（実際 SetupRepository の
 * 保存テストが書けずにいた）。注入にしておけば、テストでは固定時刻を差し込み、
 * 「更新すると updatedAt だけが進む」といった時間そのものが主題の振る舞いを
 * 素直にアサートできる。
 */
fun interface TimeProvider {
    /** エポックミリ秒 */
    fun now(): Long
}
