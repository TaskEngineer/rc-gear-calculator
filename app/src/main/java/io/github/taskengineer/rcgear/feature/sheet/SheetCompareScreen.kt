package io.github.taskengineer.rcgear.feature.sheet

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.designsystem.component.Option
import io.github.taskengineer.rcgear.core.designsystem.component.RcCard
import io.github.taskengineer.rcgear.core.designsystem.component.RcSelectField
import io.github.taskengineer.rcgear.core.designsystem.component.SwitchRow
import io.github.taskengineer.rcgear.core.designsystem.component.ValueDiffHeader
import io.github.taskengineer.rcgear.core.designsystem.component.ValueDiffRow
import io.github.taskengineer.rcgear.core.ui.RcDetailScaffold
import io.github.taskengineer.rcgear.core.ui.asString
import io.github.taskengineer.rcgear.domain.diff.DiffKind
import io.github.taskengineer.rcgear.domain.diff.FieldDiff
import io.github.taskengineer.rcgear.domain.schema.TouringSetupSchema

/**
 * シートの比較画面（G-6）。3 つの比較軸をタブで切り替える。
 *
 * **左が相手、右がこのシート。** `SheetDiff` は主役を `left` と呼ぶが、
 * 表示は「元の値 → 今の値」と読めるほうが自然なので、行に渡すときに左右が入れ替わる。
 * この 1 箇所だけで入れ替えているので、他に読み替えは要らない。
 */
@Composable
fun SheetCompareScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SheetCompareViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    RcDetailScaffold(
        title = stringResource(R.string.sheet_compare_title),
        onNavigateBack = onNavigateBack,
        events = viewModel.events,
        isLoading = state.isLoading,
        modifier = modifier
    ) {
        TabRow(selectedTabIndex = state.axis.ordinal) {
            DiffAxis.entries.forEach { axis ->
                Tab(
                    selected = state.axis == axis,
                    onClick = { viewModel.onAxisChange(axis) },
                    text = { Text(stringResource(axis.labelRes)) }
                )
            }
        }

        when (state.axis) {
            DiffAxis.CHASSIS -> Text(
                text = stringResource(R.string.sheet_compare_chassis_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            DiffAxis.BASELINE -> if (!state.hasBaseline) {
                Text(
                    text = stringResource(R.string.sheet_compare_no_baseline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DiffAxis.OTHER -> if (state.candidates.isEmpty()) {
                Text(
                    text = stringResource(R.string.sheet_compare_no_candidates),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                RcSelectField(
                    label = stringResource(R.string.sheet_compare_select_other),
                    selectedKey = state.otherSheetId,
                    options = state.candidates.map { Option(key = it.id, label = it.name) },
                    onSelect = viewModel::onOtherSheetSelect
                )
            }
        }

        SwitchRow(
            title = stringResource(R.string.sheet_compare_include_same),
            checked = state.includeSame,
            onCheckedChange = viewModel::onIncludeSameChange
        )

        val otherLabel = state.otherLabel
        if (otherLabel != null && state.groups.isEmpty()) {
            Text(
                text = stringResource(R.string.sheet_compare_no_diff),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (otherLabel != null) {
            val leftLabel = otherLabel.asString()
            state.groups.forEach { group ->
                RcCard(
                    title = group.sectionTitle(),
                    spacing = 4.dp
                ) {
                    ValueDiffHeader(
                        leftLabel = leftLabel,
                        rightLabel = stringResource(R.string.sheet_compare_this_sheet)
                    )
                    group.rows.forEach { diff ->
                        DiffRow(diff = diff)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffRow(diff: FieldDiff) {
    val label = diff.field?.let { fieldEditorLabel(it) } ?: diff.fieldKey
    ValueDiffRow(
        label = label,
        // 左 = 相手（SheetDiff の right）、右 = このシート（SheetDiff の left）
        left = diff.right?.let { setupValueText(diff.field, it) },
        right = diff.left?.let { setupValueText(diff.field, it) },
        changed = diff.kind != DiffKind.SAME
    )
}

/** セクション見出し。レジストリに無いキーの束は「未分類」にする */
@Composable
private fun DiffGroup.sectionTitle(): String {
    val section = sectionKey?.let { key ->
        TouringSetupSchema.sections.firstOrNull { it.key == key }
    } ?: return stringResource(R.string.sheet_section_unknown)
    return stringResource(section.labelRes)
}
