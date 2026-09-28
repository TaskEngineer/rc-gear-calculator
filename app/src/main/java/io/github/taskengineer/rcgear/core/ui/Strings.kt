package io.github.taskengineer.rcgear.core.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * 文言を引く口（F-4）。
 *
 * `stringResource` はコンポジションの中でしか呼べず、[Context] を直に配ると
 * **その関数が JVM 単体テストから呼べなくなる**（Robolectric を入れていない）。
 * 文字列を組み立てるロジック（テキスト版シートの書式など）はテストしたいので、
 * 解決の手段だけをこの 1 段で抽象化する。
 *
 * 本番は [asStrings] で [Context] を包み、テストは「id をそのまま返す」
 * Fake を渡す。`UiText`（ViewModel → 画面）とは役割が違う — あちらは
 * 「まだ文字列になっていない文言を運ぶ入れ物」で、こちらは「文字列にする関数」。
 */
fun interface Strings {
    fun get(@StringRes id: Int, vararg args: Any): String
}

/** 本番の実装。`Context.getString` をそのまま使う */
fun Context.asStrings(): Strings = Strings { id, args -> getString(id, *args) }

/** Composable から取る。`LocalContext` は設定変更で差し替わるので再コンポジションに追従する */
@Composable
fun rememberStrings(): Strings = LocalContext.current.asStrings()
