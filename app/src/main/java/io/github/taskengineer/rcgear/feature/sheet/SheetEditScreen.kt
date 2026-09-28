package io.github.taskengineer.rcgear.feature.sheet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.ui.RcDetailScaffold
import io.github.taskengineer.rcgear.core.ui.asString
import io.github.taskengineer.rcgear.domain.schema.NumberFieldDef
import io.github.taskengineer.rcgear.domain.schema.TextFieldDef
import io.github.taskengineer.rcgear.feature.sheet.component.FieldEditor

/**
 * シートの値をセクション単位で編集する画面（G-4）。
 *
 * **並んでいるのはレジストリの項目そのまま。** 入力欄の選択は
 * [FieldEditor] が `FieldUi` で分岐するので、項目を足してもこの画面は変わらない
 * （G-7 の受け入れ条件）。
 *
 * グリッドのセクションでも編集は縦 1 列にする。入力欄は幅を食うので、
 * 2 列に並べると 1 つずつが狭くなって押しづらい。代わりに項目名へ列名を添える
 * （[fieldEditorLabel]）。
 */
@Composable
fun SheetEditScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SheetEditViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // 端末の戻るジェスチャでも未保存の確認を出す（画面上の ← と同じ扱い）
    BackHandler(enabled = state.isDirty) { viewModel.onBackRequest() }

    RcDetailScaffold(
        title = state.section?.let { stringResource(it.labelRes) } ?: state.sheetName,
        // イベント（保存・破棄の完了）は素直に戻り、矢印だけ確認を挟む
        onNavigateBack = onNavigateBack,
        events = viewModel.events,
        isLoading = state.isLoading || state.section == null,
        modifier = modifier,
        onBackClick = viewModel::onBackRequest
    ) {
        val section = state.section ?: return@RcDetailScaffold

        Text(
            text = state.sheetName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        section.visibleFields(state.traits).forEach { field ->
            FieldEditor(
                field = field,
                value = state.values[field.key],
                draft = state.drafts[field.key],
                error = state.errors[field.key]?.asString(),
                onNumberInput = { text ->
                    (field as? NumberFieldDef)?.let { viewModel.onNumberInput(it, text) }
                },
                onTextInput = { text ->
                    (field as? TextFieldDef)?.let { viewModel.onTextInput(it, text) }
                },
                onValueChange = { value -> viewModel.onValueChange(field, value) }
            )
        }

        Button(
            onClick = viewModel::onSave,
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.action_save))
        }
        if (!state.canSave) {
            Text(
                text = stringResource(R.string.sheet_edit_save_blocked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }

    if (state.showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::onDiscardDismiss,
            title = { Text(stringResource(R.string.sheet_edit_discard_title)) },
            text = { Text(stringResource(R.string.sheet_edit_discard_text)) },
            confirmButton = {
                TextButton(onClick = viewModel::onDiscardConfirm) {
                    Text(
                        text = stringResource(R.string.sheet_edit_discard),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDiscardDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
