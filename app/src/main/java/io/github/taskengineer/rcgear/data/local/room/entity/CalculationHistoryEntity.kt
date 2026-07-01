package io.github.taskengineer.rcgear.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 計算履歴のテーブル定義。
 *
 * MVP ではテーブル定義 + Insert のみ実装し、閲覧UIは Phase 2 で追加する（PLAN 2.1.3）。
 * Phase 2 の「頻繁に使うシャーシのランキング表示」等に備えて chassisId を持つ。
 */
@Entity(tableName = "calculation_history")
data class CalculationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chassisId: String,
    val pinion: Int,
    val spur: Int,
    val topSpeedKmh: Double,
    val createdAt: Long
)
