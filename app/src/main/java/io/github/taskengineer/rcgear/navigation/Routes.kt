package io.github.taskengineer.rcgear.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * ナビゲーションのルート文字列定義。
 *
 * トップレベル4画面 + 将来の派生画面（詳細・編集）をここに集約する。
 * 文字列そのものは NavHost と TopLevelDestination だけが参照する。
 */
object Routes {
    const val CALC = "calc"
    const val SETUPS = "setups"
    const val DB = "db"
    const val CONFIG = "config"

    // ----- 派生画面（Step 9 / 10 で使用予定） -----
    // 引数付きルートは "setups/{setupId}" のような形で定義する
    const val SETUP_DETAIL = "setups/{setupId}"
    const val CHASSIS_EDIT = "db/{chassisId}"
}

/**
 * ボトムナビゲーションのトップレベル4タブ（PLAN 5.1）。
 *
 * @property route ナビゲーションルート
 * @property label ボトムナビ用ラベル。英大文字 + 等幅フォントで表示する
 * @property title TopAppBar 用の画面タイトル（日本語）
 * @property selectedIcon 選択中に表示する Filled アイコン
 * @property unselectedIcon 非選択時に表示する Outlined アイコン
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    CALC(
        route = Routes.CALC,
        label = "CALC",
        title = "計算",
        selectedIcon = Icons.Filled.Speed,
        unselectedIcon = Icons.Outlined.Speed
    ),
    SETUPS(
        route = Routes.SETUPS,
        label = "SETUPS",
        title = "保存一覧",
        selectedIcon = Icons.Filled.Bookmarks,
        unselectedIcon = Icons.Outlined.Bookmarks
    ),
    DB(
        route = Routes.DB,
        label = "DB",
        title = "シャーシDB",
        selectedIcon = Icons.Filled.Storage,
        unselectedIcon = Icons.Outlined.Storage
    ),
    CONFIG(
        route = Routes.CONFIG,
        label = "CONFIG",
        title = "設定",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
}
