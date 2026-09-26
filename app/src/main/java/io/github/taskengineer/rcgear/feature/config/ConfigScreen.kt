package io.github.taskengineer.rcgear.feature.config

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.taskengineer.rcgear.BuildConfig
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.designsystem.component.ChoiceRow
import io.github.taskengineer.rcgear.core.designsystem.component.RcNumberField
import io.github.taskengineer.rcgear.core.designsystem.component.SectionHeader
import io.github.taskengineer.rcgear.core.designsystem.component.SwitchRow
import io.github.taskengineer.rcgear.core.ui.UiText
import io.github.taskengineer.rcgear.core.ui.asString
import io.github.taskengineer.rcgear.core.ui.formatSpeed
import io.github.taskengineer.rcgear.domain.model.ThemeMode
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

    // 文字列化は合成中に済ませる（showSnackbar は Composable の外で走るため）
    val message = state.message?.asString()
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
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
            SectionHeader(stringResource(R.string.config_section_display))
            ChoiceRow(
                title = stringResource(R.string.config_theme),
                value = stringResource(state.preferences.themeMode.labelRes),
                onClick = viewModel::onThemeDialogOpen
            )
            SwitchRow(
                title = stringResource(R.string.config_show_mph),
                checked = state.preferences.showMphAlongside,
                onCheckedChange = viewModel::onShowMphChange
            )
            SwitchRow(
                title = stringResource(R.string.config_animation),
                checked = state.preferences.animationEnabled,
                onCheckedChange = viewModel::onAnimationEnabledChange
            )

            // ---- CALC TUNING ----
            SectionHeader(stringResource(R.string.config_section_calc_tuning))
            ChoiceRow(
                title = stringResource(R.string.config_balance_fdr),
                subtitle = stringResource(R.string.config_balance_fdr_subtitle),
                value = state.preferences.balanceFdr.formatSpeed(),
                onClick = viewModel::onBalanceFdrDialogOpen
            )

            // ---- DATA ----
            SectionHeader(stringResource(R.string.config_section_data))
            ChoiceRow(
                title = stringResource(R.string.config_export),
                subtitle = stringResource(R.string.config_export_subtitle),
                onClick = { exportLauncher.launch(defaultExportFileName()) }
            )
            ChoiceRow(
                title = stringResource(R.string.config_import),
                subtitle = stringResource(R.string.config_import_subtitle),
                onClick = { importLauncher.launch(arrayOf("application/json")) }
            )
            ChoiceRow(
                title = stringResource(R.string.config_delete_all),
                subtitle = stringResource(R.string.config_delete_all_subtitle),
                titleColor = MaterialTheme.colorScheme.error,
                onClick = viewModel::onDeleteAllClick
            )

            // ---- ABOUT ----
            SectionHeader(stringResource(R.string.config_section_about))
            ChoiceRow(
                title = stringResource(R.string.config_version),
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
            title = { Text(stringResource(R.string.config_delete_all_confirm_title)) },
            text = { Text(stringResource(R.string.config_delete_all_confirm_text)) },
            confirmButton = {
                TextButton(onClick = viewModel::onDeleteAllConfirm) {
                    Text(
                        text = stringResource(R.string.config_delete_all_confirm_button),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDeleteAllDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
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
        title = { Text(stringResource(R.string.config_theme)) },
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
                            text = stringResource(mode.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun BalanceFdrDialog(
    input: String,
    error: UiText?,
    onInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.config_balance_fdr)) },
        text = {
            RcNumberField(
                label = stringResource(R.string.config_balance_fdr),
                value = input,
                onValueChange = onInputChange,
                decimal = true,
                error = error?.asString(),
                hint = stringResource(R.string.config_balance_fdr_hint)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.config_balance_fdr_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

// ============================================================
// ヘルパー
// ============================================================

/**
 * [ThemeMode] の表示名（S-11）。
 *
 * enum 自体は `:core:domain`（純 Kotlin）にあり `R` を参照できないので、
 * ラベルの対応表は `:app` 側のここに置く。when が網羅的なので、
 * 選択肢を増やすとコンパイルエラーで気付ける。
 */
private val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.DARK -> R.string.config_theme_dark
        ThemeMode.LIGHT -> R.string.config_theme_light
        ThemeMode.SYSTEM -> R.string.config_theme_system
    }

/** エクスポートのデフォルトファイル名。例: rcgear-export-20260702.json */
private fun defaultExportFileName(): String {
    val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    return "rcgear-export-$date.json"
}
