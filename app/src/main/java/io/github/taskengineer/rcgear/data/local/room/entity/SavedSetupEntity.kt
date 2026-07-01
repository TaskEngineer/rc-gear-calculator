package io.github.taskengineer.rcgear.data.local.room.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 保存セッティングのテーブル定義。
 *
 * name にユニークインデックスを張り、同名セッティングの重複保存を DB レベルでも防ぐ
 * （アプリレベルのバリデーションは SaveSetupUseCase で行う予定）。
 *
 * @property internalRatioSnapshot 保存時点の内部減速比を「凍結」した値。
 *   後からユーザーがシャーシDBの値を上書きしても、この保存セッティングは影響を受けない。
 *   読込時に現在値と比較して差分を表示する（PLAN 9.5 スナップショット差分表示）。
 */
@Entity(
    tableName = "saved_setups",
    indices = [Index(value = ["name"], unique = true)]
)
data class SavedSetupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
