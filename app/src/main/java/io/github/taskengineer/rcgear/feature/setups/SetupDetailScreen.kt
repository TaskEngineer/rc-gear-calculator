package io.github.taskengineer.rcgear.feature.setups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Input
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.taskengineer.rcgear.core.designsystem.theme.RcGearTheme
import io.github.taskengineer.rcgear.domain.model.GearCalculationResult
import io.github.taskengineer.rcgear.domain.model.SavedSetup
import java.util.Locale
import kotlin.math.roundToLong

/**
 * セッティング詳細画面（PLAN Step 9）。
 *
 * - 保存値の一覧表示
 * - スナップショット差分表示（PLAN 9.5）: 内部減速比が保存時と現在で異なる場合、
 *   「保存時 2.60 / 現在 2.70」の形式で両方の計算結果を並べる
 * - 「CALC に流し込む」ボタン → CALC タブへ遷移（PLAN 5.3）
 * - 削除（確認ダイアログ付き）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupDetailScreen(
    onNavigateBack: () -> Unit,
    onLoadToCalc: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SetupDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // 削除完了 or 対象が見つからない場合は一覧へ戻る
    LaunchedEffect(state.isDeleted, state.notFound) {
        if (state.isDeleted || state.notFound) onNavigateBack()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.setup?.name ?: "セッティング詳細") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "戻る"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::onDeleteClick) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "削除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        val setup = state.setup
        if (state.isLoading || setup == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                    onClick = {
                        viewModel.onLoadToCalc()
                        onLoadToCalc()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Input, contentDescription = null)
                    Text(
                        text = "CALC に流し込む",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }

    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::onDeleteConfirmDismiss,
            title = { Text("削除の確認") },
            text = { Text("「${state.setup?.name}」を削除しますか？この操作は取り消せません。") },
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DetailRow(
                label = "シャーシ",
                value = state.chassis?.name ?: setup.chassisId
            )
            DetailRow(label = "ピニオン", value = "${setup.pinion}T")
            DetailRow(label = "スパー", value = "${setup.spur}T")
            DetailRow(label = "モーターKV", value = "${setup.kv}")
            DetailRow(label = "セル数", value = "${setup.cells}S")
            DetailRow(label = "タイヤ径", value = "${setup.tireMm}mm")
            DetailRow(
                label = "内部減速比（保存時）",
                value = String.format(Locale.US, "%.2f", setup.internalRatioSnapshot)
            )
        }
    }
}

/**
 * スナップショット差分カード（PLAN 9.5）。
 * 内部減速比が保存時と現在で異なる場合のみ表示される。
 */
@Composable
private fun SnapshotDiffCard(
    snapshotRatio: Double,
    currentRatio: Double,
    snapshotResult: GearCalculationResult?,
    currentResult: GearCalculationResult
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "⚠ 内部減速比がDBと異なります",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = String.format(
                    Locale.US,
                    "保存時 %.2f / 現在 %.2f",
                    snapshotRatio, currentRatio
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            snapshotResult?.let {
                DetailRow(
                    label = "最高速（保存時比）",
                    value = String.format(Locale.US, "%.1f km/h", it.topSpeedKmh)
                )
            }
            DetailRow(
                label = "最高速（現在比）",
                value = String.format(Locale.US, "%.1f km/h", currentResult.topSpeedKmh)
            )
            Text(
                text = "「CALC に流し込む」と現在のDB値で再計算されます",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ResultCard(title: String, result: GearCalculationResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            DetailRow(
                label = "最高速",
                value = String.format(Locale.US, "%.1f km/h", result.topSpeedKmh)
            )
            DetailRow(
                label = "1次減速比",
                value = String.format(Locale.US, "%.2f", result.primaryRatio)
            )
            DetailRow(
                label = "最終減速比 FDR",
                value = String.format(Locale.US, "%.2f", result.finalDriveRatio)
            )
            DetailRow(
                label = "ホイールRPM",
                value = String.format(Locale.US, "%,d", result.wheelRpm.roundToLong())
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = RcGearTheme.extendedTypography.hudUnit.copy(
                fontSize = MaterialTheme.typography.bodyMedium.fontSize
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
