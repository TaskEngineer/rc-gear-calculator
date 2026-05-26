package io.github.taskengineer.rcgear.data.local.asset.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * chassis-db.json のルート構造。
 *
 * {
 *   "schemaVersion": 1,
 *   "sources": ["https://...", "各メーカー公式マニュアル"],
 *   "makers": {
 *     "タミヤ": [ { "id": "tamiya_tt02", "name": "TT-02", ... } ],
 *     "ヨコモ": [ ... ]
 *   }
 * }
 */
@Serializable
data class ChassisDatabaseDto(
    @SerialName("schemaVersion") val schemaVersion: Int,

    // 出典URL・文献リスト。MVPでは未使用だが、Phase 2 で「データ出典」画面に出す予定
    @SerialName("sources") val sources: List<String> = emptyList(),

    // メーカー名をキーにしたマップ。JSON のオブジェクト構造をそのまま受ける。
    // kotlinx.serialization 1.7 系はデフォルトで挿入順を保持する LinkedHashMap を使う。
    @SerialName("makers") val makers: Map<String, List<ChassisDto>>
)