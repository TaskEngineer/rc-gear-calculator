package io.github.taskengineer.rcgear.feature.sheet

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.ui.formatDecimals
import io.github.taskengineer.rcgear.domain.model.SetupValue
import io.github.taskengineer.rcgear.domain.schema.ChoiceFieldDef
import io.github.taskengineer.rcgear.domain.schema.FieldDef
import io.github.taskengineer.rcgear.domain.schema.NumberFieldDef

/**
 * セッティングシートの値を表示用の文字列にする（G-3）。
 *
 * 「どんな値か」（型・桁数・単位・選択肢）は全てレジストリ（`:core:domain`）が持ち、
 * 「どう見せるか」（桁の書式・単位記号・空欄の記号）はここに 1 箇所だけ置く。
 * 閲覧（G-3）・編集（G-4）・差分（G-6）がこの関数を共有するので、
 * 同じ値が画面ごとに違う書式で出ることが無い。
 *
 * @param field 項目の定義。**未知キー（レジストリに無い）なら null** で、
 *   その場合は型に応じた素の表記にする（将来の版のデータを読んだとき）
 * @param value 入っている値。`null` は空欄
 */
@Composable
fun setupValueText(field: FieldDef?, value: SetupValue?): String {
    if (value == null) return stringResource(R.string.sheet_value_empty)
    val number = numberText(field, value)
    if (number != null) return number
    return when (value) {
        is SetupValue.BoolV -> stringResource(
            if (value.value) R.string.sheet_value_on else R.string.sheet_value_off
        )

        is SetupValue.ChoiceV -> choiceText(field, value)
        is SetupValue.TextV -> value.value
        // 数値は numberText で処理済み。ここに来るのは型が定義と食い違う場合だけ
        else -> value.toString()
    }
}

/** 数値なら「値 + 単位」、そうでなければ null */
@Composable
private fun numberText(field: FieldDef?, value: SetupValue): String? {
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
        stringResource(R.string.sheet_value_with_unit, text, stringResource(unitRes))
    }
}

/**
 * 選択肢の表示名。文言が無い（レジストリに無いキー・改名された選択肢）ときは
 * **保存されているキーをそのまま出す**。空欄にすると値が消えたように見えるため。
 */
@Composable
private fun choiceText(field: FieldDef?, value: SetupValue.ChoiceV): String {
    val labelRes = (field as? ChoiceFieldDef)?.labelResOf(value.key) ?: SetupFieldLabels.NO_LABEL
    return if (labelRes == SetupFieldLabels.NO_LABEL) value.key else stringResource(labelRes)
}
