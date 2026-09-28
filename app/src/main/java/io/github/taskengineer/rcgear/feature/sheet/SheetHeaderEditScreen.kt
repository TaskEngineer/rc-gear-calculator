package io.github.taskengineer.rcgear.feature.sheet

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.designsystem.component.ChoiceRow
import io.github.taskengineer.rcgear.core.designsystem.component.Option
import io.github.taskengineer.rcgear.core.designsystem.component.RcCard
import io.github.taskengineer.rcgear.core.designsystem.component.RcNumberField
import io.github.taskengineer.rcgear.core.designsystem.component.RcSelectField
import io.github.taskengineer.rcgear.core.designsystem.component.RcTextField
import io.github.taskengineer.rcgear.core.ui.RcDetailScaffold
import io.github.taskengineer.rcgear.core.ui.asString
import io.github.taskengineer.rcgear.core.ui.formatDate

/**
 * シートのヘッダ（名前・走行条件・ベースライン・備考）の編集画面（G-4）。
 *
 * ここで入れた走行日がシート一覧の並び順（走行日の新しい順）を決め、
 * ベースラインが差分の軸 B（前回のセットから何を変えたか。G-6）の相手になる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetHeaderEditScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SheetHeaderEditViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    RcDetailScaffold(
        title = stringResource(R.string.sheet_header_edit_title),
        onNavigateBack = onNavigateBack,
        events = viewModel.events,
        isLoading = state.isLoading,
        modifier = modifier
    ) {
        RcTextField(
            label = stringResource(R.string.car_detail_sheet_name),
            value = state.nameInput,
            onValueChange = viewModel::onNameChange
        )

        // ---- 走行条件 ----
        RcCard(title = stringResource(R.string.sheet_detail_conditions_section), spacing = 8.dp) {
            ChoiceRow(
                title = stringResource(R.string.sheet_field_session_date),
                value = state.sessionDate?.formatDate() ?: stringResource(R.string.value_unset),
                onClick = viewModel::onDatePickerOpen
            )
            if (state.sessionDate != null) {
                TextButton(onClick = viewModel::onDateClear) {
                    Text(stringResource(R.string.sheet_header_date_clear))
                }
            }
            RcTextField(
                label = stringResource(R.string.sheet_field_track_name),
                value = state.trackInput,
                onValueChange = viewModel::onTrackChange
            )
            RcTextField(
                label = stringResource(R.string.sheet_field_surface),
                value = state.surfaceInput,
                onValueChange = viewModel::onSurfaceChange,
                hint = stringResource(R.string.sheet_header_surface_hint)
            )
            // エラーは入力のたびに欄の下へ出す（BUG-7 と同じ規約）
            RcNumberField(
                label = stringResource(R.string.sheet_field_air_temp),
                value = state.airTempInput,
                onValueChange = viewModel::onAirTempChange,
                error = state.airTempError?.asString(),
                decimal = true,
                unit = stringResource(R.string.unit_celsius)
            )
            RcNumberField(
                label = stringResource(R.string.sheet_field_track_temp),
                value = state.trackTempInput,
                onValueChange = viewModel::onTrackTempChange,
                error = state.trackTempError?.asString(),
                decimal = true,
                unit = stringResource(R.string.unit_celsius)
            )
            RcNumberField(
                label = stringResource(R.string.sheet_field_humidity),
                value = state.humidityInput,
                onValueChange = viewModel::onHumidityChange,
                error = state.humidityError?.asString(),
                unit = stringResource(R.string.unit_percent)
            )
            RcNumberField(
                label = stringResource(R.string.sheet_field_best_lap),
                value = state.bestLapInput,
                onValueChange = viewModel::onBestLapChange,
                error = state.bestLapError?.asString(),
                decimal = true,
                unit = stringResource(R.string.unit_second)
            )
        }

        // ---- ベースライン ----
        RcSelectField(
            label = stringResource(R.string.sheet_header_baseline),
            selectedKey = state.baselineId ?: BASELINE_NONE,
            options = listOf(
                Option(key = BASELINE_NONE, label = stringResource(R.string.sheet_header_baseline_none))
            ) + state.baselineCandidates.map { Option(key = it.id, label = it.name) },
            onSelect = { key -> viewModel.onBaselineSelect(key.takeIf { it != BASELINE_NONE }) }
        )
        Text(
            text = stringResource(R.string.sheet_header_baseline_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        RcTextField(
            label = stringResource(R.string.sheet_header_note),
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
    }

    if (state.isDatePickerOpen) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = state.sessionDate)
        DatePickerDialog(
            onDismissRequest = viewModel::onDatePickerDismiss,
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let(viewModel::onDatePicked)
                            ?: viewModel.onDatePickerDismiss()
                    }
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDatePickerDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** 「ベースラインなし」を表す選択肢のキー。シートの id と衝突しない値にしてある */
private const val BASELINE_NONE = ""
