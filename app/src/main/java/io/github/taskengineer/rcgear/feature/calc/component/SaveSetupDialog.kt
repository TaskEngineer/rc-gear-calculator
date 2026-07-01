package io.github.taskengineer.rcgear.feature.calc.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
        title = { Text("セッティングを保存") },
        text = {
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text("名前") },
                placeholder = { Text("例: TT-02 サーキット用") },
                singleLine = true,
                isError = state.errorMessage != null,
                supportingText = {
                    state.errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !state.isSaving
            ) {
                Text(if (state.isSaving) "保存中..." else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isSaving) {
                Text("キャンセル")
            }
        }
    )
}
