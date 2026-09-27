package io.github.taskengineer.rcgear.feature.garage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.designsystem.component.RcCard
import io.github.taskengineer.rcgear.core.designsystem.component.RcTextField
import io.github.taskengineer.rcgear.core.ui.RcDetailScaffold
import io.github.taskengineer.rcgear.core.ui.asString
import io.github.taskengineer.rcgear.core.ui.formatDate
import io.github.taskengineer.rcgear.core.ui.formatRatio

/**
 * 車詳細 = その車のセッティングシート一覧（G-2）。
 *
 * シートは数十枚の規模なので `LazyColumn` にせず [RcDetailScaffold] の
 * 縦スクロール Column にそのまま並べる（scrollable の入れ子は実行時に落ちる）。
 */
@Composable
fun CarDetailScreen(
    onNavigateBack: () -> Unit,
    onEditCarClick: (String) -> Unit,
    onSheetClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CarDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    RcDetailScaffold(
        title = state.car?.name.orEmpty(),
        onNavigateBack = onNavigateBack,
        events = viewModel.events,
        isLoading = state.isLoading,
        modifier = modifier,
        actions = {
            IconButton(onClick = { onEditCarClick(state.carId) }) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.car_detail_edit_car)
                )
            }
        }
    ) {
        // ---- 車のヘッダ ----
        val chassis = state.chassis
        RcCard(spacing = 2.dp) {
            Text(
                text = chassis?.let { entry ->
                    listOf(entry.makerName, entry.name).filter { it.isNotBlank() }.joinToString(" ")
                } ?: stringResource(R.string.garage_chassis_unknown, state.car?.chassisId.orEmpty()),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (chassis != null) {
                Text(
                    text = stringResource(
                        R.string.car_detail_chassis_summary,
                        chassis.internalRatio.formatRatio(),
                        chassis.defaultTireMm
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.car?.note?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---- シート一覧 ----
        Text(
            text = stringResource(R.string.car_detail_sheets_section, state.sheets.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )

        val newSheetName = stringResource(R.string.car_detail_new_sheet_default_name)
        Button(
            onClick = { viewModel.onNewSheetClick(newSheetName) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.car_detail_new_sheet))
        }

        if (state.sheets.isEmpty()) {
            Text(
                text = stringResource(R.string.car_detail_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            state.sheets.forEach { sheet ->
                val duplicateName =
                    stringResource(R.string.car_detail_duplicate_default_name, sheet.name)
                SheetCard(
                    sheet = sheet,
                    onClick = { onSheetClick(sheet.id) },
                    onDuplicateClick = { viewModel.onDuplicateClick(sheet.id, duplicateName) }
                )
            }
        }
    }

    state.sheetDialog?.let { dialog ->
        SheetNameDialog(
            dialog = dialog,
            onNameChange = viewModel::onSheetNameChange,
            onConfirm = viewModel::onSheetDialogConfirm,
            onDismiss = viewModel::onSheetDialogDismiss
        )
    }
}

@Composable
private fun SheetCard(
    sheet: SheetListItem,
    onClick: () -> Unit,
    onDuplicateClick: () -> Unit
) {
    RcCard(onClick = onClick, spacing = 2.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (sheet.isFavorite) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    Text(
                        text = sheet.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                // 走行日とコース名は G-4（ヘッダ編集）で入るまで空欄のことが多い。
                // その間は「更新日」を出して、どのシートを最後に触ったか分かるようにする
                val subtitle = listOfNotNull(
                    sheet.sessionDate?.formatDate(),
                    sheet.trackName?.takeIf { it.isNotBlank() }
                ).takeIf { it.isNotEmpty() }?.joinToString(stringResource(R.string.list_separator))
                    ?: stringResource(R.string.car_detail_updated_at, sheet.updatedAt.formatDate())
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                sheet.baselineName?.let {
                    Text(
                        text = stringResource(R.string.car_detail_baseline, it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(onClick = onDuplicateClick) {
                Text(stringResource(R.string.car_detail_duplicate))
            }
        }
    }
}

/**
 * シート名を決めるダイアログ。新規と複製で同じものを使い、文言だけ出し分ける。
 */
@Composable
private fun SheetNameDialog(
    dialog: SheetDialogState,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDuplicate = dialog.sourceSheetId != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isDuplicate) {
                        R.string.car_detail_duplicate_dialog_title
                    } else {
                        R.string.car_detail_new_sheet_dialog_title
                    }
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isDuplicate) {
                        stringResource(
                            R.string.car_detail_duplicate_dialog_text,
                            dialog.sourceSheetName.orEmpty()
                        )
                    } else {
                        stringResource(R.string.car_detail_new_sheet_dialog_text)
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                RcTextField(
                    label = stringResource(R.string.car_detail_sheet_name),
                    value = dialog.nameInput,
                    onValueChange = onNameChange,
                    error = dialog.error?.asString()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
