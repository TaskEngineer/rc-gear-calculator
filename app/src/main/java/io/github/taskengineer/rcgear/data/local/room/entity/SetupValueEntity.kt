package io.github.taskengineer.rcgear.data.local.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * セッティングシートの値 1 つ（M-3）。EAV（属性値テーブル）。
 *
 * 40〜80 項目を固定カラムで持つと、1 項目足すたびに Entity / ドメインモデル /
 * 変換 2 本 / Export DTO / Import / Migration と 9 箇所を直すことになる。
 * `(sheetId, fieldKey)` で値をアドレスできる形にして、項目の定義は
 * `:core:domain` の `TouringSetupSchema` に集約した（計画 §4.1）。
 *
 * **ペイロード列は `num` と `text` の 2 本だけ。3 本目は作らない。**
 * 整数・小数・真偽は `num`、選択肢キーと自由入力は `text` に入る。
 * どちらとして読むかはレジストリの型定義が決める（`SetupValueCodec`）。
 *
 * この形が重いと分かったら、シートごとの JSON 列に差し替えてよい。
 * ドメインと UI は `SetupValues` 越しにしか触らないので、変更は Repository 内で閉じる。
 *
 * @property fieldKey 永続キー。`INDEX` があるのは「全シート横断で 1 項目を見る」用
 */
@Entity(
    tableName = "setup_values",
    primaryKeys = ["sheetId", "fieldKey"],
    foreignKeys = [
        ForeignKey(
            entity = SetupSheetEntity::class,
            parentColumns = ["id"],
            childColumns = ["sheetId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("fieldKey")]
)
data class SetupValueEntity(
    val sheetId: String,
    val fieldKey: String,
    val num: Double?,
    val text: String?,
    val updatedAt: Long
)
