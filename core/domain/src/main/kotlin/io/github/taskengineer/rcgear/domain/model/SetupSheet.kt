package io.github.taskengineer.rcgear.domain.model

import io.github.taskengineer.rcgear.domain.schema.TouringSetupSchema

/**
 * セッティングシート 1 枚のヘッダ（M-4）。
 *
 * **ヘッダと bag の線引き**（計画 §4.3）:
 * > bag（[SetupValues]）＝「車に対して設定した値」。ヘッダ＝「その設定を行った条件」。
 *
 * 気温やトラック名をヘッダに置くのは、汎用 diff の対象を bag だけに保ちたいから。
 * bag に入れると「気温が 3℃ 違います」が「セッティング変更点」として出てしまう。
 *
 * 値は [SetupSheetWithValues] が持つ。一覧画面は値を読まないので分けてある。
 *
 * @property baselineId 「前回のセット」。差分の軸 B の相手。消えたら null になる
 */
data class SetupSheet(
    val id: String,
    val carId: String,
    val name: String,
    val baselineId: String? = null,
    val conditions: SessionConditions = SessionConditions(),
    val note: String? = null,
    val isFavorite: Boolean = false,
    val schemaId: String = TouringSetupSchema.SCHEMA_ID,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * そのセッティングで走ったときの条件。シートのヘッダに並記するだけで、差分には出さない。
 *
 * @property bestLapMs ベストラップ[ms]。走行結果もここに置く（設定ではなく結果なので）
 */
data class SessionConditions(
    val sessionDate: Long? = null,
    val trackName: String? = null,
    val surface: String? = null,
    val airTempC: Double? = null,
    val trackTempC: Double? = null,
    val humidityPct: Int? = null,
    val bestLapMs: Int? = null
)

/** シート 1 枚を丸ごと（ヘッダ + 値）。閲覧・編集・差分・エクスポートで使う */
data class SetupSheetWithValues(
    val sheet: SetupSheet,
    val values: SetupValues
) {
    val id: String get() = sheet.id
}
