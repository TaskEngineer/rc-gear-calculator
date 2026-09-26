package io.github.taskengineer.rcgear.domain.model

/**
 * メーカー単位のグルーピング。
 * 「タミヤ → TT-02, TA-08, ...」のように、メーカー名と所属シャーシのリストを持つ。
 *
 * @property name     メーカー名（日本語表示）。例: "タミヤ"
 * @property chassis  そのメーカーのシャーシ一覧
 */
data class Maker(
    val name: String,
    val chassis: List<Chassis>
)