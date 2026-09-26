package io.github.taskengineer.rcgear.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
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
 * CALC 画面（スクラッチパッド計算機）。
 *
 * M-3 で「保存セッティングの流し込み」（引数 `setupId`）を一旦外した。
 * 受け皿だった SETUPS がシートに置き換わるため、Phase 3 の G-5 で
 * `Calc(sheetId: String?)` として入れ直す。その時にルート引数方式（U-3）も戻る。
 */
@Serializable
data object Calc

@Serializable
data object Db

@Serializable
data object Config

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

/** シャーシ編集画面のルート引数を復元する */
fun SavedStateHandle.chassisEditRoute(): ChassisEdit =
    ChassisEdit(chassisId = checkNotNull(get<String>("chassisId")))

/**
 * ボトムナビゲーションのトップレベルタブ（PLAN 5.1）。
 *
 * M-3 で SETUPS を外したので現在は CALC / DB / CONFIG の 3 つ。
 * Phase 3 の G-1 で GARAGE（車一覧）が先頭に入る。
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
        route = Calc,
        label = "CALC",
        titleRes = R.string.tab_title_calc,
        selectedIcon = Icons.Filled.Speed,
        unselectedIcon = Icons.Outlined.Speed
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
