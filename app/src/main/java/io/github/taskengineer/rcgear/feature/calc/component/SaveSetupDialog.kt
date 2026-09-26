package io.github.taskengineer.rcgear.feature.calc.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.ui.asString
import io.github.taskengineer.rcgear.feature.calc.SaveDialogState

/**
 * セッティング保存ダイアログ（PLAN 5.2）。
 * 名前を入力して保存する。バリデーションエラーはダイアログ内に表示する。
 */
@Composable
fun SaveSetupDialog(
    state: SaveDialogState,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calc_save_dialog_title)) },
        text = {
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.calc_save_dialog_name_label)) },
                placeholder = {
                    Text(stringResource(R.string.calc_save_dialog_name_placeholder))
                },
                singleLine = true,
                isError = state.errorMessage != null,
                supportingText = {
                    state.errorMessage?.let {
                        Text(it.asString(), color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !state.isSaving
            ) {
                Text(
                    stringResource(
                        if (state.isSaving) R.string.action_saving else R.string.action_save
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isSaving) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
