package io.github.taskengineer.rcgear.feature.calc.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.taskengineer.rcgear.core.designsystem.theme.RcGearTheme
import java.util.Locale

/**
 * メインHUD: 理論最高速度の大型表示（PLAN Step 8 / アニメーションは Step 12）。
 *
 * animationEnabled のとき、数値の変化をカウントアップ/ダウンで滑らかに見せる。
 * スライダー操作中も 60fps を維持できるよう、アニメーション対象は Float 1つに絞る。
 *
 * @param topSpeedKmh 最高速 [km/h]。シャーシ未選択時は null
 * @param topSpeedMph 最高速 [mph]
 * @param showMph     mph を併記するか（ユーザー設定）
 * @param animationEnabled 数値変化アニメーションの有効/無効（ユーザー設定）
 */
@Composable
fun SpeedHud(
    topSpeedKmh: Double?,
    topSpeedMph: Double?,
    showMph: Boolean,
    animationEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    // 数値変化のアニメーション。無効時は snap（即時反映）
    val animatedKmh by animateFloatAsState(
        targetValue = topSpeedKmh?.toFloat() ?: 0f,
        animationSpec = if (animationEnabled) tween(durationMillis = 250) else tween(0),
        label = "topSpeedKmh"
    )
    val animatedMph by animateFloatAsState(
        targetValue = topSpeedMph?.toFloat() ?: 0f,
        animationSpec = if (animationEnabled) tween(durationMillis = 250) else tween(0),
        label = "topSpeedMph"
    )

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
                    text = if (topSpeedKmh != null) {
                        String.format(Locale.US, "%.1f", animatedKmh)
                    } else {
                        "--.-"
                    },
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
                    text = if (topSpeedMph != null) {
                        String.format(Locale.US, "%.1f mph", animatedMph)
                    } else {
                        "--.- mph"
                    },
                    style = RcGearTheme.extendedTypography.hudUnit,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
