package io.github.taskengineer.rcgear.data.local.file.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * エクスポートJSONのルート構造（PLAN 2.1.1「JSONによる全データのエクスポート / インポート」）。
 *
 * ユーザーが作成したデータを持ち出す。端末ローカルな表示設定（テーマ等）は含めない。
 *
 * ### schemaVersion 2（M-6。セッティングシート化）
 * - 追加: [cars] / [sheets]
 * - 廃止: [setups]（v1 の保存セッティング）。**読み込みは永久に残す**（AGENTS.md §4）。
 *   v1 ファイルは「1 セッティング = 1 台 + 1 シート」に変換して取り込む
 * - [cars] / [sheets] は **UUID を出力する**。v1 は Room の自動採番 id が
 *   端末固有だったので落としていたが、UUID は端末固有ではない。出力すると
 *   取り込みが id による upsert になり、**書き出し / 読み込みが冪等**になる
 *   （v1 の「同名スキップ」という奇妙な挙動と、リネームで往復不能になる問題が両方消える）
 *
 * ユーザー定義シャーシ（`user_chassis`）はまだ出力しない。テーブルは M-3 で作ったが
 * 使うのは F-5（Phase 3）なので、その時に `userChassis` キーを**追加**する。
 * 読み込み側は知らないキーを無視するので、追加に版上げは要らない。
 *
 * **`schemaVersion` の既定値を [CURRENT_SCHEMA_VERSION] に連動させてはいけない**
 * （Phase 0 レビュー）。既定値はデコード時に「キーが無かった場合の値」として働く。
 * S-6 より前に書き出した実ファイルには `schemaVersion` キーが無いので、
 * ここを CURRENT に連動させると v2 を出した瞬間に旧ファイルが v2 と誤認され、
 * v2 のリーダーに渡って壊れる。**版の省略は永久に v1** と判定する。
 * 書き出し側は [io.github.taskengineer.rcgear.data.local.file.JsonBackupCodec] が
 * `schemaVersion` を明示的に渡す。
 */
@Serializable
data class ExportDataDto(
    @SerialName("schemaVersion") val schemaVersion: Int = OMITTED_SCHEMA_VERSION,
    @SerialName("exportedAt") val exportedAt: Long,
    @SerialName("setupSchema") val setupSchema: ExportedSchemaRefDto? = null,
    @SerialName("cars") val cars: List<ExportedCarDto> = emptyList(),
    @SerialName("sheets") val sheets: List<ExportedSheetDto> = emptyList(),
    @SerialName("overrides") val overrides: List<ExportedOverrideDto> = emptyList(),
    /** v1 の保存セッティング。書き出しでは常に空で、読み込み時だけ中身が入る */
    @SerialName("setups") val setups: List<ExportedSetupDto> = emptyList()
) {
    companion object {
        /** 書き出す版。フォーマットを変えるときに上げる */
        const val CURRENT_SCHEMA_VERSION = 2

        /**
         * `schemaVersion` キーが無いファイルの版（S-6 以前の実形式）。
         * 過去の事実なので **この値は二度と変わらない**。
         */
        const val OMITTED_SCHEMA_VERSION = 1

        /** v1: 保存セッティング + シャーシ上書きだけを持つ形式 */
        const val SCHEMA_VERSION_V1 = 1
    }
}

/** どのセッティングシート定義で書かれたか。互換判定ではなく記録として持つ */
@Serializable
data class ExportedSchemaRefDto(
    @SerialName("schemaId") val schemaId: String,
    @SerialName("revision") val revision: Int
)

/** 車 1 台分。id は UUID なので端末をまたいでも意味を保つ */
@Serializable
data class ExportedCarDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("chassisId") val chassisId: String,
    @SerialName("note") val note: String? = null,
    @SerialName("isArchived") val isArchived: Boolean = false,
    @SerialName("createdAt") val createdAt: Long,
    @SerialName("updatedAt") val updatedAt: Long
)

/**
 * セッティングシート 1 枚分。
 *
 * **[values] は配列ではなくオブジェクト**（fieldKey → 生の JSON スカラ）。
 * EAV の行は内部表現であってワイヤ形式ではない。オブジェクトにすると
 * 人が読める / git で diff できる / サイズが半分になる。
 * 型は取り込み時にレジストリから復元し、レジストリに無いキーは
 * number → 数値 / string → 文字列 として**捨てずに保全する**。
 */
@Serializable
data class ExportedSheetDto(
    @SerialName("id") val id: String,
    @SerialName("carId") val carId: String,
    @SerialName("name") val name: String,
    @SerialName("baselineId") val baselineId: String? = null,
    @SerialName("sessionDate") val sessionDate: Long? = null,
    @SerialName("trackName") val trackName: String? = null,
    @SerialName("surface") val surface: String? = null,
    @SerialName("airTempC") val airTempC: Double? = null,
    @SerialName("trackTempC") val trackTempC: Double? = null,
    @SerialName("humidityPct") val humidityPct: Int? = null,
    @SerialName("bestLapMs") val bestLapMs: Int? = null,
    @SerialName("note") val note: String? = null,
    @SerialName("isFavorite") val isFavorite: Boolean = false,
    @SerialName("schemaId") val schemaId: String,
    @SerialName("values") val values: JsonObject = JsonObject(emptyMap()),
    @SerialName("createdAt") val createdAt: Long,
    @SerialName("updatedAt") val updatedAt: Long
)

/**
 * v1 の保存セッティング 1 件分。**読み込み専用。**
 * Room の id は端末固有だったので出力していない（だから往復が冪等でなかった）。
 */
@Serializable
data class ExportedSetupDto(
    @SerialName("name") val name: String,
    @SerialName("chassisId") val chassisId: String,
    @SerialName("pinion") val pinion: Int,
    @SerialName("spur") val spur: Int,
    @SerialName("internalRatioSnapshot") val internalRatioSnapshot: Double,
    @SerialName("kv") val kv: Int,
    @SerialName("cells") val cells: Int,
    @SerialName("tireMm") val tireMm: Int,
    @SerialName("createdAt") val createdAt: Long,
    @SerialName("updatedAt") val updatedAt: Long
)

/** シャーシ上書き1件分（差分のみ、null = 上書きなし） */
@Serializable
data class ExportedOverrideDto(
    @SerialName("chassisId") val chassisId: String,
    @SerialName("internalRatio") val internalRatio: Double? = null,
    @SerialName("defaultTireMm") val defaultTireMm: Int? = null,
    @SerialName("note") val note: String? = null,
    @SerialName("updatedAt") val updatedAt: Long
)
