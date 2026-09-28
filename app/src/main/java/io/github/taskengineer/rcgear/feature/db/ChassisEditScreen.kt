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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.designsystem.component.RcCard
import io.github.taskengineer.rcgear.core.designsystem.component.RcNumberField
import io.github.taskengineer.rcgear.core.designsystem.component.RcTextField
import io.github.taskengineer.rcgear.core.ui.RcDetailScaffold
import io.github.taskengineer.rcgear.core.ui.asString
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
        title = standard?.name ?: stringResource(R.string.chassis_edit_title),
        onNavigateBack = onNavigateBack,
        events = viewModel.events,
        isLoading = state.isLoading || standard == null,
        modifier = modifier
    ) {
        if (standard != null) {
            // ---- 標準値の表示 ----
            RcCard(title = stringResource(R.string.chassis_edit_standard_title), spacing = 4.dp) {
                Text(
                    text = stringResource(
                        R.string.chassis_edit_standard_summary,
                        standard.internalRatio.formatRatio(),
                        standard.defaultTireMm
                    ),
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
            // エラーは入力のたびに欄の下へ出し、残っている間は保存させない（BUG-7）
            RcNumberField(
                label = stringResource(R.string.field_internal_ratio),
                value = state.ratioInput,
                onValueChange = viewModel::onRatioChange,
                error = state.ratioError?.asString(),
                decimal = true
            )
            RcNumberField(
                label = stringResource(R.string.field_tire_mm),
                value = state.tireInput,
                onValueChange = viewModel::onTireChange,
                error = state.tireError?.asString(),
                unit = stringResource(R.string.unit_millimeter)
            )
            RcTextField(
                label = stringResource(R.string.chassis_edit_note),
                value = state.noteInput,
                onValueChange = viewModel::onNoteChange,
                singleLine = false
            )

            Button(
                onClick = viewModel::onSave,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.action_save))
            }

            // リセットは上書きが存在するときだけ出す
            if (state.current?.isUserEdited == true) {
                OutlinedButton(
                    onClick = viewModel::onResetClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.chassis_edit_reset),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (state.showResetConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::onResetConfirmDismiss,
            title = { Text(stringResource(R.string.chassis_edit_reset_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.chassis_edit_reset_confirm_text,
                        standard?.name.orEmpty()
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::onResetConfirm) {
                    Text(
                        text = stringResource(R.string.chassis_edit_reset),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onResetConfirmDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
