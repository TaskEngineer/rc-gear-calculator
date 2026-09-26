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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.taskengineer.rcgear.R

/**
 * 派生画面（詳細・編集）のトップバー（U-1）。
 *
 * `onNavigateBack` を渡せば戻るボタンが付く。3 画面が同じ
 * 「TopAppBar + 戻る矢印 + contentDescription」を書き写していたのをここに集約した。
 *
 * 文言は原則として呼び出し側が渡す（[title]）。ただし **常に同じ a11y ラベル**は
 * 部品の中で `stringResource` する（S-11）。呼び出し側に「戻る」を毎回書かせると、
 * 書き忘れた画面だけ読み上げが無音になる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RcTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
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
                        contentDescription = stringResource(R.string.action_back)
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
        RcTopAppBar(title = "Rd1 予選セット", onNavigateBack = {})
    }
}
