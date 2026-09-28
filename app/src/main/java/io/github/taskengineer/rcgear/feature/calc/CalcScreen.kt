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
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.designsystem.component.MetricsGrid
import io.github.taskengineer.rcgear.core.designsystem.component.RcCard
import io.github.taskengineer.rcgear.core.designsystem.component.RcSlider
import io.github.taskengineer.rcgear.core.ui.ChassisSelectBottomSheet
import io.github.taskengineer.rcgear.core.ui.Share
import io.github.taskengineer.rcgear.core.ui.asString
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput
import io.github.taskengineer.rcgear.feature.calc.component.BalanceBar
import io.github.taskengineer.rcgear.feature.calc.component.ChassisSelectorCard
import io.github.taskengineer.rcgear.feature.calc.component.GearDiagram
import io.github.taskengineer.rcgear.feature.calc.component.SpeedHud
import io.github.taskengineer.rcgear.feature.calc.component.gearMetrics
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

    // スナックバーに出すのは画像書き出しの結果と、シートへの反映結果（G-5）。
    val message = state.message
    if (message != null) {
        val text = message.asString()
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(text)
            viewModel.onMessageShown()
        }
    }

    // ---- 画像エクスポート（Step 12） ----
    // 結果エリアの描画内容を記録する GraphicsLayer。
    // 描画のたびに record されるので、保存時点の最新の見た目が取れる。
    val captureLayer = rememberGraphicsLayer()
    val shareChooserTitle = stringResource(R.string.calc_share_chooser)
    val shareFailedMessage = stringResource(R.string.calc_share_failed)

    val imageExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        uri?.let {
            scope.launch {
                val message = try {
                    saveLayerAsPng(captureLayer, it, context)
                    context.getString(R.string.calc_image_saved)
                } catch (e: Exception) {
                    context.getString(R.string.calc_image_save_failed, e.message.orEmpty())
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

                    // シートから流し込まれて開いた場合だけ、出どころと書き戻し口を出す（G-5）
                    state.sheetContext?.let { context ->
                        SheetContextCard(
                            context = context,
                            onApplyClick = viewModel::onApplyToSheet
                        )
                    }

                    SpeedHud(
                        topSpeedKmh = state.result?.topSpeedKmh,
                        topSpeedMph = state.result?.topSpeedMph,
                        showMph = state.showMphAlongside,
                        animationEnabled = state.animationEnabled
                    )

                    MetricsGrid(metrics = gearMetrics(state.result))

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
                // 共有（F-4）: 保存（SAF）とは別操作。ピットでは投げるほうが多い
                SmallFloatingActionButton(
                    onClick = {
                        scope.launch {
                            val bitmap = captureLayer.toImageBitmap().asAndroidBitmap()
                            val shared = Share.image(
                                context = context,
                                bitmap = bitmap,
                                fileName = defaultImageFileName(),
                                chooserTitle = shareChooserTitle
                            )
                            if (!shared) snackbarHostState.showSnackbar(shareFailedMessage)
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = stringResource(R.string.calc_share_image)
                    )
                }
                SmallFloatingActionButton(
                    onClick = { imageExportLauncher.launch(defaultImageFileName()) }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoCamera,
                        contentDescription = stringResource(R.string.calc_export_image)
                    )
                }
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
}

/**
 * 流し込み元のシートを示すカードと、書き戻しボタン（G-5）。
 *
 * 書き戻しは「このシートに反映」の 1 タップで確定する（確認を挟まない）。
 * 上書きするのはギアの 5 項目だけで、シート側の履歴が消えるわけではないため。
 */
@Composable
private fun SheetContextCard(
    context: CalcSheetContext,
    onApplyClick: () -> Unit
) {
    RcCard(spacing = 4.dp) {
        Text(
            text = stringResource(
                R.string.calc_sheet_context,
                context.carName,
                context.sheetName
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!context.isComplete) {
            Text(
                text = stringResource(R.string.calc_sheet_incomplete),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        OutlinedButton(
            onClick = onApplyClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.calc_apply_to_sheet))
        }
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
        } ?: error(context.getString(R.string.io_error_open_output))
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
    RcCard(title = stringResource(R.string.calc_input_section), spacing = 4.dp) {
        RcSlider(
            label = stringResource(R.string.field_pinion),
            value = state.pinion,
            onValueChange = viewModel::onPinionChange,
            onValueChangeFinished = viewModel::onSliderChangeFinished,
            valueRange = GearCalculationInput.MIN_PINION..GearCalculationInput.MAX_PINION,
            unit = stringResource(R.string.unit_teeth)
        )
        RcSlider(
            label = stringResource(R.string.field_spur),
            value = state.spur,
            onValueChange = viewModel::onSpurChange,
            onValueChangeFinished = viewModel::onSliderChangeFinished,
            valueRange = GearCalculationInput.MIN_SPUR..GearCalculationInput.MAX_SPUR,
            unit = stringResource(R.string.unit_teeth)
        )
        RcSlider(
            label = stringResource(R.string.field_motor_kv),
            value = state.kv,
            onValueChange = viewModel::onKvChange,
            onValueChangeFinished = viewModel::onSliderChangeFinished,
            valueRange = GearCalculationInput.MIN_KV..GearCalculationInput.MAX_KV,
            step = GearCalculationInput.KV_STEP
        )
        RcSlider(
            label = stringResource(R.string.field_cells),
            value = state.cells,
            onValueChange = viewModel::onCellsChange,
            onValueChangeFinished = viewModel::onSliderChangeFinished,
            valueRange = GearCalculationInput.MIN_CELLS..GearCalculationInput.MAX_CELLS,
            unit = stringResource(R.string.unit_cells)
        )
        RcSlider(
            label = stringResource(R.string.field_tire_mm),
            value = state.tireMm,
            onValueChange = viewModel::onTireMmChange,
            onValueChangeFinished = viewModel::onSliderChangeFinished,
            valueRange = GearCalculationInput.MIN_TIRE_MM..GearCalculationInput.MAX_TIRE_MM,
            unit = stringResource(R.string.unit_millimeter)
        )
    }
}
