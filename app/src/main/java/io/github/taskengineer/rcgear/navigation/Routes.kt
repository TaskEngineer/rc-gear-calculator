package io.github.taskengineer.rcgear.navigation

import androidx.annotation.StringRes
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
import androidx.lifecycle.SavedStateHandle
import io.github.taskengineer.rcgear.R
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

// ============================================================
// ナビゲーションのルート定義（S-12 / REF-5）
//
// Navigation Compose 2.8 の型安全ルート（@Serializable なクラス）で表す。
// 引数の名前と型はクラス定義そのものなので、"setups/$id" のような文字列組み立てと
// navArgument の二重定義が要らない。受け取り側はこのファイル末尾のルート復元関数を使う。
//
// ViewModel からこのファイルを参照する（feature → navigation）が、
// ルートは「画面の入力パラメータの宣言」なので依存として妥当と判断した。
// ============================================================

/**
 * CALC 画面。
 *
 * @property setupId 流し込む保存セッティングの ID。null = 素のスクラッチパッド（U-3）。
 *   ルート引数はバックスタックに載るのでプロセス death を生き延びる。
 */
@Serializable
data class Calc(val setupId: Long? = null)

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

// ============================================================
// SavedStateHandle からのルート復元
//
// 本来は androidx.navigation の SavedStateHandle.toRoute<T>() を使うところだが、
// あれは内部で android.os.Bundle を組み立てるため、Robolectric 無しの JVM 単体テストでは
// "Method putString in android.os.BaseBundle not mocked" で落ちる。
// ViewModel のテストを Robolectric 抜きで書き続けたいので、引数の読み出しだけ自前でやる
// （SavedStateHandle には NavType が put した生の値がそのまま入っている）。
//
// キー名はルートクラスのプロパティ名と一致していなければならない。
// その危ない対応関係をルート定義と同じファイルに閉じ込めるのが、この関数群の役目。
// ============================================================

/** CALC 画面のルート引数を復元する。引数なしで開かれた場合は [Calc.setupId] が null */
fun SavedStateHandle.calcRoute(): Calc = Calc(setupId = get<Long>("setupId"))

/** セッティング詳細画面のルート引数を復元する */
fun SavedStateHandle.setupDetailRoute(): SetupDetail =
    SetupDetail(setupId = checkNotNull(get<Long>("setupId")))

/** シャーシ編集画面のルート引数を復元する */
fun SavedStateHandle.chassisEditRoute(): ChassisEdit =
    ChassisEdit(chassisId = checkNotNull(get<String>("chassisId")))

/**
 * ボトムナビゲーションのトップレベル4タブ（PLAN 5.1）。
 *
 * @property route        タブのルート（引数なしの既定インスタンス）
 * @property label        ボトムナビ用ラベル。英大文字 + 等幅フォントで表示する。
 *   翻訳対象外の固有表記なのでリソースに置かない
 * @property titleRes     TopAppBar 用の画面タイトル（S-11 で @StringRes 化）
 * @property childRoutes  このタブに属する派生画面。親タブを選択状態にするために使う
 * @property selectedIcon 選択中に表示する Filled アイコン
 * @property unselectedIcon 非選択時に表示する Outlined アイコン
 */
enum class TopLevelDestination(
    val route: Any,
    val label: String,
    @StringRes val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val childRoutes: List<KClass<*>> = emptyList()
) {
    CALC(
        route = Calc(),
        label = "CALC",
        titleRes = R.string.tab_title_calc,
        selectedIcon = Icons.Filled.Speed,
        unselectedIcon = Icons.Outlined.Speed
    ),
    SETUPS(
        route = Setups,
        label = "SETUPS",
        titleRes = R.string.tab_title_setups,
        selectedIcon = Icons.Filled.Bookmarks,
        unselectedIcon = Icons.Outlined.Bookmarks,
        childRoutes = listOf(SetupDetail::class)
    ),
    DB(
        route = Db,
        label = "DB",
        titleRes = R.string.tab_title_db,
        selectedIcon = Icons.Filled.Storage,
        unselectedIcon = Icons.Outlined.Storage,
        childRoutes = listOf(ChassisEdit::class)
    ),
    CONFIG(
        route = Config,
        label = "CONFIG",
        titleRes = R.string.tab_title_config,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    );

    /** タブ自身のルートクラス（`hasRoute` 判定用） */
    val routeClass: KClass<*> get() = route::class
}
