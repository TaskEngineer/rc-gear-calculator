package io.github.taskengineer.rcgear.domain.schema

import io.github.taskengineer.rcgear.domain.model.ChassisTraits

/**
 * ツーリング（オンロード）用セッティングシートの項目定義。**単一の真実**（M-1）。
 *
 * ここと `:app` の `strings.xml` の 2 ファイルを足すだけで項目が増える、という状態を保つこと。
 * Composable を書き足さないと項目が増えないなら、レジストリ方式が壊れている合図になる。
 *
 * ### 第 1 スライス
 * 計画（ROADMAP Phase 2 / M-1）に従い、まずは **ギア + 前後ダンパー + 主要サスペンション**
 * だけを定義する。駆動系・タイヤ・ESC・車体・走行結果は Phase 3 の G-7 で横展開する。
 * その時点で UI コードが 1 行も増えないことが、この設計が機能していることの証明になる。
 *
 * ### 範囲値の出どころ
 * ギアセクションの範囲は `GearCalculationInput` の定数（Web 版と同一）と一致させる。
 * 二重定義に見えるが、`GearCalculationInput` は bag を知らない純粋な計算入力であり続ける
 * ので参照はしない。一致は `SchemaTest` が検証する。
 */
object TouringSetupSchema {

    /** `setup_sheets.schemaId` に保存する識別子。バギー用が増えたらここが分岐点になる */
    const val SCHEMA_ID = "touring"

    /**
     * 項目を足した / 範囲を変えたときに上げる版数。エクスポート JSON に出す。
     * 読み込み側は「知らないキーは捨てずに保全する」ので、版数は互換性の判定ではなく
     * 「どの版で書かれたか」の記録として使う。
     */
    const val REVISION = 1

    // ----- 選択肢 -----

    /**
     * ダンパーオイルの単位。**wt と cSt は自動換算しない。**
     * 銘柄によって対応が一致しないので、換算すると嘘の値になる（計画 §4.2）。
     */
    val DAMPER_OIL_UNIT = ChoiceSet.of("damperOilUnit", "wt", "cst")

    /**
     * スプリングの硬さ。メーカーごとに色の対応が違うため、色ではなく硬さの段階で持つ。
     * 銘柄そのものは（必要になったら）別の自由入力項目で足す。
     */
    val SPRING_RATE = ChoiceSet.of(
        "springRate",
        "ultraSoft", "soft", "mediumSoft", "medium", "mediumHard", "hard", "ultraHard"
    )

    /** ダンパー上側の取り付け位置。内側から数えた穴の番号 */
    val DAMPER_MOUNT = ChoiceSet.of("damperMount", "pos1", "pos2", "pos3", "pos4", "pos5")

    // ----- セクション -----

    /**
     * ギア。CALC 画面と同じ値をシートの 1 セクションとして持つ。
     *
     * 実カラムに分けずに bag へ入れるのは、分けると汎用 diff が
     * 「一番見たい差分（ギア）だけ黙ってスキップする」ことになるため。
     * `GearCalculator` との接続点は `SetupValues.toGearInput()`（M-2）1 本だけ。
     */
    val GEAR = SectionDef(
        key = "gear",
        fields = listOf(
            NumberFieldDef(
                key = "pinion",
                ui = FieldUi.SLIDER,
                min = 14.0, max = 40.0, step = 1.0,
                unit = FieldUnit.TEETH,
                defaultValue = 22.0
            ),
            NumberFieldDef(
                key = "spur",
                ui = FieldUi.SLIDER,
                min = 60.0, max = 120.0, step = 1.0,
                unit = FieldUnit.TEETH,
                defaultValue = 84.0
            ),
            NumberFieldDef(
                key = "internalRatio",
                ui = FieldUi.NUMBER_FIELD,
                min = 0.5, max = 6.0, step = 0.01, decimals = 2,
                defaultFrom = ChassisDefault.INTERNAL_RATIO
            ),
            NumberFieldDef(
                key = "motorKv",
                ui = FieldUi.SLIDER,
                min = 1500.0, max = 13500.0, step = 100.0,
                unit = FieldUnit.KV,
                defaultValue = 6500.0
            ),
            NumberFieldDef(
                key = "cells",
                ui = FieldUi.STEPPER,
                min = 1.0, max = 4.0, step = 1.0,
                unit = FieldUnit.CELL,
                defaultValue = 2.0
            ),
            // タイヤ径は「計算に使う 1 つの値」としてギアに置く。
            // 実測値を前後で分けて記録したくなったら tire セクション（G-7）に
            // tire.diaFrontMm / tire.diaRearMm を足し、この項目との関係を決める。
            NumberFieldDef(
                key = "tireMm",
                ui = FieldUi.SLIDER,
                min = 40.0, max = 120.0, step = 1.0,
                unit = FieldUnit.MM,
                defaultFrom = ChassisDefault.DEFAULT_TIRE_MM
            )
        )
    )

