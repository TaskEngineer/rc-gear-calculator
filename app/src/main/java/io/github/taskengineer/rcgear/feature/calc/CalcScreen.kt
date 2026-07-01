package io.github.taskengineer.rcgear.feature.calc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput
import io.github.taskengineer.rcgear.feature.calc.component.BalanceBar
import io.github.taskengineer.rcgear.feature.calc.component.ChassisSelectBottomSheet
import io.github.taskengineer.rcgear.feature.calc.component.ChassisSelectorCard
import io.github.taskengineer.rcgear.feature.calc.component.GearDiagram
import io.github.taskengineer.rcgear.feature.calc.component.GearSlider
import io.github.taskengineer.rcgear.feature.calc.component.MetricsGrid
import io.github.taskengineer.rcgear.feature.calc.component.SaveSetupDialog
import io.github.taskengineer.rcgear.feature.calc.component.SpeedHud

/**
 * CALC タブ: メイン計算画面（PLAN Step 8）。
 *
 * 構成（上から）:
 * 1. シャーシ選択カード（タップでボトムシート）
 * 2. メインHUD（最高速大型表示）
 * 3. 派生メトリック（1次減速比 / FDR / 電圧 / RPM）
 * 4. セッティング傾向バー
 * 5. ギア表示（Canvas）
 * 6. スライダー入力 × 5
 * + 保存 FAB / 保存ダイアログ / スナックバー
 */
@Composable
fun CalcScreen(
    modifier: Modifier = Modifier,
    viewModel: CalcViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 保存成功メッセージをスナックバーで表示する（1回だけ）
    LaunchedEffect(state.savedMessage) {
        state.savedMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onSavedMessageShown()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ChassisSelectorCard(
                    selected = state.selectedChassis,
                    onClick = viewModel::onChassisCardClick
                )

                SpeedHud(
                    topSpeedKmh = state.result?.topSpeedKmh,
                    topSpeedMph = state.result?.topSpeedMph,
                    showMph = state.showMphAlongside
                )

                MetricsGrid(result = state.result)

                BalanceBar(balancePct = state.result?.balanceIndicatorPct)

                GearDiagram(pinion = state.pinion, spur = state.spur)

                SliderSection(state = state, viewModel = viewModel)

                // FAB に隠れないよう下部に余白を確保
                Spacer(modifier = Modifier.height(72.dp))
            }
        }

        // 保存 FAB: シャーシ選択済みのときだけ表示
        if (state.selectedChassis != null) {
            ExtendedFloatingActionButton(
                onClick = viewModel::onSaveClick,
                icon = { Icon(Icons.Filled.Save, contentDescription = null) },
                text = { Text("保存") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // ----- モーダル類 -----

    if (state.isChassisSheetOpen) {
        ChassisSelectBottomSheet(
            makers = state.makers,
            selectedChassisId = state.selectedChassis?.chassis?.id,
            onChassisSelected = viewModel::onChassisSelected,
            onDismiss = viewModel::onChassisSheetDismiss
        )
    }

    state.saveDialog?.let { dialog ->
        SaveSetupDialog(
            state = dialog,
            onNameChange = viewModel::onSaveDialogNameChange,
            onConfirm = viewModel::onSaveDialogConfirm,
            onDismiss = viewModel::onSaveDialogDismiss
        )
    }
}

/**
 * スライダー5本をカードにまとめたセクション。
 * 範囲・刻みは GearCalculationInput の定数（= Web 版と同一）を使う。
 */
@Composable
private fun SliderSection(
    state: CalcUiState,
    viewModel: CalcViewModel
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "入力",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            GearSlider(
                label = "ピニオン",
                value = state.pinion,
                onValueChange = viewModel::onPinionChange,
                onValueChangeFinished = viewModel::onSliderChangeFinished,
                valueRange = GearCalculationInput.MIN_PINION..GearCalculationInput.MAX_PINION,
                unit = "T"
            )
            GearSlider(
                label = "スパー",
                value = state.spur,
                onValueChange = viewModel::onSpurChange,
                onValueChangeFinished = viewModel::onSliderChangeFinished,
                valueRange = GearCalculationInput.MIN_SPUR..GearCalculationInput.MAX_SPUR,
                unit = "T"
            )
            GearSlider(
                label = "モーターKV",
                value = state.kv,
                onValueChange = viewModel::onKvChange,
                onValueChangeFinished = viewModel::onSliderChangeFinished,
                valueRange = GearCalculationInput.MIN_KV..GearCalculationInput.MAX_KV,
                step = GearCalculationInput.KV_STEP
            )
            GearSlider(
                label = "セル数",
                value = state.cells,
                onValueChange = viewModel::onCellsChange,
                onValueChangeFinished = viewModel::onSliderChangeFinished,
                valueRange = GearCalculationInput.MIN_CELLS..GearCalculationInput.MAX_CELLS,
                unit = "S"
            )
            GearSlider(
                label = "タイヤ径",
                value = state.tireMm,
                onValueChange = viewModel::onTireMmChange,
                onValueChangeFinished = viewModel::onSliderChangeFinished,
                valueRange = GearCalculationInput.MIN_TIRE_MM..GearCalculationInput.MAX_TIRE_MM,
                unit = "mm"
            )
        }
    }
}
