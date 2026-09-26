package io.github.taskengineer.rcgear.core.designsystem.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/**
 * 派生画面（詳細・編集）のトップバー（U-1）。
 *
 * `onNavigateBack` を渡せば戻るボタンが付く。3 画面が同じ
 * 「TopAppBar + 戻る矢印 + contentDescription」を書き写していたのをここに集約した。
 *
 * @param backContentDescription 戻るボタンの読み上げ文。呼び出し側が文言を持つのは、
 *   designsystem を `R.string` に依存させないため
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RcTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    backContentDescription: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = { Text(title) },
        modifier = modifier,
        navigationIcon = {
            if (onNavigateBack != null) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = backContentDescription
                    )
                }
            }
        },
        actions = actions
    )
}

@Preview(name = "RcTopAppBar", showBackground = true)
@Composable
private fun RcTopAppBarPreview() {
    ComponentPreview {
        RcTopAppBar(title = "計算")
        RcTopAppBar(
            title = "Rd1 予選セット",
            onNavigateBack = {},
            backContentDescription = "戻る"
        )
    }
}
