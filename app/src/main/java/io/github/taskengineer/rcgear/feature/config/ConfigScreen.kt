package io.github.taskengineer.rcgear.feature.config

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * CONFIG タブ: 設定画面。
 *
 * [プレースホルダー] Step 11 で以下を実装する:
 * - セクション分け（DISPLAY / DATA / CALC_TUNING / ABOUT）
 * - テーマ選択ダイアログ、基準FDR入力ダイアログ
 * - データ書出/読込（SAF連携）、全データ削除
 */
@Composable
fun ConfigScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "CONFIG（Step 11 で実装）",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
