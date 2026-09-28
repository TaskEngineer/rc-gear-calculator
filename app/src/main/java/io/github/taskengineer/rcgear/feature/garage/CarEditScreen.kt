package io.github.taskengineer.rcgear.feature.garage

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
import io.github.taskengineer.rcgear.core.designsystem.component.RcTextField
import io.github.taskengineer.rcgear.core.designsystem.component.SwitchRow
import io.github.taskengineer.rcgear.core.ui.ChassisSelectBottomSheet
import io.github.taskengineer.rcgear.core.ui.RcDetailScaffold

/**
 * 車の新規作成・編集画面（G-1）。
 *
 * 名前・シャーシ・備考の 3 点だけ。セッティングは車ではなくシート（G-2 以降）が持つ。
 * 削除とアーカイブは編集のときだけ出す。
 */
@Composable
fun CarEditScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CarEditViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    RcDetailScaffold(
        title = stringResource(
            if (state.isNew) R.string.car_edit_title_new else R.string.car_edit_title
        ),
        onNavigateBack = onNavigateBack,
        events = viewModel.events,
        isLoading = state.isLoading,
        modifier = modifier
    ) {
        RcTextField(
            label = stringResource(R.string.car_edit_name),
            value = state.nameInput,
            onValueChange = viewModel::onNameChange,
            hint = stringResource(R.string.car_edit_name_hint)
        )

        // シャーシは選択肢が 40 台以上あるのでドロップダウンにせず、
        // CALC 画面と同じボトムシート（core/ui）で選ぶ
        RcCard(
            title = stringResource(R.string.car_edit_chassis_section),
            onClick = viewModel::onChassisCardClick,
            spacing = 2.dp
        ) {
            val selected = state.selectedChassis
            if (selected == null) {
                Text(
                    text = stringResource(R.string.car_edit_chassis_unselected),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = selected.makerName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = selected.chassis.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        RcTextField(
            label = stringResource(R.string.car_edit_note),
            value = state.noteInput,
            onValueChange = viewModel::onNoteChange,
            singleLine = false
        )

        if (!state.isNew) {
            SwitchRow(
                title = stringResource(R.string.car_edit_archive),
                subtitle = stringResource(R.string.car_edit_archive_hint),
                checked = state.isArchived,
                onCheckedChange = viewModel::onArchivedChange
            )
        }

        // 必須（名前・シャーシ）が埋まるまで保存させない（BUG-7 と同じ規約）
        Button(
            onClick = viewModel::onSave,
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.action_save))
        }

        if (!state.isNew) {
            OutlinedButton(
                onClick = viewModel::onDeleteClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.car_edit_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (state.isChassisSheetOpen) {
        ChassisSelectBottomSheet(
            makers = state.makers,
            selectedChassisId = state.selectedChassis?.chassis?.id,
            onChassisSelected = viewModel::onChassisSelected,
            onDismiss = viewModel::onChassisSheetDismiss
        )
    }

    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::onDeleteConfirmDismiss,
            title = { Text(stringResource(R.string.car_edit_delete_confirm_title)) },
            text = {
                Text(stringResource(R.string.car_edit_delete_confirm_text, state.nameInput))
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
