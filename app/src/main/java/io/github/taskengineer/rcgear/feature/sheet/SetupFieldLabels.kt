package io.github.taskengineer.rcgear.feature.sheet

import androidx.annotation.StringRes
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.domain.schema.ChoiceFieldDef
import io.github.taskengineer.rcgear.domain.schema.ChoiceOption
import io.github.taskengineer.rcgear.domain.schema.ChoiceSet
import io.github.taskengineer.rcgear.domain.schema.ColumnDef
import io.github.taskengineer.rcgear.domain.schema.FieldDef
import io.github.taskengineer.rcgear.domain.schema.FieldUnit
import io.github.taskengineer.rcgear.domain.schema.GridRow
import io.github.taskengineer.rcgear.domain.schema.SectionDef

/**
 * セッティングシートの項目 → 表示文言の対応表（M-1 / HANDOFF §5.3）。
 *
 * `TouringSetupSchema` は `:core:domain`（純 Kotlin JVM）にあり `R.string` を参照できない。
 * そこで **定義は domain、文言の解決は `:app`** という分担にして、その接点をこの 1 ファイルに集めた。
 * 項目を足してここに足し忘れると `SetupFieldLabelsTest` が落ちる — それがこの分担の担保。
 *
 * 前後で同じ行になる項目（`front.camberDeg` / `rear.camberDeg`）は**同じ文言を共有する**。
 * 列見出し（フロント / リア）が別に出るので、行ラベルに「フロント」を含めると二重になるため。
 */
object SetupFieldLabels {

    /** 文言が見つからないことを表す値。`@StringRes` の 0 は「リソース無し」 */
    const val NO_LABEL = 0

    private val fieldLabels: Map<String, Int> = mapOf(
        // --- ギア（CALC 画面と共有の文言） ---
        "pinion" to R.string.field_pinion,
        "spur" to R.string.field_spur,
        "internalRatio" to R.string.field_internal_ratio,
        "motorKv" to R.string.field_motor_kv,
        "cells" to R.string.field_cells,
        "tireMm" to R.string.field_tire_mm,
        // --- サスペンション ---
        "front.camberDeg" to R.string.field_camber,
        "rear.camberDeg" to R.string.field_camber,
        "front.toeDeg" to R.string.field_toe,
        "rear.toeDeg" to R.string.field_toe,
        "front.casterDeg" to R.string.field_caster,
        "front.rideHeightMm" to R.string.field_ride_height,
        "rear.rideHeightMm" to R.string.field_ride_height,
        "front.droopMm" to R.string.field_droop,
        "rear.droopMm" to R.string.field_droop,
        // --- ダンパー ---
        "front.damperOilValue" to R.string.field_damper_oil_value,
        "rear.damperOilValue" to R.string.field_damper_oil_value,
        "front.damperOilUnit" to R.string.field_damper_oil_unit,
        "rear.damperOilUnit" to R.string.field_damper_oil_unit,
        "front.damperOilBrand" to R.string.field_damper_oil_brand,
        "rear.damperOilBrand" to R.string.field_damper_oil_brand,
        "front.damperPistonHoles" to R.string.field_damper_piston_holes,
        "rear.damperPistonHoles" to R.string.field_damper_piston_holes,
        "front.damperPistonDiaMm" to R.string.field_damper_piston_dia,
        "rear.damperPistonDiaMm" to R.string.field_damper_piston_dia,
        "front.springRate" to R.string.field_spring_rate,
        "rear.springRate" to R.string.field_spring_rate,
        "front.damperLenMm" to R.string.field_damper_len,
        "rear.damperLenMm" to R.string.field_damper_len,
        "front.damperUpperMount" to R.string.field_damper_upper_mount,
        "rear.damperUpperMount" to R.string.field_damper_upper_mount,
        // --- 駆動系（G-7） ---
        "front.diffType" to R.string.field_diff_type,
        "center.diffType" to R.string.field_diff_type,
        "rear.diffType" to R.string.field_diff_type,
        "front.diffOilCst" to R.string.field_diff_oil,
        "center.diffOilCst" to R.string.field_diff_oil,
        "rear.diffOilCst" to R.string.field_diff_oil,
        "front.beltTension" to R.string.field_belt_tension,
        "rear.beltTension" to R.string.field_belt_tension,
        // --- タイヤ（G-7） ---
        "front.tireBrand" to R.string.field_tire_brand,
        "rear.tireBrand" to R.string.field_tire_brand,
        "front.tireCompound" to R.string.field_tire_compound,
        "rear.tireCompound" to R.string.field_tire_compound,
        "front.tireInsert" to R.string.field_tire_insert,
        "rear.tireInsert" to R.string.field_tire_insert,
        "front.tireAdditive" to R.string.field_tire_additive,
        "rear.tireAdditive" to R.string.field_tire_additive,
        "front.tireDiaMm" to R.string.field_tire_dia,
        "rear.tireDiaMm" to R.string.field_tire_dia,
        // --- モーター / ESC（G-7） ---
        "motorTurn" to R.string.field_motor_turn,
        "motorBrand" to R.string.field_motor_brand,
        "motorTimingDeg" to R.string.field_motor_timing,
        "escBrand" to R.string.field_esc_brand,
        "escBoostDeg" to R.string.field_esc_boost,
        "escTurboDeg" to R.string.field_esc_turbo,
        "escPunch" to R.string.field_esc_punch,
        "escDragBrakePct" to R.string.field_esc_drag_brake,
        // --- 車体（G-7） ---
        "bodyName" to R.string.field_body_name,
        "bodyWing" to R.string.field_body_wing,
        "ballastG" to R.string.field_ballast_weight,
        "ballastPosition" to R.string.field_ballast_position,
        // --- 走行結果（G-7） ---
        "feelEntry" to R.string.field_feel_entry,
        "feelMid" to R.string.field_feel_mid,
        "feelExit" to R.string.field_feel_exit,
        "gripFeel" to R.string.field_grip_feel
    )

