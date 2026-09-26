package io.github.taskengineer.rcgear.domain.model

import io.github.taskengineer.rcgear.domain.schema.ChassisTrait

/**
 * シャーシ1台分のドメインモデル。
 * JSON由来の値と Room の上書き値を合成した結果を表す（Step 5 で合成ロジック実装）。
 *
 * @property id             グローバル一意キー。例: "tamiya_tt02"。将来も変更しない
 * @property makerName      メーカー名。M-7 で JSON がフラット配列になり、エントリ自身が持つようになった
 * @property name           表示名。例: "TT-02"
 * @property internalRatio  内部減速比（JSON値 or 上書き値）
 * @property defaultTireMm  デフォルトのタイヤ径 [mm]
 * @property category       車種。ツーリング専用アプリなので、一覧の絞り込みに使う（M-7）
 * @property traits         項目の出し分け（`FieldDef.requires`）を駆動する素性
 * @property note           備考。キット標準の歯数など
 * @property isUserEdited   ユーザーが上書きしている場合 true。UI でバッジ表示等に使う
 */
data class Chassis(
    val id: String,
    val name: String,
    val internalRatio: Double,
    val defaultTireMm: Int,
    val makerName: String = "",
    val category: ChassisCategory = ChassisCategory.OTHER,
    val traits: ChassisTraits = ChassisTraits(),
    val note: String? = null,
    val isUserEdited: Boolean = false
)

/**
 * 車種（M-7）。
 *
 * このアプリはツーリング専用だが、同梱 DB にはバギーやドリフトのエントリが
 * 入っている。**id は消せない**（保存データの外部キー。AGENTS.md §4）ので、
 * 一覧から隠したいときはこの分類で絞る。
 */
enum class ChassisCategory {
    TOURING,
    BUGGY,
    DRIFT,

    /** F1・ミニッツ・モンスタートラックなど、上のどれでもないもの */
    OTHER;

    companion object {
        /** JSON の文字列から解釈する。知らない値は [OTHER] にする（読み込みを落とさない） */
        fun fromKey(key: String?): ChassisCategory =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: OTHER
    }
}

/** 駆動方式（M-7）。`FieldDef.requires` の評価に使う */
enum class ChassisDrive {
    SHAFT_4WD,
    BELT_4WD,

    /** ベルトとシャフトの併用 */
    HYBRID_4WD,
    FWD,
    RWD,

    /** ダイレクトドライブ（F1・ミニッツ） */
    DIRECT;

    companion object {
        /** JSON の文字列から解釈する。知らない値は `null`（＝不明）にする */
        fun fromKey(key: String?): ChassisDrive? =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) }
    }
}

/**
 * 項目の出し分けに使うシャーシの素性（M-7）。
 *
 * **不明（null）なら項目を出す。** 同梱 DB にはまだ裏の取れていない項目があり、
 * 「分からないから隠す」と、実際には設定できる欄が無言で消えることになる。
 * 隠すのは「その車には確実に存在しない」と分かっている場合だけにする。
 */
data class ChassisTraits(
    val drive: ChassisDrive? = null,
    val hasCenterDiff: Boolean? = null
) {

    /** [trait] の条件を満たすか。`null`（条件なし）は常に true */
    fun satisfies(trait: ChassisTrait?): Boolean = when (trait) {
        null -> true
        ChassisTrait.HAS_CENTER_DIFF -> hasCenterDiff != false
        ChassisTrait.BELT_DRIVE ->
            drive == null ||
                drive == ChassisDrive.BELT_4WD ||
                drive == ChassisDrive.HYBRID_4WD
    }

    companion object {
        /** 何も分かっていない状態。全ての項目が出る */
        val UNKNOWN = ChassisTraits()
    }
}
