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
import io.github.taskengineer.rcgear.core.designsystem.theme.RcGearTheme
import java.util.Locale

/**
 * メインHUD: 理論最高速度の大型表示（PLAN Step 8）。
 *
 * @param topSpeedKmh 最高速 [km/h]。シャーシ未選択時は null
 * @param topSpeedMph 最高速 [mph]
 * @param showMph     mph を併記するか（ユーザー設定）
 */
@Composable
fun SpeedHud(
    topSpeedKmh: Double?,
    topSpeedMph: Double?,
    showMph: Boolean,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "理論最高速度",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    // 小数1桁（PLAN 9.1）。ロケール依存の小数点記号を避けるため Locale.US 固定
                    text = topSpeedKmh?.let { String.format(Locale.US, "%.1f", it) } ?: "--.-",
                    style = RcGearTheme.extendedTypography.hudMega,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "km/h",
                    style = RcGearTheme.extendedTypography.hudUnit,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 14.dp)
                )
            }
            if (showMph) {
                Text(
                    text = topSpeedMph?.let { String.format(Locale.US, "%.1f mph", it) } ?: "--.- mph",
                    style = RcGearTheme.extendedTypography.hudUnit,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
