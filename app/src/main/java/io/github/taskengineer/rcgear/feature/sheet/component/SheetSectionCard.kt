package io.github.taskengineer.rcgear.feature.sheet.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.designsystem.component.ComponentPreview
import io.github.taskengineer.rcgear.core.designsystem.component.LabeledRow
import io.github.taskengineer.rcgear.core.designsystem.component.RcCard
import io.github.taskengineer.rcgear.core.designsystem.theme.RcGearTheme
import io.github.taskengineer.rcgear.domain.model.ChassisTraits
import io.github.taskengineer.rcgear.domain.model.SetupValue
import io.github.taskengineer.rcgear.domain.model.SetupValues
import io.github.taskengineer.rcgear.domain.schema.SectionDef
import io.github.taskengineer.rcgear.domain.schema.TouringSetupSchema
import io.github.taskengineer.rcgear.feature.sheet.labelRes
import io.github.taskengineer.rcgear.feature.sheet.setupValueText

/**
 * セクション 1 つを読み取り専用で描く（G-3）。
 *
 * 列（F / C / R）を持つセクションは**実物のセッティングシート用紙と同じグリッド**にする。
 * 行と列の組み立ては `SectionDef.grid()`（`:core:domain`）が済ませているので、
 * ここは並べるだけ。**項目を足してもこのファイルは変わらない** — それが
 * レジストリ方式（HANDOFF §5.3）が機能していることの確認点で、G-7 の受け入れ条件でもある。
 *
 * @param traits シャーシの素性。出さない項目（センターデフの無い車のデフオイル等）を落とす。
 *   不明なら出す（`ChassisTraits.satisfies`）
 */
@Composable
fun SheetSectionCard(
    section: SectionDef,
    values: SetupValues,
    traits: ChassisTraits,
    modifier: Modifier = Modifier
) {
    val visibleKeys = section.visibleFields(traits).map { it.key }.toSet()
    if (visibleKeys.isEmpty()) return

    RcCard(
        title = stringResource(section.labelRes),
        modifier = modifier,
        spacing = 4.dp
    ) {
        val columns = section.columns
        if (columns == null) {
            section.fields
                .filter { it.key in visibleKeys }
                .forEach { field ->
                    LabeledRow(
                        label = stringResource(field.labelRes),
                        value = setupValueText(field, values[field.key]),
                        valueColor = if (values[field.key] == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
        } else {
            // ---- 列見出し ----
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "",
                    modifier = Modifier.weight(LABEL_WEIGHT)
                )
                columns.forEach { column ->
                    Text(
                        text = stringResource(column.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            HorizontalDivider()

            // ---- 行 ----
            section.grid().forEach { row ->
                val cells = row.byColumn.filterValues { it.key in visibleKeys }
                if (cells.isEmpty()) return@forEach
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(row.labelRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(LABEL_WEIGHT)
                            .padding(vertical = 2.dp)
                    )
                    columns.forEach { column ->
                        val field = cells[column.key]
                        // フロントにしか無い項目（キャスター等）はリア列が空欄になる
                        Text(
                            text = setupValueText(field, field?.let { values[it.key] }),
                            style = RcGearTheme.extendedTypography.hudUnit.copy(
                                fontSize = MaterialTheme.typography.bodySmall.fontSize
                            ),
                            color = if (field == null || values[field.key] == null) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * レジストリに無いキーを読み取り専用で出す（G-3）。
 *
 * 新しい版のアプリで書かれたエクスポート JSON を古い版で読むと起こる。
 * 値は捨てずに保持する仕様（`SetupValues` の KDoc）なので、
 * 「見えないが残っている」ではなく「読めない項目として見える」ようにする。
 */
@Composable
fun UnknownValuesCard(
    values: SetupValues,
    modifier: Modifier = Modifier
) {
    val unknown = values.unknownKeys().sorted()
    if (unknown.isEmpty()) return

    RcCard(
        title = stringResource(R.string.sheet_section_unknown),
        modifier = modifier,
        spacing = 4.dp
    ) {
        Text(
            text = stringResource(R.string.sheet_section_unknown_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        unknown.forEach { key ->
            LabeledRow(
                label = key,
                value = setupValueText(field = null, value = values[key])
            )
        }
    }
}

/** 行ラベルの幅。列が 2 つのときにラベルが折り返さない程度に取る */
private const val LABEL_WEIGHT = 1.4f

@Preview(name = "SheetSectionCard", showBackground = true)
@Composable
private fun SheetSectionCardPreview() {
    ComponentPreview {
        SheetSectionCard(
            section = TouringSetupSchema.GEAR,
            values = SetupValues.of(
                "pinion" to SetupValue.IntV(29),
                "spur" to SetupValue.IntV(84),
                "internalRatio" to SetupValue.DecimalV(1.9),
                "tireMm" to SetupValue.IntV(62)
            ),
            traits = ChassisTraits.UNKNOWN
        )
        SheetSectionCard(
            section = TouringSetupSchema.SUSPENSION,
            values = SetupValues.of(
                "front.camberDeg" to SetupValue.DecimalV(-2.0),
                "rear.camberDeg" to SetupValue.DecimalV(-1.5),
                "front.casterDeg" to SetupValue.DecimalV(6.0)
            ),
            traits = ChassisTraits.UNKNOWN
        )
    }
}
