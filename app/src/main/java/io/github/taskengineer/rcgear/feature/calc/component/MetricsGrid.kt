package io.github.taskengineer.rcgear.feature.calc.component

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.taskengineer.rcgear.core.designsystem.theme.RcGearTheme
import io.github.taskengineer.rcgear.domain.model.GearCalculationResult
import java.util.Locale
import kotlin.math.roundToLong

/**
 * 派生メトリックの一覧表示（PLAN Step 8）。
 * 1次減速比 / FDR / 電圧 / モーターRPM / ホイールRPM を 2列グリッドで並べる。
 *
 * 表示桁は PLAN 9.1 に従う:
 * - 減速比: 小数2桁 / ホイールRPM・モーターRPM: 整数 / 電圧: 小数1桁
 */
@Composable
fun MetricsGrid(
    result: GearCalculationResult?,
    modifier: Modifier = Modifier
) {
    // ラベルと表示値のペアに整形してから並べる
    val metrics = listOf(
        "1次減速比" to result?.primaryRatio?.format2(),
        "最終減速比 FDR" to result?.finalDriveRatio?.format2(),
        "電圧" to result?.voltage?.let { String.format(Locale.US, "%.1f V", it) },
        "モーターRPM" to result?.motorRpm?.formatRpm(),
        "ホイールRPM" to result?.wheelRpm?.formatRpm()
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        metrics.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value) ->
                    MetricCell(
                        label = label,
                        value = value ?: "--",
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

@Composable
private fun MetricCell(
    label: String,
    value: String,
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
                text = value,
                style = RcGearTheme.extendedTypography.hudMetric.copy(fontSize = 20.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun Double.format2(): String = String.format(Locale.US, "%.2f", this)

private fun Double.formatRpm(): String =
    String.format(Locale.US, "%,d", this.roundToLong())
