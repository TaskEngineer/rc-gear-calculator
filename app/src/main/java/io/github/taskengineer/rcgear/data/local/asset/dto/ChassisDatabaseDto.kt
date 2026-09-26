package io.github.taskengineer.rcgear.data.local.asset.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * chassis-db.json のルート構造（M-7 で v2 へ）。
 *
 * ```
 * {
 *   "schemaVersion": 2,
 *   "sources": ["https://...", "各メーカー公式マニュアル"],
 *   "chassis": [
 *     { "id": "tamiya_tt02", "maker": "タミヤ", "name": "TT-02",
 *       "category": "touring", "drive": "shaft_4wd",
 *       "internalRatio": 2.6, "defaultTireMm": 63, "note": "..." }
 *   ]
 * }
 * ```
 *
 * v1 は `"makers": { "タミヤ": [...] }` というメーカー名キーのマップだった。
 * フラット配列にしたのは次の 2 つのため:
 * - `id` での検索が **O(1)** になる（v1 はメーカーを総なめする入れ子走査だった）
 * - エントリ自身が `maker` / `category` / `drive` を持てる
 *
 * **既存 45 件の `id` は 1 つも変えていない。** 保存データ・上書き・エクスポート JSON の
 * 外部キーなので、改名も削除もしない（AGENTS.md §4）。
 * 一覧から外したいときは `category` で絞る。
 */
@Serializable
data class ChassisDatabaseDto(
    @SerialName("schemaVersion") val schemaVersion: Int,

    /** 出典URL・文献リスト。CONFIG > ABOUT に出す予定（ROADMAP P-1） */
    @SerialName("sources") val sources: List<String> = emptyList(),

    @SerialName("chassis") val chassis: List<ChassisDto> = emptyList()
)
