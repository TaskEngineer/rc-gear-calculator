package io.github.taskengineer.rcgear.feature.sheet.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.designsystem.component.ComponentPreview
import io.github.taskengineer.rcgear.core.designsystem.component.Option
import io.github.taskengineer.rcgear.core.designsystem.component.RcNumberField
import io.github.taskengineer.rcgear.core.designsystem.component.RcSelectField
import io.github.taskengineer.rcgear.core.designsystem.component.RcSlider
import io.github.taskengineer.rcgear.core.designsystem.component.RcStepper
import io.github.taskengineer.rcgear.core.designsystem.component.RcTextField
import io.github.taskengineer.rcgear.core.designsystem.component.SwitchRow
import io.github.taskengineer.rcgear.core.ui.formatDecimals
import io.github.taskengineer.rcgear.domain.model.SetupValue
import io.github.taskengineer.rcgear.domain.schema.BoolFieldDef
import io.github.taskengineer.rcgear.domain.schema.ChoiceFieldDef
import io.github.taskengineer.rcgear.domain.schema.FieldDef
import io.github.taskengineer.rcgear.domain.schema.FieldUi
import io.github.taskengineer.rcgear.domain.schema.NumberFieldDef
import io.github.taskengineer.rcgear.domain.schema.TextFieldDef
import io.github.taskengineer.rcgear.domain.schema.TouringSetupSchema
import io.github.taskengineer.rcgear.feature.sheet.SetupFieldLabels
import io.github.taskengineer.rcgear.feature.sheet.fieldEditorLabel
import io.github.taskengineer.rcgear.feature.sheet.labelResOf
import io.github.taskengineer.rcgear.feature.sheet.unitLabelRes
import kotlin.math.roundToInt

/**
 * 項目 1 つの入力欄を、定義（[FieldDef]）から選んで描く（G-4）。
 *
 * **ここが「項目を足しても Composable を書かない」ことの受け皿。**
 * 入力手段の選択は `FieldUi` の分岐だけで、項目そのものは一切知らない。
 * 新しい入力手段が必要になったときだけ `FieldUi` とこの `when` に 1 ケース増える
 * （項目の追加では増えない）。
 *
 * 値の持ち方について: 数値・テキストは **文字列の下書き**（[draft]）を
 * ViewModel が持つ。`Int` で持つと「入力途中の空文字」「マイナスだけ」を表現できず、
 * 1 文字消しただけで 0 に戻る（`RcNumberField` の KDoc と同じ理由）。
 * スライダー・ステッパー・選択肢・スイッチは範囲内の値しか作れないので下書きは要らない。
 *
 * @param draft   数値・テキスト欄の入力途中の文字列。null なら [value] から起こす
 * @param error   インラインに出す検証メッセージ。null なら問題なし
 * @param onNumberInput / onTextInput 文字列のまま ViewModel に渡す（検証は ViewModel 側）
 * @param onValueChange 型の付いた値をそのまま確定する経路
 */
