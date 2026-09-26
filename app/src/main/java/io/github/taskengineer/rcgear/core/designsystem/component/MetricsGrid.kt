package io.github.taskengineer.rcgear.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.taskengineer.rcgear.core.designsystem.theme.RcGearTheme

/**
 * 数値 1 つを見せるセル（U-1）。`feature/calc/component/MetricsGrid` からの移設。
 *
 * @param value 表示用に整形済みの文字列。null は「まだ計算できていない」
 */
@Composable
fun MetricCell(
    label: String,
    value: String?,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value ?: PLACEHOLDER,
                style = RcGearTheme.extendedTypography.hudMetric.copy(fontSize = 20.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * [MetricCell] を 2 列で並べる（U-1）。
 *
 * 引数を `GearCalculationResult` から [Metric] のリストに変えたのが移設時の変更点。
 * 以前は整形とラベルを内部に抱えていたため、ギア比以外の用途に再利用できなかった。
 * **何を出すか（ラベル・整形・単位）は呼び出し側**、**どう並べるかはここ**、と分けている。
 */
@Composable
fun MetricsGrid(
    metrics: List<Metric>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        metrics.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { metric ->
                    MetricCell(
                        label = metric.label,
                        value = metric.displayValue,
                        modifier = Modifier.weight(1f)
                    )
                }
                // 奇数個のとき最後の行の空きを埋めて幅を揃える
                if (row.size == 1) {
                    Column(modifier = Modifier.weight(1f)) {}
                }
            }
        }
    }
}

/**
 * グリッドに出す 1 項目。
 *
 * @property label 項目名
 * @property value 整形済みの数値。null は未計算
 * @property unit  単位。null なら付けない
 */
data class Metric(
    val label: String,
    val value: String?,
    val unit: String? = null
) {
    /** 値と単位をつないだ表示文字列。未計算なら null */
    val displayValue: String?
        get() = value?.let { if (unit != null) "$it $unit" else it }
}

/** 未計算のセルに出す文字列 */
private const val PLACEHOLDER = "--"

@Preview(name = "MetricsGrid", showBackground = true)
@Composable
private fun MetricsGridPreview() {
    ComponentPreview {
        MetricsGrid(
            metrics = listOf(
                Metric("1次減速比", "2.60"),
                Metric("最終減速比 FDR", "6.24"),
                Metric("電圧", "7.4", "V"),
                Metric("モーターRPM", "48100"),
                Metric("ホイールRPM", "7708")
            )
        )
        MetricsGrid(
            metrics = listOf(
                Metric("1次減速比", null),
                Metric("最終減速比 FDR", null)
            )
        )
    }
}
