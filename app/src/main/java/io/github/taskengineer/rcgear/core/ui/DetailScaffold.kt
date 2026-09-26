package io.github.taskengineer.rcgear.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.taskengineer.rcgear.core.designsystem.component.RcTopAppBar
import kotlinx.coroutines.flow.Flow

/**
 * 派生画面（詳細・編集）の外枠（U-2）。
 *
 * 詳細画面は 3 つとも次の形をしていて、同じコードが写されていた:
 *  1. `Scaffold` + 戻る矢印付き `TopAppBar`
 *  2. 読込中はスピナー、読み込めたら縦スクロールの `Column`（余白 16dp / 行間 12dp）
 *  3. 完了・対象なしで前の画面へ戻る `LaunchedEffect`
 *
 * 3 は [ScreenEvent] の購読としてここに閉じ込めた。画面側は
 * 「タイトル・読込中か・中身」だけを渡す。
 *
 * @param events ViewModel が流す画面イベント。[ScreenEvent.NavigateBack] を受けると戻る
 * @param isLoading true の間はスピナーを出し、[content] を組まない
 */
@Composable
fun RcDetailScaffold(
    title: String,
    onNavigateBack: () -> Unit,
    events: Flow<ScreenEvent>,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    backContentDescription: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    ObserveEvents(events = events, onNavigateBack = onNavigateBack)

    Scaffold(
        modifier = modifier,
        topBar = {
            RcTopAppBar(
                title = title,
                onNavigateBack = onNavigateBack,
                backContentDescription = backContentDescription,
                actions = actions
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}
