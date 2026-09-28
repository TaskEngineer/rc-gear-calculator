package io.github.taskengineer.rcgear.feature.sheet

import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.ui.Strings
import io.github.taskengineer.rcgear.core.ui.formatDate
import io.github.taskengineer.rcgear.core.ui.formatDecimals
import io.github.taskengineer.rcgear.core.ui.formatLapTime
import io.github.taskengineer.rcgear.core.ui.formatRatio
import io.github.taskengineer.rcgear.core.ui.formatSpeed
import io.github.taskengineer.rcgear.domain.model.ChassisTraits
import io.github.taskengineer.rcgear.domain.model.GearCalculationResult
import io.github.taskengineer.rcgear.domain.model.SessionConditions
import io.github.taskengineer.rcgear.domain.model.SetupValues
import io.github.taskengineer.rcgear.domain.schema.SectionDef
import io.github.taskengineer.rcgear.domain.schema.TouringSetupSchema

/**
 * シート 1 枚をテキストにする（F-4）。
 *
 * ピットで LINE に貼る・メールに入れる用。画像（PNG）と違って**引用して直せる**ので、
 * 「このセットのここだけ変えた」というやり取りに向く。
 *
 * ### 書式
 * 等幅で読まれる保証が無いので、**桁揃えに頼らない**。グリッドのセクションは
 * 「キャンバー  F: -2.0°  R: -1.5°」のように列名を値に添える形にする。
 *
 * 空欄の項目は出さない。紙のシートと違って空行に意味が無く、
 * 40 項目のうち 30 行が「—」だと本文が読めなくなるため。
 */
object SheetTextFormatter {

    fun format(
        strings: Strings,
        sheetName: String,
        carName: String,
        chassisName: String?,
        conditions: SessionConditions,
        note: String?,
        values: SetupValues,
        traits: ChassisTraits,
        gearResult: GearCalculationResult?
    ): String = buildString {
        appendLine(strings.get(R.string.sheet_share_title, carName, sheetName))
        chassisName?.takeIf { it.isNotBlank() }?.let { appendLine(it) }

        conditionsLine(strings, conditions)?.let { appendLine(it) }
        conditions.bestLapMs?.let {
            appendLine(
                strings.get(R.string.sheet_share_line, label(strings, R.string.sheet_field_best_lap)) +
                    strings.get(
                        R.string.sheet_value_with_unit,
                        it.formatLapTime(),
                        strings.get(R.string.unit_second)
                    )
            )
        }
        note?.takeIf { it.isNotBlank() }?.let { appendLine(it) }

        // ギアは計算結果（FDR / 最高速 / ロールアウト）も添える。
        // 受け取った側が同じ値を出すために電卓を叩かなくて済む
        if (gearResult != null) {
            appendLine()
            appendLine(
                strings.get(R.string.sheet_share_line, label(strings, R.string.metric_fdr)) +
                    gearResult.finalDriveRatio.formatRatio()
            )
            appendLine(
                strings.get(R.string.sheet_share_line, label(strings, R.string.metric_top_speed)) +
                    strings.get(R.string.value_kmh, gearResult.topSpeedKmh.formatSpeed())
            )
            appendLine(
                strings.get(R.string.sheet_share_line, label(strings, R.string.metric_rollout)) +
                    strings.get(
                        R.string.sheet_value_with_unit,
                        gearResult.rolloutMm.formatDecimals(1),
                        strings.get(R.string.unit_millimeter)
                    )
            )
        }

        for (section in TouringSetupSchema.sections) {
            val body = sectionBody(strings, section, values, traits)
            if (body.isEmpty()) continue
            appendLine()
            appendLine(strings.get(R.string.sheet_share_section, strings.get(section.labelRes)))
            body.forEach { appendLine(it) }
        }
    }.trimEnd()

    /**
     * セクション 1 つ分の行。**値が 1 つも入っていなければ空**を返し、
     * 呼び出し側が見出しごと落とす。
     */
    private fun sectionBody(
        strings: Strings,
        section: SectionDef,
        values: SetupValues,
        traits: ChassisTraits
    ): List<String> {
        val visibleKeys = section.visibleFields(traits).map { it.key }.toSet()
        val columns = section.columns
        if (columns == null) {
            return section.fields
                .filter { it.key in visibleKeys && values[it.key] != null }
                .map { field ->
                    strings.get(R.string.sheet_share_line, strings.get(field.labelRes)) +
                        setupValueString(field, values[field.key], strings)
                }
        }
        return section.grid().mapNotNull { row ->
            val cells = columns.mapNotNull { column ->
                val field = row.byColumn[column.key] ?: return@mapNotNull null
                if (field.key !in visibleKeys) return@mapNotNull null
                val value = values[field.key] ?: return@mapNotNull null
                strings.get(
                    R.string.sheet_share_cell,
                    strings.get(column.labelRes),
                    setupValueString(field, value, strings)
                )
            }
            if (cells.isEmpty()) return@mapNotNull null
            strings.get(R.string.sheet_share_line, strings.get(row.labelRes)) +
                cells.joinToString("  ")
        }
    }

    /** 走行日・コース・路面・気温を 1 行にまとめる。何も無ければ null */
    private fun conditionsLine(strings: Strings, conditions: SessionConditions): String? {
        val parts = buildList {
            conditions.sessionDate?.let { add(it.formatDate()) }
            conditions.trackName?.takeIf { it.isNotBlank() }?.let { add(it) }
            conditions.surface?.takeIf { it.isNotBlank() }?.let { add(it) }
            conditions.airTempC?.let {
                add(
                    strings.get(
                        R.string.sheet_value_with_unit,
                        it.formatDecimals(1),
                        strings.get(R.string.unit_celsius)
                    )
                )
            }
        }
        if (parts.isEmpty()) return null
        return parts.joinToString(strings.get(R.string.list_separator))
    }

    private fun label(strings: Strings, resId: Int): String = strings.get(resId)
}
