package io.github.taskengineer.rcgear.feature.calc.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt

/**
 * ラベル + 現在値（等幅） + スライダーの入力行（PLAN Step 8）。
 *
 * Int 値のスライダーとして動く。step 指定時はその刻みにスナップする。
 *
 * @param label     項目名（例: "ピニオン"）
 * @param value     現在値
 * @param onValueChange 値変更コールバック（ドラッグ中も発火）
 * @param onValueChangeFinished 操作確定コールバック（DataStore 保存用）
 * @param valueRange 最小〜最大
 * @param step      値の刻み。1 なら整数刻み、100 なら KV 用
 * @param unit      値の後ろに付ける単位表示（例: "T", "mm"）。不要なら空文字
 */
@Composable
fun GearSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: IntRange,
    modifier: Modifier = Modifier,
    step: Int = 1,
    unit: String = ""
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$value$unit",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { raw ->
                // step 刻みにスナップしてから Int 化。
                // 同値への変更は上流の StateFlow が弾くので二重再計算にはならない
                val snapped = (raw / step).roundToInt() * step
                onValueChange(snapped.coerceIn(valueRange.first, valueRange.last))
            },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            // steps は「中間ストップの個数」= 区間数 - 1
            steps = ((valueRange.last - valueRange.first) / step - 1).coerceAtLeast(0)
        )
    }
}
