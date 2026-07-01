package io.github.taskengineer.rcgear.feature.db

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * DB タブ: シャーシDB管理画面。
 *
 * [プレースホルダー] Step 10 で以下を実装する:
 * - メーカーごとのグルーピング表示、フィルタータブ
 * - ユーザー編集済みエントリの視覚的識別
 * - シャーシ編集画面への遷移、上書き / リセット機能
 */
@Composable
fun DbScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "DB（Step 10 で実装）",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
