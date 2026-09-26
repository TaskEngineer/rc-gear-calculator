package io.github.taskengineer.rcgear.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.designsystem.theme.RcGearTheme
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 「− 値 ＋」のステッパー入力（U-1、新規）。
 *
 * セッティングシートの値の多くは **刻みが決まっていて可動域が狭い**。
 * キャンバー -3.0°〜0.0°（0.5 刻み）、車高 4.0〜7.0mm（0.5 刻み）のような値を
 * スライダーで合わせるのは指が太すぎるので、ステッパーにする。
 *
 * @param step      1 タップあたりの増減量
 * @param decimals  表示小数桁。0 なら整数として出す
 * @param unit      値の後ろに付ける単位（例: "°", "mm"）
 */
@Composable
fun RcStepper(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    valueRange: ClosedFloatingPointRange<Double>,
    modifier: Modifier = Modifier,
    step: Double = 0.5,
    decimals: Int = 1,
    unit: String = ""
) {
    // 浮動小数の加算誤差（0.1 を 3 回足すと 0.30000000000000004）を
    // 表示にも保存値にも持ち込まないよう、刻みの格子に丸める
    fun snap(raw: Double): Double {
        val snapped = (raw / step).roundToInt() * step
        return snapped.coerceIn(valueRange.start, valueRange.endInclusive)
    }

    LabeledRow(label = label, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = { onValueChange(snap(value - step)) },
                enabled = value - step >= valueRange.start - EPSILON
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = stringResource(R.string.action_stepper_decrement)
                )
            }
            Text(
                text = value.formatStep(decimals) + unit,
                style = RcGearTheme.extendedTypography.hudUnit.copy(
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize
                ),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(72.dp)
            )
            FilledTonalIconButton(
                onClick = { onValueChange(snap(value + step)) },
                enabled = value + step <= valueRange.endInclusive + EPSILON
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.action_stepper_increment)
                )
            }
        }
    }
}

/** Int 値用。歯数・枚数のような「刻み 1 の整数」に使う */
@Composable
fun RcStepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange,
    modifier: Modifier = Modifier,
    step: Int = 1,
    unit: String = ""
) {
    RcStepper(
        label = label,
        value = value.toDouble(),
        onValueChange = { onValueChange(it.roundToInt()) },
        valueRange = valueRange.first.toDouble()..valueRange.last.toDouble(),
        modifier = modifier,
        step = step.toDouble(),
        decimals = 0,
        unit = unit
    )
}

/** 端の比較を浮動小数の誤差で落とさないための許容差 */
private const val EPSILON = 1e-9

/**
 * 小数点は常に `.` にする（ロケールによって `,` になると等幅の桁が崩れる）。
 *
 * `core/ui/Format.kt` を使わないのは、designsystem をドメイン・表示整形層に
 * 依存させないため。桁数は呼び出し側（フィールド定義）が決める値なので、
 * 整形もここで完結させる。
 */
private fun Double.formatStep(decimals: Int): String =
    String.format(Locale.US, "%.${decimals.coerceAtLeast(0)}f", this)

@Preview(name = "RcStepper", showBackground = true)
@Composable
private fun RcStepperPreview() {
    ComponentPreview {
        RcStepper(
            label = "キャンバー（F）",
            value = -1.5,
            onValueChange = {},
            valueRange = -5.0..0.0,
            unit = "°"
        )
        RcStepper(
            label = "車高（F）",
            value = 5.0,
            onValueChange = {},
            valueRange = 4.0..8.0,
            unit = "mm"
        )
        RcStepper(
            label = "スプリング枚数",
            value = 2,
            onValueChange = {},
            valueRange = 0..4
        )
    }
}