@Composable
fun FieldEditor(
    field: FieldDef,
    value: SetupValue?,
    draft: String?,
    error: String?,
    onNumberInput: (String) -> Unit,
    onTextInput: (String) -> Unit,
    onValueChange: (SetupValue?) -> Unit,
    modifier: Modifier = Modifier
) {
    val label = fieldEditorLabel(field)
    val unit = field.unitLabelRes
        .takeIf { it != SetupFieldLabels.NO_LABEL }
        ?.let { stringResource(it) }

    when (field.ui) {
        FieldUi.SLIDER -> {
            val number = field as NumberFieldDef
            // レジストリのスライダー項目は全て整数（歯数・KV・タイヤ径）。
            // 小数の項目が来たらステッパーに落とす（RcSlider は Int 専用）
            if (number.isInteger) {
                Column(modifier = modifier.fillMaxWidth()) {
                    RcSlider(
                        label = label,
                        value = value.asInt() ?: number.min.roundToInt(),
                        onValueChange = { onValueChange(SetupValue.IntV(it)) },
                        onValueChangeFinished = { },
                        valueRange = number.min.roundToInt()..number.max.roundToInt(),
                        step = number.step.roundToInt().coerceAtLeast(1),
                        unit = unit.orEmpty()
                    )
                    FieldError(error)
                }
            } else {
                StepperEditor(number, value, error, onValueChange, unit, label, modifier)
            }
        }

        FieldUi.STEPPER -> StepperEditor(
            field as NumberFieldDef,
            value,
            error,
            onValueChange,
            unit,
            label,
            modifier
        )

        FieldUi.NUMBER_FIELD -> {
            val number = field as NumberFieldDef
            RcNumberField(
                label = label,
                value = draft ?: value.asText(number.decimals),
                onValueChange = onNumberInput,
                modifier = modifier,
                error = error,
                decimal = !number.isInteger,
                unit = unit
            )
        }

        FieldUi.SELECT -> {
            val choice = field as ChoiceFieldDef
            Column(modifier = modifier.fillMaxWidth()) {
                RcSelectField(
                    label = label,
                    selectedKey = (value as? SetupValue.ChoiceV)?.key,
                    options = choice.choiceSet.options.map { option ->
                        val labelRes = choice.labelResOf(option.key)
                        Option(
                            key = option.key,
                            // 文言が無ければキーをそのまま出す（空の選択肢を作らない）
                            label = if (labelRes == SetupFieldLabels.NO_LABEL) {
                                option.key
                            } else {
                                stringResource(labelRes)
                            }
                        )
                    },
                    onSelect = { onValueChange(SetupValue.ChoiceV(it)) }
                )
                FieldError(error)
            }
        }

        FieldUi.TEXT_FIELD, FieldUi.TEXT_AREA -> {
            val text = field as TextFieldDef
            RcTextField(
                label = label,
                value = draft ?: (value as? SetupValue.TextV)?.value.orEmpty(),
                onValueChange = onTextInput,
                modifier = modifier,
                error = error,
                singleLine = !text.multiline
            )
        }

        FieldUi.SWITCH -> {
            val bool = field as BoolFieldDef
            Column(modifier = modifier.fillMaxWidth()) {
                SwitchRow(
                    title = label,
                    checked = (value as? SetupValue.BoolV)?.value ?: bool.defaultValue ?: false,
                    onCheckedChange = { onValueChange(SetupValue.BoolV(it)) }
                )
                FieldError(error)
            }
        }
    }
}

@Composable
private fun StepperEditor(
    field: NumberFieldDef,
    value: SetupValue?,
    error: String?,
    onValueChange: (SetupValue?) -> Unit,
    unit: String?,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        RcStepper(
            label = label,
            // 空欄の項目は既定値（無ければ下限）から動かし始める
            value = value.asDouble() ?: field.defaultValue ?: field.min,
            onValueChange = { onValueChange(SetupValue.number(it, field.decimals)) },
            valueRange = field.min..field.max,
            step = field.step,
            decimals = field.decimals,
            unit = unit.orEmpty()
        )
        FieldError(error)
    }
}

/** 入力欄の下に出す検証メッセージ。`RcTextField` は自前で出すのでそれ以外で使う */
@Composable
private fun FieldError(error: String?) {
    if (error == null) return
    Text(
        text = error,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

private fun SetupValue?.asInt(): Int? = when (this) {
    is SetupValue.IntV -> value
    is SetupValue.DecimalV -> value.roundToInt()
    else -> null
}

private fun SetupValue?.asDouble(): Double? = when (this) {
    is SetupValue.IntV -> value.toDouble()
    is SetupValue.DecimalV -> value
    else -> null
}

/** 数値欄の初期文字列。空欄は空文字（0 で埋めない） */
private fun SetupValue?.asText(decimals: Int): String = when (this) {
    is SetupValue.IntV -> value.toString()
    is SetupValue.DecimalV -> value.formatDecimals(decimals)
    else -> ""
}

@Preview(name = "FieldEditor", showBackground = true)
@Composable
private fun FieldEditorPreview() {
    ComponentPreview {
        // スライダー（整数）・ステッパー（小数）・数値欄・選択肢を 1 つずつ
        FieldEditor(
            field = TouringSetupSchema.byKey.getValue("pinion"),
            value = SetupValue.IntV(29),
            draft = null,
            error = null,
            onNumberInput = {},
            onTextInput = {},
            onValueChange = {}
        )
        FieldEditor(
            field = TouringSetupSchema.byKey.getValue("front.camberDeg"),
            value = SetupValue.DecimalV(-2.0),
            draft = null,
            error = null,
            onNumberInput = {},
            onTextInput = {},
            onValueChange = {}
        )
        FieldEditor(
            field = TouringSetupSchema.byKey.getValue("internalRatio"),
            value = null,
            draft = "2.6x",
            error = stringResource(R.string.sheet_edit_error_number),
            onNumberInput = {},
            onTextInput = {},
            onValueChange = {}
        )
        FieldEditor(
            field = TouringSetupSchema.byKey.getValue("front.springRate"),
            value = SetupValue.ChoiceV("medium"),
            draft = null,
            error = null,
            onNumberInput = {},
            onTextInput = {},
            onValueChange = {}
        )
    }
}
