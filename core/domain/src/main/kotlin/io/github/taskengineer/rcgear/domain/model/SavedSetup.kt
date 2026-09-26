package io.github.taskengineer.rcgear.domain.model

/**
 * 保存セッティングのドメインモデル。
 *
 * @property internalRatioSnapshot 保存時点で凍結した内部減速比。
 *   詳細画面で現在のシャーシDB値と比較し、差があれば
 *   「保存時 2.60 / 現在 2.70」のように可視化する（PLAN 9.5）。
 * @property createdAt / updatedAt エポックミリ秒
 */
data class SavedSetup(
    val id: Long,
    val name: String,
    val chassisId: String,
    val pinion: Int,
    val spur: Int,
    val internalRatioSnapshot: Double,
    val kv: Int,
    val cells: Int,
    val tireMm: Int,
    val createdAt: Long,
    val updatedAt: Long
)
