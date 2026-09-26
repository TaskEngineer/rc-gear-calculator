package io.github.taskengineer.rcgear.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * ViewModel から画面へ渡す「まだ文字列になっていない文言」（S-11 / REF-4）。
 *
 * 以前は ViewModel が日本語を直接組み立てて UiState に載せていた（DEBT-4）。それだと:
 *  - 英語化（F-7）の際に ViewModel まで書き換えることになる
 *  - テストが「「Rd1」を保存しました」という文字列に依存し、文言を変えると落ちる
 *  - ViewModel が `Context` を持たないと `getString` できないのに、持たせたくない
 *
 * そこで **ViewModel はリソース ID と引数だけを持ち**、文字列化は Composable で行う。
 * テストは `UiText.Res(R.string.calc_saved, listOf("Rd1"))` という構造を突き合わせられる。
 */
sealed interface UiText {

    /**
     * リソース ID + 書式引数。
     *
     * @param args `%1$s` 等に埋める値。[UiText] を入れると再帰的に解決される
     *   （「取り込み完了 … （同名スキップ 1件）」のような入れ子のため）
     */
    data class Res(
        @StringRes val id: Int,
        val args: List<Any> = emptyList()
    ) : UiText

    /** 既に文字列になっているもの（例外メッセージなど、翻訳できない値） */
    data class Raw(val value: String) : UiText

    /** 複数を区切り文字でつなぐ。区切り文字もリソースから引く */
    data class Joined(
        val parts: List<UiText>,
        @StringRes val separatorId: Int
    ) : UiText

    /** 何も表示しない（空文字）。書式引数の「無い場合」に使う */
    data object Empty : UiText
}

/**
 * [UiText] を表示用の文字列にする。Composable の中だけで呼べる。
 *
 * ラムダの中で `stringResource` を呼べるのは `map` が inline だからで、
 * `joinToString`（非 inline）の transform では呼べない。先に `map` で解決する。
 */
@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Raw -> value
    UiText.Empty -> ""
    // joinToString の transform は inline ではないので、先に map で解決しておく
    is UiText.Joined -> parts.map { it.asString() }.joinToString(stringResource(separatorId))
    is UiText.Res -> if (args.isEmpty()) {
        stringResource(id)
    } else {
        stringResource(id, *args.map { arg -> if (arg is UiText) arg.asString() else arg }.toTypedArray())
    }
}
