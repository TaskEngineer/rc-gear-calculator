package io.github.taskengineer.rcgear.feature.setups

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * SETUPS タブ: 保存セッティング一覧画面。
 *
 * [プレースホルダー] Step 9 で以下を実装する:
 * - 保存セッティングの一覧表示（カード）
 * - カードタップで詳細画面（スナップショット差分表示）
 * - 「CALC に流し込む」遷移、削除機能
 */
@Composable
fun SetupsScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "SETUPS（Step 9 で実装）",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
