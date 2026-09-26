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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
        title = setup?.name ?: "セッティング詳細",
        onNavigateBack = onNavigateBack,
        events = viewModel.events,
        isLoading = state.isLoading || setup == null,
        modifier = modifier,
        backContentDescription = "戻る",
        actions = {
            IconButton(onClick = viewModel::onDeleteClick) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "削除",
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
                    title = if (state.currentResult != null) "計算結果（保存時の内部減速比）" else "計算結果",
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
                    text = "CALC に流し込む",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }

    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::onDeleteConfirmDismiss,
            title = { Text("削除の確認") },
            text = { Text("「${setup?.name}」を削除しますか？この操作は取り消せません。") },
            confirmButton = {
                TextButton(onClick = viewModel::onDeleteConfirm) {
                    Text("削除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDeleteConfirmDismiss) {
                    Text("キャンセル")
                }
            }
        )
    }
}

@Composable
private fun InfoCard(state: SetupDetailUiState, setup: SavedSetup) {
    RcCard {
        LabeledRow(
            label = "シャーシ",
            value = state.chassis?.name ?: setup.chassisId
        )
        LabeledRow(label = "ピニオン", value = "${setup.pinion}T")
        LabeledRow(label = "スパー", value = "${setup.spur}T")
        LabeledRow(label = "モーターKV", value = "${setup.kv}")
        LabeledRow(label = "セル数", value = "${setup.cells}S")
        LabeledRow(label = "タイヤ径", value = "${setup.tireMm}mm")
        LabeledRow(
            label = "内部減速比（保存時）",
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
            text = "⚠ 内部減速比がDBと異なります",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.tertiary
        )
        ValueDiffHeader(leftLabel = "保存時", rightLabel = "現在")
        ValueDiffRow(
            label = "内部減速比",
            left = snapshotRatio.formatRatio(),
            right = currentRatio.formatRatio()
        )
        ValueDiffRow(
            label = "最高速",
            left = snapshotResult?.let { "${it.topSpeedKmh.formatSpeed()} km/h" },
            right = "${currentResult.topSpeedKmh.formatSpeed()} km/h"
        )
        Text(
            text = "「CALC に流し込む」と現在のDB値で再計算されます",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ResultCard(title: String, result: GearCalculationResult) {
    RcCard(title = title) {
        LabeledRow(
            label = "最高速",
            value = "${result.topSpeedKmh.formatSpeed()} km/h"
        )
        LabeledRow(
            label = "1次減速比",
            value = result.primaryRatio.formatRatio()
        )
        LabeledRow(
            label = "最終減速比 FDR",
            value = result.finalDriveRatio.formatRatio()
        )
        LabeledRow(
            label = "ホイールRPM",
            value = result.wheelRpm.formatRpm()
        )
    }
}
