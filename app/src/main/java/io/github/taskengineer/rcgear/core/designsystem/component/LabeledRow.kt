package io.github.taskengineer.rcgear.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import io.github.taskengineer.rcgear.core.designsystem.theme.RcGearTheme

/**
 * 「ラベル ─ 値」の 1 行（U-1）。
 *
 * 値は等幅（hudUnit）で描く。桁が縦に揃うので、行が並んだときに数値が読める。
 * `SetupDetailScreen.DetailRow` からの昇格。
 *
 * @param valueColor 値の色。既定以外にしたいのは「標準値と違う」等を示すとき
 */
@Composable
fun LabeledRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    LabeledRow(label = label, modifier = modifier) {
        Text(
            text = value,
            style = RcGearTheme.extendedTypography.hudUnit.copy(
                fontSize = MaterialTheme.typography.bodyMedium.fontSize
            ),
            color = valueColor
        )
    }
}

/**
 * 右側に任意の Composable を置ける版。スイッチ・ステッパー等を載せるときに使う。
 */
@Composable
fun LabeledRow(
    label: String,
    modifier: Modifier = Modifier,
    value: @Composable () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        value()
    }
}

@Preview(name = "LabeledRow", showBackground = true)
@Composable
private fun LabeledRowPreview() {
    ComponentPreview {
        LabeledRow(label = "ピニオン", value = "28T")
        LabeledRow(label = "内部減速比", value = "2.60")
        LabeledRow(
            label = "内部減速比（上書き）",
            value = "2.70",
            valueColor = MaterialTheme.colorScheme.tertiary
        )
    }
}
