package io.github.taskengineer.rcgear.data.local.room.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 「車」1 台のテーブル定義（M-3）。
 *
 * セッティングシート化で入った新しい中心概念。同じシャーシを 2 台持つ
 * （「TA08 #1」「TA08 #2」）ことが普通にあるので、シャーシとは別の実体として持つ。
 *
 * **主キーは UUID の文字列。** 自動採番の Long ではないのは、後から UUID に
 * 変えるのが全テーブルの作り直しになるため（同期を設計するのは先の話だが、
 * 主キーの形だけは今決めておく — ROADMAP「やらないこと」参照）。
 * 一方 tombstone（`deletedAt`）は入れない。全クエリに条件が付く割に、
 * 競合解決のルールが無い状態では意味がないため。
 *
 * @property name       表示名。**UNIQUE にしない**（車ごとに好きな名前を付けられる）
 * @property chassisId  `chassis-db.json` の id、または `user_chassis.id`
 * @property isArchived 一覧から隠すフラグ。削除ではないので値は残る
 */
@Entity(
    tableName = "cars",
    indices = [Index("chassisId"), Index("updatedAt")]
)
data class CarEntity(
    @PrimaryKey val id: String,
    val name: String,
    val chassisId: String,
    val note: String?,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
