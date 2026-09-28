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
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.designsystem.component.Option
import io.github.taskengineer.rcgear.core.designsystem.component.RcNumberField
import io.github.taskengineer.rcgear.core.designsystem.component.RcSelectField
import io.github.taskengineer.rcgear.core.designsystem.component.RcTextField
import io.github.taskengineer.rcgear.core.ui.RcDetailScaffold
import io.github.taskengineer.rcgear.core.ui.asString
import io.github.taskengineer.rcgear.domain.model.ChassisCategory
import io.github.taskengineer.rcgear.domain.model.ChassisDrive

/**
 * ユーザー定義シャーシの作成・編集画面（F-5）。
 *
 * 同梱 DB に無い車種を自分で登録する。駆動方式とセンターデフの有無は
 * **シートの項目の出し分け**（ベルトテンション・デフオイル）に効くので、
 * 分からなければ「不明」のままにしておく — その場合は全ての項目が出る。
 */
@Composable
fun UserChassisEditScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserChassisEditViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    RcDetailScaffold(
        title = stringResource(
            if (state.isNew) R.string.user_chassis_title_new else R.string.user_chassis_title
        ),
        onNavigateBack = onNavigateBack,
        events = viewModel.events,
        isLoading = state.isLoading,
        modifier = modifier
    ) {
        RcTextField(
            label = stringResource(R.string.user_chassis_maker),
            value = state.makerInput,
            onValueChange = viewModel::onMakerChange,
            hint = stringResource(R.string.user_chassis_maker_hint)
        )
        RcTextField(
            label = stringResource(R.string.user_chassis_name),
            value = state.nameInput,
            onValueChange = viewModel::onNameChange
        )
        RcNumberField(
            label = stringResource(R.string.field_internal_ratio),
            value = state.ratioInput,
            onValueChange = viewModel::onRatioChange,
            decimal = true
        )
        RcNumberField(
            label = stringResource(R.string.field_tire_mm),
            value = state.tireInput,
            onValueChange = viewModel::onTireChange,
            unit = stringResource(R.string.unit_millimeter)
        )

        RcSelectField(
            label = stringResource(R.string.user_chassis_category),
            selectedKey = state.category.name,
            options = ChassisCategory.entries.map {
                Option(key = it.name, label = stringResource(it.labelRes))
            },
            onSelect = { key -> viewModel.onCategoryChange(ChassisCategory.fromKey(key)) }
        )

        // 駆動方式とセンターデフは項目の出し分けに効く。「不明」を既定にしておく
        RcSelectField(
            label = stringResource(R.string.user_chassis_drive),
            selectedKey = state.drive?.name ?: UNKNOWN_KEY,
            options = listOf(
                Option(key = UNKNOWN_KEY, label = stringResource(R.string.user_chassis_unknown))
            ) + ChassisDrive.entries.map {
                Option(key = it.name, label = stringResource(it.labelRes))
            },
            onSelect = { key -> viewModel.onDriveChange(ChassisDrive.fromKey(key)) }
        )
        RcSelectField(
            label = stringResource(R.string.user_chassis_center_diff),
            selectedKey = when (state.hasCenterDiff) {
                true -> YES_KEY
                false -> NO_KEY
                null -> UNKNOWN_KEY
            },
            options = listOf(
                Option(key = UNKNOWN_KEY, label = stringResource(R.string.user_chassis_unknown)),
                Option(key = YES_KEY, label = stringResource(R.string.user_chassis_yes)),
                Option(key = NO_KEY, label = stringResource(R.string.user_chassis_no))
            ),
            onSelect = { key ->
                viewModel.onCenterDiffChange(
                    when (key) {
                        YES_KEY -> true
                        NO_KEY -> false
                        else -> null
                    }
                )
            }
        )
        Text(
            text = stringResource(R.string.user_chassis_traits_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        RcTextField(
            label = stringResource(R.string.chassis_edit_note),
            value = state.noteInput,
            onValueChange = viewModel::onNoteChange,
            singleLine = false
        )

        state.errorMessage?.let {
            Text(
                text = it.asString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Button(
            onClick = viewModel::onSave,
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
                    text = stringResource(R.string.user_chassis_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::onDeleteConfirmDismiss,
            title = { Text(stringResource(R.string.user_chassis_delete_confirm_title)) },
            text = {
                Text(stringResource(R.string.user_chassis_delete_confirm_text, state.nameInput))
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

/** 選択肢の「不明」「あり」「なし」を表すキー。enum の値と衝突しない文字列にしてある */
private const val UNKNOWN_KEY = "__unknown"
private const val YES_KEY = "__yes"
private const val NO_KEY = "__no"
