package io.github.taskengineer.rcgear.data.local.file.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * エクスポートJSONのルート構造（PLAN 2.1.1「JSONによる全データのエクスポート / インポート」）。
 *
 * ユーザーが作成したデータ（保存セッティング + シャーシ上書き）を持ち出す。
 * 端末ローカルな表示設定（テーマ等）は含めない。
 *
 * schemaVersion はインポート時の互換判定に使う。
 * 将来フォーマットを変えるときはこの値を上げ、旧バージョンの読み込み処理を残す。
 *
 * **`schemaVersion` の既定値を [CURRENT_SCHEMA_VERSION] に連動させてはいけない**
 * （Phase 0 レビュー）。既定値はデコード時に「キーが無かった場合の値」として働く。
 * S-6 より前に書き出した実ファイルには `schemaVersion` キーが無いので、
 * ここを CURRENT に連動させると v2 を出した瞬間に旧ファイルが v2 と誤認され、
 * v2 のリーダーに渡って壊れる。**版の省略は永久に v1** と判定する。
 * 書き出し側は [JsonBackupCodec] が `schemaVersion` を明示的に渡す。
 */
@Serializable
data class ExportDataDto(
    @SerialName("schemaVersion") val schemaVersion: Int = OMITTED_SCHEMA_VERSION,
    @SerialName("exportedAt") val exportedAt: Long,
    @SerialName("setups") val setups: List<ExportedSetupDto> = emptyList(),
    @SerialName("overrides") val overrides: List<ExportedOverrideDto> = emptyList()
) {
    companion object {
        /** 書き出す版。フォーマットを変えるときに上げる */
        const val CURRENT_SCHEMA_VERSION = 1

        /**
         * `schemaVersion` キーが無いファイルの版（S-6 以前の実形式）。
         * 過去の事実なので **この値は二度と変わらない**。
         */
        const val OMITTED_SCHEMA_VERSION = 1
    }
}

/** 保存セッティング1件分。Room の id は端末固有なので出力しない */
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
