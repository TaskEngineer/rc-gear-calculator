package io.github.taskengineer.rcgear.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 画面内のひとかたまりを囲むカード（U-1）。
 *
 * これまで各画面が `Card { Column(Modifier.padding(16.dp), spacedBy(6.dp)) { ... } }` を
 * 書き写していた。余白と行間をここに 1 回だけ書く。
 *
 * @param title    カード上部に出す見出し。null なら見出し無し
 * @param onClick  カード全体をタップ可能にする。null なら非タップ
 * @param spacing  子要素の縦間隔
 */
@Composable
fun RcCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    onClick: (() -> Unit)? = null,
    spacing: Dp = 6.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            content()
        }
    }

    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth()) { body() }
    } else {
        Card(modifier = modifier.fillMaxWidth()) { body() }
    }
}

@Preview(name = "RcCard", showBackground = true)
@Composable
private fun RcCardPreview() {
    ComponentPreview {
        RcCard(title = "計算結果") {
            LabeledRow(label = "最高速", value = "48.2 km/h")
            LabeledRow(label = "最終減速比 FDR", value = "6.24")
        }
        RcCard(onClick = {}) {
            Text("タップできるカード", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
