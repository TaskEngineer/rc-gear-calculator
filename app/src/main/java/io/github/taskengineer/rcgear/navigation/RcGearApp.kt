package io.github.taskengineer.rcgear.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/**
 * アプリのルート Composable。
 * Scaffold + NavigationBar のレイアウトに NavHost を組み込む（PLAN Step 7）。
 *
 * - TopAppBar タイトル: 現在タブの日本語タイトル（PLAN 5.4）
 * - ボトムナビラベル: 英大文字 + 等幅（labelMedium が Roboto Mono に設定済み）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RcGearApp() {
    val navController = rememberNavController()

    // 現在の目的地からアクティブなタブを求める。
    // hierarchy を見るのは、将来 "setups/{setupId}" のような子画面にいる時も
    // 親タブ（SETUPS）を選択状態にするため。
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentTab = TopLevelDestination.entries.firstOrNull { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.route } == true
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(currentTab?.title ?: "") }
            )
        },
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { tab ->
                    val selected = tab == currentTab
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                // タブ切替の標準パターン:
                                // - スタートタブまで戻してバックスタックの肥大化を防ぐ
                                // - 各タブの状態（スクロール位置等）は保存・復元する
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        RcGearNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}
