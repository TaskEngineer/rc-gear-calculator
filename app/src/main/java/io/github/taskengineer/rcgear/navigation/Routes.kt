package io.github.taskengineer.rcgear.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.DirectionsCar
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
 * @property sheetId 流し込む（値を読み込む）シートの id。null = 素のスクラッチパッド。
 *   ルート引数はバックスタックに載るのでプロセス death を生き延びる（U-3 / G-5）。
 *   M-3 で一旦外した `setupId` の後継で、受け皿が保存セッティングからシートに変わった。
 */
@Serializable
data class Calc(val sheetId: String? = null)

/** GARAGE 画面（車一覧）。SETUPS の後継（G-1） */
@Serializable
data object Garage

/**
 * 車の新規作成画面。
 *
 * 編集（[CarEdit]）と同じ画面・同じ ViewModel を「引数なし」で開く。
 * `CarEdit(carId: String? = null)` の 1 ルートにまとめる案は採らなかった:
 * 型安全ルートの nullable 引数は文字列 "null" の扱いに実装依存の癖があり、
 * 「新規かどうか」を引数の有無で表すほうが受け取り側（[carEditRouteOrNull]）も素直になる。
 */
@Serializable
data object CarCreate

/** 車の編集画面 */
@Serializable
data class CarEdit(val carId: String)

/** 車詳細（その車のセッティングシート一覧）画面 */
@Serializable
data class CarDetail(val carId: String)

/** セッティングシートの閲覧画面 */
@Serializable
data class SheetDetail(val sheetId: String)

/**
 * セッティングシートの値の編集画面。
 *
 * **セクション 1 つが 1 画面**（G-4）。全項目を 1 画面に並べると、
 * 項目が 60 を超えたときに目的の欄まで延々スクロールすることになる。
 *
 * @property sectionKey `TouringSetupSchema.sections` のキー。
 *   レジストリに無いキーが来た場合は画面を開かずに戻る
 */
@Serializable
data class SheetEdit(val sheetId: String, val sectionKey: String)

/**
 * シートのヘッダ（名前・走行条件・ベースライン）の編集画面。
 *
 * 値（bag）の編集（[SheetEdit]）と分けているのは、ヘッダが
 * 「その設定を行った条件」でありセクションではないため（`SetupSheet` の KDoc）。
 */
@Serializable
data class SheetHeaderEdit(val sheetId: String)

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

/** CALC 画面のルート引数を復元する。引数なしで開かれた場合は [Calc.sheetId] が null */
fun SavedStateHandle.calcRoute(): Calc = Calc(sheetId = get<String>("sheetId"))

/** シャーシ編集画面のルート引数を復元する */
fun SavedStateHandle.chassisEditRoute(): ChassisEdit =
    ChassisEdit(chassisId = checkNotNull(get<String>("chassisId")))

/**
 * 車編集画面のルート引数を復元する。
 * [CarCreate]（新規作成）で開いた場合は引数が無いので `null` を返す。
 */
fun SavedStateHandle.carEditRouteOrNull(): CarEdit? =
    get<String>("carId")?.let { CarEdit(carId = it) }

/** 車詳細画面のルート引数を復元する */
fun SavedStateHandle.carDetailRoute(): CarDetail =
    CarDetail(carId = checkNotNull(get<String>("carId")))

/** シート閲覧画面のルート引数を復元する */
fun SavedStateHandle.sheetDetailRoute(): SheetDetail =
    SheetDetail(sheetId = checkNotNull(get<String>("sheetId")))

/** シートのヘッダ編集画面のルート引数を復元する */
fun SavedStateHandle.sheetHeaderEditRoute(): SheetHeaderEdit =
    SheetHeaderEdit(sheetId = checkNotNull(get<String>("sheetId")))

/** シート編集画面のルート引数を復元する */
fun SavedStateHandle.sheetEditRoute(): SheetEdit =
    SheetEdit(
        sheetId = checkNotNull(get<String>("sheetId")),
        sectionKey = checkNotNull(get<String>("sectionKey"))
    )

/**
 * ボトムナビゲーションのトップレベルタブ（PLAN 5.1）。
 *
 * G-1 で GARAGE（車一覧）が先頭に入り、[GARAGE] [CALC] [DB] [CONFIG] の 4 つになった。
 * CALC をシートのセクションに格下げしないのは、「車の文脈なしで 10 秒で終わる問い」に
 * 答える画面であり、一番速い操作を一番遅くしたくないため（ROADMAP Phase 3）。
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
    GARAGE(
        route = Garage,
        label = "GARAGE",
        titleRes = R.string.tab_title_garage,
        selectedIcon = Icons.Filled.DirectionsCar,
        unselectedIcon = Icons.Outlined.DirectionsCar,
        childRoutes = listOf(
            CarCreate::class,
            CarEdit::class,
            CarDetail::class,
            SheetDetail::class,
            SheetEdit::class,
            SheetHeaderEdit::class
        )
    ),
    CALC(
        route = Calc(),
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
