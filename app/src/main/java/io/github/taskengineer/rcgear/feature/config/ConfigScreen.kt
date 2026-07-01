package io.github.taskengineer.rcgear.feature.config

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.taskengineer.rcgear.BuildConfig
import io.github.taskengineer.rcgear.core.designsystem.theme.ThemeMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CONFIG タブ: 設定画面（PLAN Step 11）。
 * セクション構成: DISPLAY / CALC TUNING / DATA / ABOUT
 */
@Composable
fun ConfigScreen(
    modifier: Modifier = Modifier,
    viewModel: ConfigViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onMessageShown()
        }
    }

    // ----- SAF ランチャー（PLAN 9.3） -----
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::onExportToUri) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::onImportFromUri) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ---- DISPLAY ----
            SectionHeader("DISPLAY")
            ConfigRow(
                title = "テーマ",
                value = state.preferences.themeMode.label(),
                onClick = viewModel::onThemeDialogOpen
            )
            SwitchRow(
                title = "mph を併記",
                checked = state.preferences.showMphAlongside,
                onCheckedChange = viewModel::onShowMphChange
            )
            SwitchRow(
                title = "アニメーション",
                checked = state.preferences.animationEnabled,
                onCheckedChange = viewModel::onAnimationEnabledChange
            )

            // ---- CALC TUNING ----
            SectionHeader("CALC TUNING")
            ConfigRow(
                title = "基準 FDR",
                subtitle = "セッティング傾向バーの中央となる最終減速比",
                value = String.format(Locale.US, "%.1f", state.preferences.balanceFdr),
                onClick = viewModel::onBalanceFdrDialogOpen
            )

            // ---- DATA ----
            SectionHeader("DATA")
            ConfigRow(
                title = "データを書き出す",
                subtitle = "保存セッティングとシャーシ上書きを JSON で保存",
                onClick = { exportLauncher.launch(defaultExportFileName()) }
            )
            ConfigRow(
                title = "データを読み込む",
                subtitle = "書き出した JSON から取り込み（既存データは保持）",
                onClick = { importLauncher.launch(arrayOf("application/json")) }
            )
            ConfigRow(
                title = "全データを削除",
                subtitle = "保存セッティング・上書き・履歴・設定をすべて消去",
                titleColor = MaterialTheme.colorScheme.error,
                onClick = viewModel::onDeleteAllClick
            )

            // ---- ABOUT ----
            SectionHeader("ABOUT")
            ConfigRow(
                title = "バージョン",
                value = BuildConfig.VERSION_NAME,
                onClick = null
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // ----- ダイアログ類 -----

    if (state.showThemeDialog) {
        ThemeSelectDialog(
            current = state.preferences.themeMode,
            onSelect = viewModel::onThemeModeSelected,
            onDismiss = viewModel::onThemeDialogDismiss
        )
    }

    if (state.showBalanceFdrDialog) {
        BalanceFdrDialog(
            input = state.balanceFdrInput,
            error = state.balanceFdrError,
            onInputChange = viewModel::onBalanceFdrInputChange,
            onConfirm = viewModel::onBalanceFdrConfirm,
            onDismiss = viewModel::onBalanceFdrDialogDismiss
        )
    }

    if (state.showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::onDeleteAllDismiss,
            title = { Text("全データ削除の確認") },
            text = {
                Text("保存セッティング・シャーシ上書き・計算履歴・設定をすべて削除します。この操作は取り消せません。")
            },
            confirmButton = {
                TextButton(onClick = viewModel::onDeleteAllConfirm) {
                    Text("削除する", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDeleteAllDismiss) {
                    Text("キャンセル")
                }
            }
        )
    }
}

// ============================================================
// セクション・行コンポーネント
// ============================================================

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun ConfigRow(
    title: String,
    onClick: (() -> Unit)?,
    subtitle: String? = null,
    value: String? = null,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        value?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ============================================================
// ダイアログ
// ============================================================

@Composable
private fun ThemeSelectDialog(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("テーマ") },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = mode == current,
                                onClick = { onSelect(mode) }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = mode == current,
                            onClick = { onSelect(mode) }
                        )
                        Text(
                            text = mode.label(),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

@Composable
private fun BalanceFdrDialog(
    input: String,
    error: String?,
    onInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("基準 FDR") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                label = { Text("基準 FDR") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = error != null,
                supportingText = {
                    Text(error ?: "セッティング傾向バーの中央（デフォルト 7.0）")
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("設定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

// ============================================================
// ヘルパー
// ============================================================

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.DARK -> "ダーク"
    ThemeMode.LIGHT -> "ライト"
    ThemeMode.SYSTEM -> "システムに従う"
}

/** エクスポートのデフォルトファイル名。例: rcgear-export-20260702.json */
private fun defaultExportFileName(): String {
    val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    return "rcgear-export-$date.json"
}
