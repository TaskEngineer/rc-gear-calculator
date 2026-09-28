package io.github.taskengineer.rcgear.feature.sheet

import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.ui.UiText
import io.github.taskengineer.rcgear.core.ui.formatDecimals
import io.github.taskengineer.rcgear.domain.schema.FieldDef
import io.github.taskengineer.rcgear.domain.schema.NumberFieldDef
import io.github.taskengineer.rcgear.domain.validation.FieldViolation

/**
 * 検証結果（`:core:domain` の [FieldViolation]）を文言に変換する（G-4）。
 *
 * `FieldValidator` は「何が違反か」だけを返し、文言を持たない
 * （純 Kotlin モジュールは `R.string` を参照できない）。その変換をここに集約する。
 * **ViewModel はこの [UiText] を UiState に載せるだけ**で、文字列化は Composable が行う。
 *
 * 範囲の上下限はレジストリが持つ `Double` なので、項目の小数桁に合わせて整形する
 * （整数項目に「14.0〜40.0」と出ると、小数が入れられるように見える）。
 */
fun FieldViolation.toUiText(field: FieldDef): UiText = when (this) {
    is FieldViolation.OutOfRange -> {
        val decimals = (field as? NumberFieldDef)?.decimals ?: 0
        UiText.Res(
            R.string.sheet_edit_error_range,
            listOf(min.formatDecimals(decimals), max.formatDecimals(decimals))
        )
    }

    is FieldViolation.TooLong -> UiText.Res(R.string.sheet_edit_error_too_long, listOf(maxLength))

    // 数値欄に数値以外が入った場合が実質これ。選択肢・真偽は UI が型を保証する
    is FieldViolation.TypeMismatch -> UiText.Res(R.string.sheet_edit_error_number)

    // 選択肢のキーが改名された古いデータを編集したとき
    is FieldViolation.UnknownChoice -> UiText.Res(R.string.sheet_edit_error_choice)
}