    /**
     * サスペンション。フロントとリアで有効範囲が実際に違う（キャンバーが典型）ので、
     * 前後を自動展開せず 1 項目ずつ書く。
     */
    val SUSPENSION = SectionDef(
        key = "suspension",
        columns = listOf(ColumnDef.FRONT, ColumnDef.REAR),
        fields = listOf(
            NumberFieldDef(
                key = "front.camberDeg",
                ui = FieldUi.STEPPER,
                min = -5.0, max = 1.0, step = 0.1, decimals = 1,
                unit = FieldUnit.DEGREE
            ),
            NumberFieldDef(
                key = "rear.camberDeg",
                ui = FieldUi.STEPPER,
                min = -3.0, max = 1.0, step = 0.1, decimals = 1,
                unit = FieldUnit.DEGREE
            ),
            NumberFieldDef(
                key = "front.toeDeg",
                ui = FieldUi.STEPPER,
                min = -5.0, max = 5.0, step = 0.1, decimals = 1,
                unit = FieldUnit.DEGREE
            ),
            NumberFieldDef(
                key = "rear.toeDeg",
                ui = FieldUi.STEPPER,
                min = -5.0, max = 5.0, step = 0.1, decimals = 1,
                unit = FieldUnit.DEGREE
            ),
            // キャスターはフロントにしか無い。リア列は空欄で描かれる
            NumberFieldDef(
                key = "front.casterDeg",
                ui = FieldUi.STEPPER,
                min = 0.0, max = 12.0, step = 0.5, decimals = 1,
                unit = FieldUnit.DEGREE
            ),
            NumberFieldDef(
                key = "front.rideHeightMm",
                ui = FieldUi.STEPPER,
                min = 3.0, max = 10.0, step = 0.1, decimals = 1,
                unit = FieldUnit.MM
            ),
            NumberFieldDef(
                key = "rear.rideHeightMm",
                ui = FieldUi.STEPPER,
                min = 3.0, max = 10.0, step = 0.1, decimals = 1,
                unit = FieldUnit.MM
            ),
            NumberFieldDef(
                key = "front.droopMm",
                ui = FieldUi.STEPPER,
                min = 0.0, max = 15.0, step = 0.1, decimals = 1,
                unit = FieldUnit.MM
            ),
            NumberFieldDef(
                key = "rear.droopMm",
                ui = FieldUi.STEPPER,
                min = 0.0, max = 15.0, step = 0.1, decimals = 1,
                unit = FieldUnit.MM
            )
        )
    )

