package io.github.taskengineer.rcgear.feature.calc

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CALC タブ: メイン計算画面（PLAN Step 8 / 仕上げは Step 12）。
 *
 * 構成（上から）:
 * 1. シャーシ選択カード（タップでボトムシート）
 * 2. メインHUD（最高速大型表示）
 * 3. 派生メトリック（1次減速比 / FDR / 電圧 / RPM）
 * 4. セッティング傾向バー
 * 5. ギア表示（Canvas、回転アニメーション）
 * 6. スライダー入力 × 5
 * + 保存 FAB / 画像エクスポート FAB / 保存ダイアログ / スナックバー
 *
 * 画像エクスポート（Step 12）:
 * 結果エリア（1〜5）を GraphicsLayer に記録し、PNG として SAF 経由で保存する。
 */
@Composable
fun CalcScreen(
    modifier: Modifier = Modifier,
    viewModel: CalcViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 保存成功メッセージをスナックバーで表示する（1回だけ）
    LaunchedEffect(state.savedMessage) {
        state.savedMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onSavedMessageShown()
        }
    }

    // ---- 画像エクスポート（Step 12） ----
    // 結果エリアの描画内容を記録する GraphicsLayer。
    // 描画のたびに record されるので、保存時点の最新の見た目が取れる。
    val captureLayer = rememberGraphicsLayer()

    val imageExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        uri?.let {
            scope.launch {
                val message = try {
                    saveLayerAsPng(captureLayer, it, context)
                    "画像を保存しました"
                } catch (e: Exception) {
                    "画像の保存に失敗しました: ${e.message}"
                }
                snackbarHostState.showSnackbar(message)
            }
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
                // ---- 結果エリア（画像エクスポートのキャプチャ対象） ----
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawWithContent {
                            captureLayer.record {
                                this@drawWithContent.drawContent()
                            }
                            drawLayer(captureLayer)
                        }
                        // 透過PNGにならないよう背景色を敷く
                        .background(MaterialTheme.colorScheme.background),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ChassisSelectorCard(
                        selected = state.selectedChassis,
                        onClick = viewModel::onChassisCardClick
                    )

                    SpeedHud(
                        topSpeedKmh = state.result?.topSpeedKmh,
                        topSpeedMph = state.result?.topSpeedMph,
                        showMph = state.showMphAlongside,
                        animationEnabled = state.animationEnabled
                    )

                    MetricsGrid(result = state.result)

                    BalanceBar(
                        balancePct = state.result?.balanceIndicatorPct,
                        animationEnabled = state.animationEnabled
                    )

                    GearDiagram(
                        pinion = state.pinion,
                        spur = state.spur,
                        animationEnabled = state.animationEnabled
                    )
                }

                SliderSection(state = state, viewModel = viewModel)

                // FAB に隠れないよう下部に余白を確保
                Spacer(modifier = Modifier.height(72.dp))
            }
        }

        // FAB 群: シャーシ選択済みのときだけ表示
        if (state.selectedChassis != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = { imageExportLauncher.launch(defaultImageFileName()) }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoCamera,
                        contentDescription = "画像として保存"
                    )
                }
                ExtendedFloatingActionButton(
                    onClick = viewModel::onSaveClick,
                    icon = { Icon(Icons.Filled.Save, contentDescription = null) },
                    text = { Text("保存") }
                )
            }
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
 * GraphicsLayer の内容を PNG として Uri に書き出す。
 * toImageBitmap はメインスレッドで呼ぶ必要があるが、圧縮と書き込みは IO で行う。
 */
private suspend fun saveLayerAsPng(
    layer: GraphicsLayer,
    uri: Uri,
    context: android.content.Context
) {
    val bitmap = layer.toImageBitmap().asAndroidBitmap()
    withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        } ?: throw IllegalStateException("出力先を開けませんでした")
    }
}

/** 画像エクスポートのデフォルトファイル名。例: rcgear-20260702-1530.png */
private fun defaultImageFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
    return "rcgear-$stamp.png"
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
