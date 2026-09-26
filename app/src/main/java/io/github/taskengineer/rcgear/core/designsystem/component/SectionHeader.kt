package io.github.taskengineer.rcgear.core.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * 画面を区切るセクション見出し（U-1）。
 *
 * `ConfigScreen.SectionHeader` からの昇格。左右 16dp の画面余白を前提にしているので、
 * カードの中ではなく「画面直下に行が並ぶ」レイアウトで使う。
 *
 * @param showDivider 見出しの下に区切り線を引く
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
    )
    if (showDivider) {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Preview(name = "SectionHeader", showBackground = true)
@Composable
private fun SectionHeaderPreview() {
    ComponentPreview {
        SectionHeader(title = "DISPLAY")
        SectionHeader(title = "CALC TUNING", showDivider = false)
    }
}
