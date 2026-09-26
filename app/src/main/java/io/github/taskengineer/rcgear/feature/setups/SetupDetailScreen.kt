package io.github.taskengineer.rcgear.feature.setups

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.designsystem.component.LabeledRow
import io.github.taskengineer.rcgear.core.designsystem.component.RcCard
import io.github.taskengineer.rcgear.core.designsystem.component.ValueDiffHeader
import io.github.taskengineer.rcgear.core.designsystem.component.ValueDiffRow
import io.github.taskengineer.rcgear.core.ui.RcDetailScaffold
import io.github.taskengineer.rcgear.core.ui.formatRatio
import io.github.taskengineer.rcgear.core.ui.formatRpm
import io.github.taskengineer.rcgear.core.ui.formatSpeed
import io.github.taskengineer.rcgear.domain.model.GearCalculationResult
import io.github.taskengineer.rcgear.domain.model.SavedSetup

/**
 * セッティング詳細画面（PLAN Step 9）。
 *
 * - 保存値の一覧表示
 * - スナップショット差分表示（PLAN 9.5）: 内部減速比が保存時と現在で異なる場合、
 *   「保存時 2.60 / 現在 2.70」の形式で両方の計算結果を並べる
 * - 「CALC に流し込む」ボタン → CALC 画面へ遷移（PLAN 5.3 / U-3）。
 *   値の受け渡しはルート引数なので、この画面は setupId を渡すだけ
 * - 削除（確認ダイアログ付き）
 */
@Composable
fun SetupDetailScreen(
    onNavigateBack: () -> Unit,
    onLoadToCalc: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SetupDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val setup = state.setup

    RcDetailScaffold(
        title = setup?.name ?: stringResource(R.string.setup_detail_title),
        onNavigateBack = onNavigateBack,
        events = viewModel.events,
        isLoading = state.isLoading || setup == null,
        modifier = modifier,
        actions = {
            IconButton(onClick = viewModel::onDeleteClick) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    ) {
        if (setup != null) {
            // ---- 基本情報 ----
            InfoCard(state = state, setup = setup)

            // ---- スナップショット差分（差がある場合のみ） ----
            state.currentResult?.let { current ->
                SnapshotDiffCard(
                    snapshotRatio = setup.internalRatioSnapshot,
                    currentRatio = state.chassis?.internalRatio ?: 0.0,
                    snapshotResult = state.snapshotResult,
                    currentResult = current
                )
            }

            // ---- 計算結果（保存時の値） ----
            state.snapshotResult?.let { result ->
                ResultCard(
                    title = stringResource(
                        if (state.currentResult != null) {
                            R.string.setup_detail_result_title_snapshot
                        } else {
                            R.string.setup_detail_result_title
                        }
                    ),
                    result = result
                )
            }

            // ---- CALC へ流し込む ----
            Button(
                onClick = { onLoadToCalc(setup.id) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.Input, contentDescription = null)
                Text(
                    text = stringResource(R.string.setup_detail_load_to_calc),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }

    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::onDeleteConfirmDismiss,
            title = { Text(stringResource(R.string.setup_detail_delete_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.setup_detail_delete_confirm_text,
                        setup?.name.orEmpty()
                    )
                )
            },
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

@Composable
private fun InfoCard(state: SetupDetailUiState, setup: SavedSetup) {
    RcCard {
        LabeledRow(
            label = stringResource(R.string.setup_detail_chassis),
            value = state.chassis?.name ?: setup.chassisId
        )
        LabeledRow(
            label = stringResource(R.string.field_pinion),
            value = stringResource(R.string.value_teeth, setup.pinion)
        )
        LabeledRow(
            label = stringResource(R.string.field_spur),
            value = stringResource(R.string.value_teeth, setup.spur)
        )
        LabeledRow(
            label = stringResource(R.string.field_motor_kv),
            value = stringResource(R.string.value_number, setup.kv)
        )
        LabeledRow(
            label = stringResource(R.string.field_cells),
            value = stringResource(R.string.value_cells, setup.cells)
        )
        LabeledRow(
            label = stringResource(R.string.field_tire_mm),
            value = stringResource(R.string.value_millimeter, setup.tireMm)
        )
        LabeledRow(
            label = stringResource(R.string.setup_detail_internal_ratio_snapshot),
            value = setup.internalRatioSnapshot.formatRatio()
        )
    }
}

/**
 * スナップショット差分カード（PLAN 9.5）。
 * 内部減速比が保存時と現在で異なる場合のみ表示される。
 *
 * 差分の描画は [ValueDiffRow] に寄せた（U-1）。Phase 2 でシート全体の差分を
 * 出すときも同じ行が使われるので、ここが最初の 1 例になる。
 */
@Composable
private fun SnapshotDiffCard(
    snapshotRatio: Double,
    currentRatio: Double,
    snapshotResult: GearCalculationResult?,
    currentResult: GearCalculationResult
) {
    RcCard {
        Text(
            text = stringResource(R.string.setup_detail_diff_warning),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.tertiary
        )
        ValueDiffHeader(
            leftLabel = stringResource(R.string.setup_detail_diff_saved),
            rightLabel = stringResource(R.string.setup_detail_diff_current)
        )
        ValueDiffRow(
            label = stringResource(R.string.field_internal_ratio),
            left = snapshotRatio.formatRatio(),
            right = currentRatio.formatRatio()
        )
        ValueDiffRow(
            label = stringResource(R.string.metric_top_speed),
            left = snapshotResult?.let {
                stringResource(R.string.value_kmh, it.topSpeedKmh.formatSpeed())
            },
            right = stringResource(R.string.value_kmh, currentResult.topSpeedKmh.formatSpeed())
        )
        Text(
            text = stringResource(R.string.setup_detail_diff_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ResultCard(title: String, result: GearCalculationResult) {
    RcCard(title = title) {
        LabeledRow(
            label = stringResource(R.string.metric_top_speed),
            value = stringResource(R.string.value_kmh, result.topSpeedKmh.formatSpeed())
        )
        LabeledRow(
            label = stringResource(R.string.metric_primary_ratio),
            value = result.primaryRatio.formatRatio()
        )
        LabeledRow(
            label = stringResource(R.string.metric_fdr),
            value = result.finalDriveRatio.formatRatio()
        )
        LabeledRow(
            label = stringResource(R.string.metric_wheel_rpm),
            value = result.wheelRpm.formatRpm()
        )
    }
}