    private val sectionLabels: Map<String, Int> = mapOf(
        "gear" to R.string.sheet_section_gear,
        "suspension" to R.string.sheet_section_suspension,
        "damper" to R.string.sheet_section_damper,
        "drivetrain" to R.string.sheet_section_drivetrain,
        "tire" to R.string.sheet_section_tire,
        "esc" to R.string.sheet_section_esc,
        "body" to R.string.sheet_section_body,
        "result" to R.string.sheet_section_result
    )

    private val columnLabels: Map<String, Int> = mapOf(
        "front" to R.string.sheet_column_front,
        "center" to R.string.sheet_column_center,
        "rear" to R.string.sheet_column_rear
    )

    private val unitLabels: Map<FieldUnit, Int> = mapOf(
        FieldUnit.NONE to NO_LABEL,
        FieldUnit.TEETH to R.string.unit_teeth,
        FieldUnit.MM to R.string.unit_millimeter,
        FieldUnit.DEGREE to R.string.unit_degree,
        FieldUnit.KV to R.string.unit_kv,
        FieldUnit.CELL to R.string.unit_cells,
        FieldUnit.CST to R.string.unit_cst,
        FieldUnit.TURN to R.string.unit_turn,
        FieldUnit.PERCENT to R.string.unit_percent,
        FieldUnit.GRAM to R.string.unit_gram
    )

