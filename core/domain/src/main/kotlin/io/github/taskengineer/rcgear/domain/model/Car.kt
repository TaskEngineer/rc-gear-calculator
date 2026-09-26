package io.github.taskengineer.rcgear.domain.model

/**
 * 「車」1 台（M-4）。GARAGE タブの主役。
 *
 * シャーシ（`chassis-db.json` のエントリ）とは別の実体にしてある。
 * 同じ TA08 を 2 台持ってそれぞれ別のセッティングを詰める、というのが普通にあるため。
 *
 * @property id        UUID。端末固有ではないので、エクスポート JSON にも出す
 * @property chassisId `chassis-db.json` の id、または `user_<uuid>`
 * @property isArchived 一覧から隠すだけのフラグ。データは残る
 */
data class Car(
    val id: String,
    val name: String,
    val chassisId: String,
    val note: String? = null,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)
