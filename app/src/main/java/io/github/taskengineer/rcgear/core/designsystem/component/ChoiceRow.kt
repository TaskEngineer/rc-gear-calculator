package io.github.taskengineer.rcgear.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * 設定画面などで使う「タップして選ぶ」行（U-1）。
 *
 * `ConfigScreen.ConfigRow` からの昇格。右端に現在値を出し、タップでダイアログを開く用途。
 *
 * @param onClick null ならタップ不可（バージョン表示のような読み取り専用行）
 * @param value   右端に出す現在値。null なら出さない
 * @param titleColor 破壊的な操作（全データ削除）を error 色にするため
 */
@Composable
fun ChoiceRow(
    title: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * on / off を切り替える行（U-1）。`ConfigScreen.SwitchRow` からの昇格。
 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Preview(name = "ChoiceRow / SwitchRow", showBackground = true)
@Composable
private fun ChoiceRowPreview() {
    ComponentPreview {
        SectionHeader(title = "DISPLAY")
        ChoiceRow(title = "テーマ", value = "ダーク", onClick = {})
        ChoiceRow(
            title = "基準 FDR",
            subtitle = "セッティング傾向バーの中央となる最終減速比",
            value = "7.0",
            onClick = {}
        )
        SwitchRow(title = "mph を併記", checked = true, onCheckedChange = {})
        ChoiceRow(
            title = "全データを削除",
            titleColor = MaterialTheme.colorScheme.error,
            onClick = {}
        )
        ChoiceRow(title = "バージョン", value = "0.1.0", onClick = null)
    }
}
