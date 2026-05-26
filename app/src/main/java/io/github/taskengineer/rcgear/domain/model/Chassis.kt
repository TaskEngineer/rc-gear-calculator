package io.github.taskengineer.rcgear.domain.model

/**
 * シャーシ1台分のドメインモデル。
 * JSON由来の値と Room の上書き値を合成した結果を表す（Step 5 で合成ロジック実装）。
 *
 * @property id             グローバル一意キー。例: "tamiya_tt02"。将来も変更しない。
 * @property name           表示名。例: "TT-02"
 * @property internalRatio  内部減速比（JSON値 or 上書き値）
 * @property defaultTireMm  デフォルトのタイヤ径 [mm]
 * @property note           備考。キット標準の歯数など
 * @property isUserEdited   ユーザーが上書きしている場合 true。UI でバッジ表示等に使う
 */
data class Chassis(
    val id: String,
    val name: String,
    val internalRatio: Double,
    val defaultTireMm: Int,
    val note: String? = null,
    val isUserEdited: Boolean = false
)