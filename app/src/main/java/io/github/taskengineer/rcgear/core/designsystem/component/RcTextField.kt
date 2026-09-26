package io.github.taskengineer.rcgear.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview

/**
 * 1 行テキスト入力（U-1）。
 *
 * エラーと補助文の出し方を 1 箇所に決めるためのラッパ。
 * `supportingText` は **エラーがあればエラー、無ければヒント**という優先順で出す。
 * この規則を各画面に書き写すと、画面ごとに「エラーが出ない」「ヒントが消える」が起きる。
 *
 * @param error エラー文。null ならエラーなし
 * @param hint  エラーが無いときに出す補助文
 */
@Composable
fun RcTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    hint: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        isError = error != null,
        keyboardOptions = keyboardOptions,
        supportingText = (error ?: hint)?.let { text -> { Text(text) } },
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * 数値入力（U-1）。[RcTextField] にキーボード種別と単位表示を足しただけのもの。
 *
 * 値を `String` で持つのは意図的。`Int` にすると「入力途中の空文字」「先頭のマイナス」を
 * 表現できず、1 文字消しただけで 0 に戻る。変換とバリデーションは ViewModel の仕事。
 *
 * @param decimal true なら小数キーボード
 * @param unit    ラベルに付ける単位（例: "mm"）。null なら付けない
 */
@Composable
fun RcNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    hint: String? = null,
    decimal: Boolean = false,
    unit: String? = null
) {
    RcTextField(
        label = if (unit != null) "$label [$unit]" else label,
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        error = error,
        hint = hint,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number
        )
    )
}

@Preview(name = "RcTextField / RcNumberField", showBackground = true)
@Composable
private fun RcTextFieldPreview() {
    ComponentPreview {
        RcTextField(label = "備考", value = "リヤ ワンウェイ", onValueChange = {})
        RcNumberField(
            label = "タイヤ径",
            value = "63",
            onValueChange = {},
            unit = "mm",
            hint = "40〜120"
        )
        RcNumberField(
            label = "内部減速比",
            value = "2,6",
            onValueChange = {},
            decimal = true,
            error = "数値を入力してください"
        )
    }
}