    /** キーは "<選択肢集合の id>/<選択肢キー>"。集合が違えば同名の選択肢でも別文言にできる */
    private val choiceLabels: Map<String, Int> = mapOf(
        "damperOilUnit/wt" to R.string.choice_damper_oil_unit_wt,
        "damperOilUnit/cst" to R.string.choice_damper_oil_unit_cst,
        "springRate/ultraSoft" to R.string.choice_spring_rate_ultra_soft,
        "springRate/soft" to R.string.choice_spring_rate_soft,
        "springRate/mediumSoft" to R.string.choice_spring_rate_medium_soft,
        "springRate/medium" to R.string.choice_spring_rate_medium,
        "springRate/mediumHard" to R.string.choice_spring_rate_medium_hard,
        "springRate/hard" to R.string.choice_spring_rate_hard,
        "springRate/ultraHard" to R.string.choice_spring_rate_ultra_hard,
        "damperMount/pos1" to R.string.choice_damper_mount_pos1,
        "damperMount/pos2" to R.string.choice_damper_mount_pos2,
        "damperMount/pos3" to R.string.choice_damper_mount_pos3,
        "damperMount/pos4" to R.string.choice_damper_mount_pos4,
        "damperMount/pos5" to R.string.choice_damper_mount_pos5,
        // --- G-7 ---
        "diffType/ball" to R.string.choice_diff_type_ball,
        "diffType/gear" to R.string.choice_diff_type_gear,
        "diffType/spool" to R.string.choice_diff_type_spool,
        "diffType/oneWay" to R.string.choice_diff_type_one_way,
        "beltTension/loose" to R.string.choice_belt_tension_loose,
        "beltTension/normal" to R.string.choice_belt_tension_normal,
        "beltTension/tight" to R.string.choice_belt_tension_tight,
        "tireCompound/superSoft" to R.string.choice_tire_compound_super_soft,
        "tireCompound/soft" to R.string.choice_tire_compound_soft,
        "tireCompound/medium" to R.string.choice_tire_compound_medium,
        "tireCompound/hard" to R.string.choice_tire_compound_hard,
        "tireInsert/soft" to R.string.choice_tire_insert_soft,
        "tireInsert/medium" to R.string.choice_tire_insert_medium,
        "tireInsert/hard" to R.string.choice_tire_insert_hard,
        "tireInsert/moulded" to R.string.choice_tire_insert_moulded,
        "escPunch/soft" to R.string.choice_esc_punch_soft,
        "escPunch/medium" to R.string.choice_esc_punch_medium,
        "escPunch/hard" to R.string.choice_esc_punch_hard,
        "ballastPosition/front" to R.string.sheet_column_front,
        "ballastPosition/center" to R.string.sheet_column_center,
        "ballastPosition/rear" to R.string.sheet_column_rear,
        "ballastPosition/left" to R.string.choice_ballast_position_left,
        "ballastPosition/right" to R.string.choice_ballast_position_right,
        "steerFeel/under" to R.string.choice_steer_feel_under,
        "steerFeel/neutral" to R.string.choice_steer_feel_neutral,
        "steerFeel/over" to R.string.choice_steer_feel_over,
        "gripFeel/low" to R.string.choice_grip_feel_low,
        "gripFeel/medium" to R.string.choice_grip_feel_medium,
        "gripFeel/high" to R.string.choice_grip_feel_high
    )

    @StringRes
    fun fieldLabelRes(fieldKey: String): Int = fieldLabels[fieldKey] ?: NO_LABEL

    @StringRes
    fun sectionLabelRes(sectionKey: String): Int = sectionLabels[sectionKey] ?: NO_LABEL

    @StringRes
    fun columnLabelRes(columnKey: String): Int = columnLabels[columnKey] ?: NO_LABEL

    /** 単位が無い項目は [NO_LABEL] を返す。呼び出し側は 0 なら単位を描かない */
    @StringRes
    fun unitLabelRes(unit: FieldUnit): Int = unitLabels[unit] ?: NO_LABEL

    @StringRes
    fun choiceLabelRes(choiceSetId: String, optionKey: String): Int =
        choiceLabels["$choiceSetId/$optionKey"] ?: NO_LABEL

    /** 未知キー（エクスポート JSON から来た将来の項目）を含む一覧で使う */
    fun hasFieldLabel(fieldKey: String): Boolean = fieldLabels.containsKey(fieldKey)
}

@get:StringRes
val FieldDef.labelRes: Int get() = SetupFieldLabels.fieldLabelRes(key)

@get:StringRes
val FieldDef.unitLabelRes: Int get() = SetupFieldLabels.unitLabelRes(unit)

@get:StringRes
val SectionDef.labelRes: Int get() = SetupFieldLabels.sectionLabelRes(key)

@get:StringRes
val ColumnDef.labelRes: Int get() = SetupFieldLabels.columnLabelRes(key)

/** 行ラベルは、その行にある任意の項目の文言（前後で共有しているので同じ） */
@get:StringRes
val GridRow.labelRes: Int
    get() = byColumn.values.firstOrNull()?.labelRes ?: SetupFieldLabels.NO_LABEL

@StringRes
fun ChoiceSet.labelResOf(option: ChoiceOption): Int =
    SetupFieldLabels.choiceLabelRes(id, option.key)

@StringRes
fun ChoiceFieldDef.labelResOf(optionKey: String): Int =
    SetupFieldLabels.choiceLabelRes(choiceSet.id, optionKey)
