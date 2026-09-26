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
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/**
 * ナビゲーションのルート定義（S-12 / REF-5）。
 *
 * Navigation Compose 2.8 の型安全ルート（`@Serializable` なクラス）で表す。
 * 引数の名前と型はクラス定義そのものなので、`"setups/$id"` のような文字列組み立てと
 * `navArgument` の二重定義が要らない。受け取り側は `SavedStateHandle.toRoute<T>()`。
 *
 * ViewModel からこのファイルを参照する（`feature` → `navigation`）が、
 * ルートは「画面の入力パラメータの宣言」なので依存として妥当と判断した。
 */
@Serializable
data object Calc

@Serializable
data object Setups

@Serializable
data object Db

@Serializable
data object Config

/** セッティング詳細画面 */
@Serializable
data class SetupDetail(val setupId: Long)

/** シャーシ編集画面 */
@Serializable
data class ChassisEdit(val chassisId: String)

/**
 * ボトムナビゲーションのトップレベル4タブ（PLAN 5.1）。
 *
 * @property route        タブのルート（引数なしの既定インスタンス）
 * @property label        ボトムナビ用ラベル。英大文字 + 等幅フォントで表示する
 * @property title        TopAppBar 用の画面タイトル（日本語）
 * @property childRoutes  このタブに属する派生画面。親タブを選択状態にするために使う
 * @property selectedIcon 選択中に表示する Filled アイコン
 * @property unselectedIcon 非選択時に表示する Outlined アイコン
 */
enum class TopLevelDestination(
    val route: Any,
    val label: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val childRoutes: List<KClass<*>> = emptyList()
) {
    CALC(
        route = Calc,
        label = "CALC",
        title = "計算",
        selectedIcon = Icons.Filled.Speed,
        unselectedIcon = Icons.Outlined.Speed
    ),
    SETUPS(
        route = Setups,
        label = "SETUPS",
        title = "保存一覧",
        selectedIcon = Icons.Filled.Bookmarks,
        unselectedIcon = Icons.Outlined.Bookmarks,
        childRoutes = listOf(SetupDetail::class)
    ),
    DB(
        route = Db,
        label = "DB",
        title = "シャーシDB",
        selectedIcon = Icons.Filled.Storage,
        unselectedIcon = Icons.Outlined.Storage,
        childRoutes = listOf(ChassisEdit::class)
    ),
    CONFIG(
        route = Config,
        label = "CONFIG",
        title = "設定",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    );

    /** タブ自身のルートクラス（`hasRoute` 判定用） */
    val routeClass: KClass<*> get() = route::class
}
