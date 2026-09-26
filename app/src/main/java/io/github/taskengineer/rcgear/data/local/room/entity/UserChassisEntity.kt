package io.github.taskengineer.rcgear.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ユーザーが自分で定義したシャーシ（M-3。UI は ROADMAP F-5 で載せる）。
 *
 * 同梱の `chassis-db.json` は読み取り専用で、そこに無い車種を登録するための受け皿。
 * **id は `user_` で始める**ことで同梱 DB の id（`tamiya_tt02` 等）と衝突しない。
 *
 * `chassis_overrides` との違い: あちらは「同梱エントリの一部を差し替える差分」、
 * こちらは「エントリそのものを足す」。合成の仕方が違うので別テーブルにしてある。
 *
 * @property category `chassis-db.json` v2（M-7）と同じ語彙。ツーリング一覧の絞り込みに使う
 * @property drive / hasCenterDiff 項目の出し分け（`FieldDef.requires`）を駆動する
 */
@Entity(tableName = "user_chassis")
data class UserChassisEntity(
    @PrimaryKey val id: String,
    val makerName: String,
    val name: String,
    val internalRatio: Double,
    val defaultTireMm: Int,
    val category: String,
    val drive: String?,
    val hasCenterDiff: Boolean?,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        /** ユーザー定義シャーシの id 接頭辞。同梱 DB と衝突させないための規約 */
        const val ID_PREFIX = "user_"
    }
}
