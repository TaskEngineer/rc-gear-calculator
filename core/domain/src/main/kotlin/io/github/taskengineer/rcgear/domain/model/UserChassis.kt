package io.github.taskengineer.rcgear.domain.model

/**
 * ユーザーが自分で定義したシャーシ（F-5）。
 *
 * 同梱の `chassis-db.json` は読み取り専用で、そこに無い車種を登録するための受け皿。
 * `chassis_overrides`（同梱エントリの一部を差し替える差分）とは別の概念で、
 * **こちらはエントリそのものを足す**。
 *
 * ### id の規約
 * **`user_` で始める。** 同梱 DB の id（`tamiya_tt02` 等）と衝突させないためで、
 * インポート側もこの接頭辞で「同梱 DB に無くても弾かない車」を見分けている
 * （`ImportDataUseCase.isKnownChassis`）。id は採番されたら変えない —
 * 車（`cars.chassisId`）とエクスポート JSON の外部キーになる。
 */
data class UserChassis(
    val id: String,
    val makerName: String,
    val name: String,
    val internalRatio: Double,
    val defaultTireMm: Int,
    val category: ChassisCategory = ChassisCategory.TOURING,
    val traits: ChassisTraits = ChassisTraits.UNKNOWN,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long
) {

    /**
     * 一覧・計算で使う [Chassis] に変換する。
     *
     * `isUserEdited` は付けない。あれは「同梱エントリを上書きしている」印で、
     * 自分で作ったエントリに出すと意味が二重になる（「編集済」なのか「自作」なのか）。
     * 自作であることは [Chassis.id] の接頭辞で分かる。
     */
    fun toChassis(): Chassis = Chassis(
        id = id,
        name = name,
        internalRatio = internalRatio,
        defaultTireMm = defaultTireMm,
        makerName = makerName,
        category = category,
        traits = traits,
        note = note
    )

    companion object {
        /** ユーザー定義シャーシの id 接頭辞。同梱 DB と衝突させないための規約 */
        const val ID_PREFIX = "user_"

        /** その id がユーザー定義か。接頭辞だけで判定できるのがこの規約の利点 */
        fun isUserDefined(chassisId: String): Boolean = chassisId.startsWith(ID_PREFIX)
    }
}
