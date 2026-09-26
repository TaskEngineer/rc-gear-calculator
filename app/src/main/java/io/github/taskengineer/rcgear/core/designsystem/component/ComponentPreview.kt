package io.github.taskengineer.rcgear.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.taskengineer.rcgear.core.designsystem.theme.RcGearTheme
import io.github.taskengineer.rcgear.domain.model.ThemeMode

/**
 * `@Preview` 用の共通ラッパ（U-1）。
 *
 * 部品ごとに `RcGearTheme { Surface { Column { ... } } }` を書き写すのをやめるためのもの。
 * テーマを通すのが目的なので、プレビュー以外から呼ばないこと（`internal`）。
 *
 * @param themeMode ダーク / ライトの見え方を切り替える。同じ部品に 2 つプレビューを置ける
 */
@Composable
internal fun ComponentPreview(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable ColumnScope.() -> Unit
) {
    RcGearTheme(themeMode = themeMode) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}
