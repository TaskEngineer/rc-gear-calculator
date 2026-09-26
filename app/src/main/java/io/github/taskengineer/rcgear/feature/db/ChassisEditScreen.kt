package io.github.taskengineer.rcgear.feature.db

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.taskengineer.rcgear.core.designsystem.component.RcCard
import io.github.taskengineer.rcgear.core.designsystem.component.RcNumberField
import io.github.taskengineer.rcgear.core.designsystem.component.RcTextField
import io.github.taskengineer.rcgear.core.ui.RcDetailScaffold
import io.github.taskengineer.rcgear.core.ui.formatRatio

/**
 * シャーシ編集画面（PLAN Step 10）。
 *
 * 標準値を残したまま、内部減速比 / タイヤ径 / 備考をフィールド単位で上書きする。
 * 「リセット」で上書きを破棄して標準値に戻す（PLAN 2.1.3）。
 */
@Composable
fun ChassisEditScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChassisEditViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val standard = state.standard

    RcDetailScaffold(
        title = standard?.name ?: "シャーシ編集",
        onNavigateBack = onNavigateBack,
        events = viewModel.events,
        isLoading = state.isLoading || standard == null,
        modifier = modifier,
        backContentDescription = "戻る"
    ) {
        if (standard != null) {
            // ---- 標準値の表示 ----
            RcCard(title = "標準値（同梱DB）", spacing = 4.dp) {
                Text(
                    text = "内部減速比 ${standard.internalRatio.formatRatio()}  /  " +
                        "タイヤ径 ${standard.defaultTireMm}mm",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                standard.note?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---- 編集フォーム ----
            RcNumberField(
                label = "内部減速比",
                value = state.ratioInput,
                onValueChange = viewModel::onRatioChange,
                decimal = true
            )
            RcNumberField(
                label = "タイヤ径",
                value = state.tireInput,
                onValueChange = viewModel::onTireChange,
                unit = "mm"
            )
            RcTextField(
                label = "備考",
                value = state.noteInput,
                onValueChange = viewModel::onNoteChange,
                singleLine = false
            )

            state.errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = viewModel::onSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存")
            }

            // リセットは上書きが存在するときだけ出す
            if (state.current?.isUserEdited == true) {
                OutlinedButton(
                    onClick = viewModel::onResetClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("標準値にリセット", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (state.showResetConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::onResetConfirmDismiss,
            title = { Text("リセットの確認") },
            text = { Text("「${standard?.name}」の上書きを破棄して標準値に戻しますか？") },
            confirmButton = {
                TextButton(onClick = viewModel::onResetConfirm) {
                    Text("リセット", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onResetConfirmDismiss) {
                    Text("キャンセル")
                }
            }
        )
    }
}
