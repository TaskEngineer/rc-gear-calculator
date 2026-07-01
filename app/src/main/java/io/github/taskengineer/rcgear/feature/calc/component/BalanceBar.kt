package io.github.taskengineer.rcgear.feature.calc.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

/**
 * セッティング傾向バー（PLAN 2.1.1 / アニメーションは Step 12）。
 *
 * 中央起点の双方向バー。基準 FDR との差を可視化する:
 * - 左方向（負・オレンジ）: 最高速寄り（FDR が基準より小さい）
 * - 右方向（正・グリーン）: トルク寄り（FDR が基準より大きい）
 *
 * @param balancePct -100（最高速側振り切り）〜 +100（トルク側振り切り）。null = シャーシ未選択
 * @param animationEnabled バーの追従アニメーションの有効/無効（ユーザー設定）
 */
@Composable
fun BalanceBar(
    balancePct: Double?,
    animationEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    // バーの伸縮を滑らかに追従させる。無効時は snap
    val animatedPct by animateFloatAsState(
        targetValue = balancePct?.toFloat() ?: 0f,
        animationSpec = if (animationEnabled) tween(durationMillis = 250) else tween(0),
        label = "balancePct"
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val centerColor = MaterialTheme.colorScheme.outline
    // 正 = トルク寄り → secondary(グリーン)、負 = 最高速寄り → tertiary(オレンジ)
    val torqueColor = MaterialTheme.colorScheme.secondary
    val speedColor = MaterialTheme.colorScheme.tertiary

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "セッティング傾向",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .height(12.dp)
            ) {
                val barHeight = size.height
                val centerX = size.width / 2f
                val corner = CornerRadius(barHeight / 2f)

                // 背景トラック
                drawRoundRect(color = trackColor, cornerRadius = corner)

                // 値バー: 中央から左右どちらかへ伸ばす。
                // ドメイン値は ±100 だが、バーの片側は全幅の 50% なので 2 で割る
                if (balancePct != null) {
                    val halfWidthPct = animatedPct / 2f // -50 〜 +50
                    val barWidth = size.width * (halfWidthPct / 100f)
                    val color = if (animatedPct >= 0) torqueColor else speedColor
                    val left = if (barWidth >= 0) centerX else centerX + barWidth
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(left, 0f),
                        size = Size(kotlin.math.abs(barWidth), barHeight),
                        cornerRadius = corner
                    )
                }

                // 中央線（基準 FDR の位置）
                drawRect(
                    color = centerColor,
                    topLeft = Offset(centerX - 1.dp.toPx() / 2f, -2.dp.toPx()),
                    size = Size(1.dp.toPx(), barHeight + 4.dp.toPx())
                )
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "◀ 最高速",
                    style = MaterialTheme.typography.labelSmall,
                    color = speedColor,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "トルク ▶",
                    style = MaterialTheme.typography.labelSmall,
                    color = torqueColor
                )
            }
        }
    }
}
