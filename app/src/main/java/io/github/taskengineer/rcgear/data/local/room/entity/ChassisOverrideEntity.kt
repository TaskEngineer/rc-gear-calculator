package io.github.taskengineer.rcgear.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * シャーシDBのユーザー上書きテーブル定義。
 *
 * 標準DB（assets/chassis-db.json）はアプリ更新で差し替わる可能性があるため、
 * ユーザーの変更は JSON を直接書き換えるのではなく、このテーブルに「差分」として持つ。
 * ChassisRepository が JSON 値と合成して最終的な Chassis を組み立てる。
 *
 * - 各フィールドは Nullable。null = そのフィールドは上書きしない（標準値を使う）。
 * - 「リセット」はレコードごと削除することで標準値に戻す（PLAN 6.2）。
 *
 * @property chassisId 上書き対象のシャーシID（JSON の id と対応するグローバル一意キー）
 */
@Entity(tableName = "chassis_overrides")
data class ChassisOverrideEntity(
    @PrimaryKey val chassisId: String,
    val internalRatio: Double?,
    val defaultTireMm: Int?,
    val note: String?,
    val updatedAt: Long
)
