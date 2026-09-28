package io.github.taskengineer.rcgear.feature.sheet

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.designsystem.component.LabeledRow
import io.github.taskengineer.rcgear.core.designsystem.component.RcCard
import io.github.taskengineer.rcgear.core.ui.RcDetailScaffold
import io.github.taskengineer.rcgear.core.ui.Share
import io.github.taskengineer.rcgear.core.ui.formatDate
import io.github.taskengineer.rcgear.core.ui.formatDecimals
import io.github.taskengineer.rcgear.core.ui.formatLapTime
import io.github.taskengineer.rcgear.core.ui.formatRatio
import io.github.taskengineer.rcgear.core.ui.formatSpeed
import io.github.taskengineer.rcgear.core.ui.rememberStrings
import io.github.taskengineer.rcgear.domain.model.GearCalculationResult
import io.github.taskengineer.rcgear.domain.model.SessionConditions
import io.github.taskengineer.rcgear.domain.schema.TouringSetupSchema
import io.github.taskengineer.rcgear.feature.sheet.component.SheetSectionCard
import io.github.taskengineer.rcgear.feature.sheet.component.UnknownValuesCard

/**
 * セッティングシートの閲覧画面（G-3）。
 *
 * セクションはレジストリ（`TouringSetupSchema.sections`）の順に並べるだけで、
 * **項目を足してもこの画面は変わらない**（G-7 の受け入れ条件）。
 *
 * セクションのカードをタップすると、そのセクションの編集画面（G-4）に入る。
 * CALC 連携は G-5、差分表示は G-6 で入る。
 */