    /**
     * ダンパー。オイルは「数値 + 単位 + 銘柄」の 3 項目に分けて持つ。
     * 1 項目に "400cSt Tamiya" と書かせると差分が文字列比較になってしまうため。
     */
    val DAMPER = SectionDef(
        key = "damper",
        columns = listOf(ColumnDef.FRONT, ColumnDef.REAR),
        fields = listOf(
            NumberFieldDef(
                key = "front.damperOilValue",
                ui = FieldUi.NUMBER_FIELD,
                min = 1.0, max = 2000.0, step = 0.5, decimals = 1
            ),
            ChoiceFieldDef(key = "front.damperOilUnit", choiceSet = DAMPER_OIL_UNIT),
            TextFieldDef(key = "front.damperOilBrand", maxLength = 30),
            NumberFieldDef(
                key = "rear.damperOilValue",
                ui = FieldUi.NUMBER_FIELD,
                min = 1.0, max = 2000.0, step = 0.5, decimals = 1
            ),
            ChoiceFieldDef(key = "rear.damperOilUnit", choiceSet = DAMPER_OIL_UNIT),
            TextFieldDef(key = "rear.damperOilBrand", maxLength = 30),
            NumberFieldDef(
                key = "front.damperPistonHoles",
                ui = FieldUi.STEPPER,
                min = 1.0, max = 6.0, step = 1.0
            ),
            NumberFieldDef(
                key = "rear.damperPistonHoles",
                ui = FieldUi.STEPPER,
                min = 1.0, max = 6.0, step = 1.0
            ),
            NumberFieldDef(
                key = "front.damperPistonDiaMm",
                ui = FieldUi.STEPPER,
                min = 1.0, max = 2.0, step = 0.05, decimals = 2,
                unit = FieldUnit.MM
            ),
            NumberFieldDef(
                key = "rear.damperPistonDiaMm",
                ui = FieldUi.STEPPER,
                min = 1.0, max = 2.0, step = 0.05, decimals = 2,
                unit = FieldUnit.MM
            ),
            ChoiceFieldDef(key = "front.springRate", choiceSet = SPRING_RATE),
            ChoiceFieldDef(key = "rear.springRate", choiceSet = SPRING_RATE),
            NumberFieldDef(
                key = "front.damperLenMm",
                ui = FieldUi.STEPPER,
                min = 40.0, max = 70.0, step = 0.5, decimals = 1,
                unit = FieldUnit.MM
            ),
            NumberFieldDef(
                key = "rear.damperLenMm",
                ui = FieldUi.STEPPER,
                min = 40.0, max = 70.0, step = 0.5, decimals = 1,
                unit = FieldUnit.MM
            ),
            ChoiceFieldDef(key = "front.damperUpperMount", choiceSet = DAMPER_MOUNT),
            ChoiceFieldDef(key = "rear.damperUpperMount", choiceSet = DAMPER_MOUNT)
        )
    )

    /** 表示順そのもの。セクションを足すときはここに並べる */
    val sections: List<SectionDef> = listOf(GEAR, SUSPENSION, DAMPER)

    /** 全項目を宣言順で平坦化したもの */
    val allFields: List<FieldDef> = sections.flatMap { it.fields }

    /** キー → 項目。bag の値を解釈するときの入口。キーは一意（`SchemaTest` が保証） */
    val byKey: Map<String, FieldDef> = allFields.associateBy { it.key }

    /** このレジストリに存在するキーか。エクスポート JSON の未知キー判定に使う */
    fun isKnown(fieldKey: String): Boolean = byKey.containsKey(fieldKey)

    /** 項目が属するセクション。未知キーなら `null` */
    fun sectionOf(fieldKey: String): SectionDef? =
        sections.firstOrNull { section -> section.fields.any { it.key == fieldKey } }

    /**
     * このシャーシで出す項目だけに絞る（M-7）。
     * シャフト車にベルトテンション欄を出さない、といった出し分けの入口。
     */
    fun visibleFields(traits: ChassisTraits): List<FieldDef> =
        allFields.filter { traits.satisfies(it.requires) }

    /** 使われている選択肢集合の一覧。ラベル網羅テスト（`:app`）が参照する */
    val choiceSets: List<ChoiceSet> =
        allFields.filterIsInstance<ChoiceFieldDef>().map { it.choiceSet }.distinctBy { it.id }
}
