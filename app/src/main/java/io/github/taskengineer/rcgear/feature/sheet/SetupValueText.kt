package io.github.taskengineer.rcgear.feature.sheet

import androidx.compose.runtime.Composable
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.ui.Strings
import io.github.taskengineer.rcgear.core.ui.formatDecimals
import io.github.taskengineer.rcgear.core.ui.rememberStrings
import io.github.taskengineer.rcgear.domain.model.SetupValue
import io.github.taskengineer.rcgear.domain.schema.ChoiceFieldDef
import io.github.taskengineer.rcgear.domain.schema.FieldDef
import io.github.taskengineer.rcgear.domain.schema.NumberFieldDef

/**
 * セッティングシートの値を表示用の文字列にする（G-3）。
 *
 * 「どんな値か」（型・桁数・単位・選択肢）は全てレジストリ（`:core:domain`）が持ち、
 * 「どう見せるか」（桁の書式・単位記号・空欄の記号）はここに 1 箇所だけ置く。
 * 閲覧（G-3）・編集（G-4）・差分（G-6）・テキスト共有（F-4）がこの関数を共有するので、
 * 同じ値が経路ごとに違う書式で出ることが無い。
 *
 * **実体は [Strings] を受ける版で、Composable 版はその薄い包み。** テキスト共有（F-4）は
 * コンポジションの外で文字列を組み立てるため、`stringResource` に依存できない。
 * [Strings] を挟んだことで、この関数は JVM 単体テストからも呼べる。
 *
 * @param field 項目の定義。**未知キー（レジストリに無い）なら null** で、
 *   その場合は型に応じた素の表記にする（将来の版のデータを読んだとき）
 * @param value 入っている値。`null` は空欄
 */
fun setupValueString(field: FieldDef?, value: SetupValue?, strings: Strings): String {
    if (value == null) return strings.get(R.string.sheet_value_empty)
    numberString(field, value, strings)?.let { return it }
    return when (value) {
        is SetupValue.BoolV -> strings.get(
            if (value.value) R.string.sheet_value_on else R.string.sheet_value_off
        )

        is SetupValue.ChoiceV -> choiceString(field, value, strings)
        is SetupValue.TextV -> value.value
        // 数値は numberString で処理済み。ここに来るのは型が定義と食い違う場合だけ
        else -> value.toString()
    }
}

/** Composable 版。表示にはこちらを使う */
@Composable
fun setupValueText(field: FieldDef?, value: SetupValue?): String =
    setupValueString(field, value, rememberStrings())

/**
 * 編集画面・差分行・テキスト共有で使う項目ラベル（G-4）。
 *
 * グリッドのセクション（F / C / R）では行ラベルを前後で共有している
 * （列見出しが別に出るため）。列見出しの無い縦並びで使うときは
 * 「キャンバー（フロント）」のように列名を添えないと、どちらの値か分からない。
 */
fun fieldEditorLabelString(field: FieldDef, strings: Strings): String {
    val label = strings.get(field.labelRes)
    val columnKey = field.key.substringBefore('.', missingDelimiterValue = "")
    if (columnKey.isEmpty()) return label
    val columnRes = SetupFieldLabels.columnLabelRes(columnKey)
    if (columnRes == SetupFieldLabels.NO_LABEL) return label
    return strings.get(R.string.sheet_field_with_column, label, strings.get(columnRes))
}

/** Composable 版 */
@Composable
fun fieldEditorLabel(field: FieldDef): String =
    fieldEditorLabelString(field, rememberStrings())

/** 数値なら「値 + 単位」、そうでなければ null */
private fun numberString(field: FieldDef?, value: SetupValue, strings: Strings): String? {
    val raw = when (value) {
        is SetupValue.IntV -> value.value.toDouble()
        is SetupValue.DecimalV -> value.value
        else -> return null
    }
    val decimals = (field as? NumberFieldDef)?.decimals
        // 未知キーの小数は桁数が分からない。1 桁に丸めず素直に出す
        ?: if (value is SetupValue.IntV) 0 else 2
    val text = raw.formatDecimals(decimals)
    val unitRes = field?.unitLabelRes ?: SetupFieldLabels.NO_LABEL
    return if (unitRes == SetupFieldLabels.NO_LABEL) {
        text
    } else {
        strings.get(R.string.sheet_value_with_unit, text, strings.get(unitRes))
    }
}

/**
 * 選択肢の表示名。文言が無い（レジストリに無いキー・改名された選択肢）ときは
 * **保存されているキーをそのまま出す**。空欄にすると値が消えたように見えるため。
 */
private fun choiceString(field: FieldDef?, value: SetupValue.ChoiceV, strings: Strings): String {
    val labelRes = (field as? ChoiceFieldDef)?.labelResOf(value.key) ?: SetupFieldLabels.NO_LABEL
    return if (labelRes == SetupFieldLabels.NO_LABEL) value.key else strings.get(labelRes)
}