@Composable
fun SheetDetailScreen(
    onNavigateBack: () -> Unit,
    onEditSectionClick: (sheetId: String, sectionKey: String) -> Unit,
    onEditHeaderClick: (String) -> Unit,
    onLoadToCalcClick: (String) -> Unit,
    onCompareClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SheetDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val strings = rememberStrings()
    val chooserTitle = stringResource(R.string.sheet_share_chooser)

    RcDetailScaffold(
        title = state.name,
        onNavigateBack = onNavigateBack,
        events = viewModel.events,
        isLoading = state.isLoading,
        modifier = modifier,
        actions = {
            IconButton(
                onClick = {
                    // テキストの組み立てはコンポジションの外。
                    // 表示と同じ書式になるよう setupValueString を共有している（F-4）
                    Share.text(
                        context = context,
                        text = SheetTextFormatter.format(
                            strings = strings,
                            sheetName = state.name,
                            carName = state.carName,
                            chassisName = state.chassis?.let { chassis ->
                                listOf(chassis.makerName, chassis.name)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" ")
                            },
                            conditions = state.conditions,
                            note = state.note,
                            values = state.values,
                            traits = state.traits,
                            gearResult = state.gearResult
                        ),
                        subject = state.name,
                        chooserTitle = chooserTitle
                    )
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = stringResource(R.string.sheet_share)
                )
            }
            IconButton(onClick = { onCompareClick(state.sheetId) }) {
                Icon(
                    imageVector = Icons.Filled.CompareArrows,
                    contentDescription = stringResource(R.string.sheet_compare_title)
                )
            }
            IconButton(onClick = { onEditHeaderClick(state.sheetId) }) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.sheet_header_edit_title)
                )
            }
            IconButton(onClick = viewModel::onFavoriteToggle) {
                Icon(
                    imageVector = if (state.isFavorite) {
                        Icons.Filled.Star
                    } else {
                        Icons.Outlined.StarOutline
                    },
                    contentDescription = stringResource(R.string.sheet_detail_favorite),
                    tint = if (state.isFavorite) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            IconButton(onClick = viewModel::onDeleteClick) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.sheet_detail_delete)
                )
            }
        }
    ) {
        // ---- ヘッダ（車 / シャーシ / 走行条件） ----
        RcCard(spacing = 2.dp) {
            Text(
                text = state.carName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            state.chassis?.let { chassis ->
                Text(
                    text = listOf(chassis.makerName, chassis.name)
                        .filter { it.isNotBlank() }
                        .joinToString(" "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(R.string.sheet_detail_updated_at, state.updatedAt.formatDate()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        ConditionsCard(
            conditions = state.conditions,
            note = state.note,
            onClick = { onEditHeaderClick(state.sheetId) }
        )

        // ---- 値（セクションごと） ----
        TouringSetupSchema.sections.forEach { section ->
            SheetSectionCard(
                section = section,
                values = state.values,
                traits = state.traits,
                onEditClick = { onEditSectionClick(state.sheetId, section.key) }
            )
            // ギアだけは「この値で何km/h 出るか」をその場で出し、CALC への入口も添える（G-5）
            if (section.key == TouringSetupSchema.GEAR.key) {
                GearResultCard(
                    result = state.gearResult,
                    onOpenCalcClick = { onLoadToCalcClick(state.sheetId) }
                )
            }
        }

        UnknownValuesCard(values = state.values)
    }

    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::onDeleteConfirmDismiss,
            title = { Text(stringResource(R.string.sheet_detail_delete_confirm_title)) },
            text = { Text(stringResource(R.string.sheet_detail_delete_confirm_text, state.name)) },
            confirmButton = {
                TextButton(onClick = viewModel::onDeleteConfirm) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDeleteConfirmDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/**
 * ギアの計算結果（G-5）。
 *
 * シートの値は絶対値のスナップショットなので、シャーシDBを後から編集しても
 * ここの数字は動かない。CALC で詰めた結果を書き戻す入口もここに置く。
 */
@Composable
private fun GearResultCard(
    result: GearCalculationResult?,
    onOpenCalcClick: () -> Unit
) {
    RcCard(spacing = 4.dp) {
        if (result == null) {
            Text(
                text = stringResource(R.string.sheet_detail_gear_incomplete),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LabeledRow(
                label = stringResource(R.string.metric_fdr),
                value = result.finalDriveRatio.formatRatio()
            )
            LabeledRow(
                label = stringResource(R.string.metric_top_speed),
                value = stringResource(R.string.value_kmh, result.topSpeedKmh.formatSpeed())
            )
            LabeledRow(
                label = stringResource(R.string.metric_rollout),
                value = stringResource(
                    R.string.sheet_value_with_unit,
                    result.rolloutMm.formatDecimals(1),
                    stringResource(R.string.unit_millimeter)
                )
            )
        }
        OutlinedButton(
            onClick = onOpenCalcClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.sheet_detail_open_calc))
        }
    }
}

/**
 * 走行条件（ヘッダ）。1 つも入っていなければカードごと出さない。
 *
 * 条件を値の束（bag）ではなくヘッダに置いているのは、差分の対象を bag だけに
 * 保つため（「気温が 3℃ 違います」が変更点として出ないように）。`SetupSheet` の KDoc 参照。
 */
@Composable
private fun ConditionsCard(
    conditions: SessionConditions,
    note: String?,
    onClick: () -> Unit
) {
    val hasAny = conditions != SessionConditions() || !note.isNullOrBlank()

    RcCard(
        title = stringResource(R.string.sheet_detail_conditions_section),
        onClick = onClick,
        spacing = 4.dp
    ) {
        if (!hasAny) {
            // 走行日もコースも空のうちは、入力の入口だと分かる 1 行だけ出す
            Text(
                text = stringResource(R.string.sheet_detail_conditions_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@RcCard
        }
        conditions.sessionDate?.let {
            LabeledRow(
                label = stringResource(R.string.sheet_field_session_date),
                value = it.formatDate()
            )
        }
        conditions.trackName?.takeIf { it.isNotBlank() }?.let {
            LabeledRow(label = stringResource(R.string.sheet_field_track_name), value = it)
        }
        conditions.surface?.takeIf { it.isNotBlank() }?.let {
            LabeledRow(label = stringResource(R.string.sheet_field_surface), value = it)
        }
        conditions.airTempC?.let {
            LabeledRow(
                label = stringResource(R.string.sheet_field_air_temp),
                value = stringResource(
                    R.string.sheet_value_with_unit,
                    it.formatDecimals(1),
                    stringResource(R.string.unit_celsius)
                )
            )
        }
        conditions.trackTempC?.let {
            LabeledRow(
                label = stringResource(R.string.sheet_field_track_temp),
                value = stringResource(
                    R.string.sheet_value_with_unit,
                    it.formatDecimals(1),
                    stringResource(R.string.unit_celsius)
                )
            )
        }
        conditions.humidityPct?.let {
            LabeledRow(
                label = stringResource(R.string.sheet_field_humidity),
                value = stringResource(
                    R.string.sheet_value_with_unit,
                    it.toString(),
                    stringResource(R.string.unit_percent)
                )
            )
        }
        conditions.bestLapMs?.let {
            LabeledRow(
                label = stringResource(R.string.sheet_field_best_lap),
                value = stringResource(
                    R.string.sheet_value_with_unit,
                    it.formatLapTime(),
                    stringResource(R.string.unit_second)
                )
            )
        }
        note?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
