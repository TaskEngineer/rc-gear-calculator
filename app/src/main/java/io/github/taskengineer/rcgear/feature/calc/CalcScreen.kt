package io.github.taskengineer.rcgear.feature.calc

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * CALC タブ: メイン計算画面。
 *
 * [プレースホルダー] Step 8 で以下を実装する:
 * - シャーシ選択ボトムシート
 * - スライダー入力（ピニオン / スパー / KV / セル / タイヤ径）
 * - メインHUD（最高速大型表示）、派生メトリック、傾向バー
 * - 保存ダイアログ、ギアSVG描画
 */
@Composable
fun CalcScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "CALC（Step 8 で実装）",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
