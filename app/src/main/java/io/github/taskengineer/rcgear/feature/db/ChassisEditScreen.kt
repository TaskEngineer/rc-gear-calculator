package io.github.taskengineer.rcgear.feature.db

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.util.Locale

/**
 * シャーシ編集画面（PLAN Step 10）。
 *
 * 標準値を残したまま、内部減速比 / タイヤ径 / 備考をフィールド単位で上書きする。
 * 「リセット」で上書きを破棄して標準値に戻す（PLAN 2.1.3）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChassisEditScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChassisEditViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // 保存・リセット完了 or 対象が見つからない場合は一覧へ戻る
    LaunchedEffect(state.isDone, state.notFound) {
        if (state.isDone || state.notFound) onNavigateBack()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.standard?.name ?: "シャーシ編集") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "戻る"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        val standard = state.standard
        if (state.isLoading || standard == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ---- 標準値の表示 ----
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "標準値（同梱DB）",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format(
                                Locale.US,
                                "内部減速比 %.2f  /  タイヤ径 %dmm",
                                standard.internalRatio, standard.defaultTireMm
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
                }

                // ---- 編集フォーム ----
                OutlinedTextField(
                    value = state.ratioInput,
                    onValueChange = viewModel::onRatioChange,
                    label = { Text("内部減速比") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.tireInput,
                    onValueChange = viewModel::onTireChange,
                    label = { Text("タイヤ径 [mm]") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.noteInput,
                    onValueChange = viewModel::onNoteChange,
                    label = { Text("備考") },
                    modifier = Modifier.fillMaxWidth()
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
    }

    if (state.showResetConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::onResetConfirmDismiss,
            title = { Text("リセットの確認") },
            text = { Text("「${state.standard?.name}」の上書きを破棄して標準値に戻しますか？") },
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
