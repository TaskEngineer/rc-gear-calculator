package io.github.taskengineer.rcgear.feature.calc.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput
import kotlin.math.cos
import kotlin.math.sin

/**
 * ピニオン / スパーのギア表示（Web 版のギアSVG を Compose Canvas に移植、PLAN Step 8）。
 *
 * 歯数に比例した半径の2枚のギアを噛み合わせて描く。
 * 歯はギア円周上の短い放射線で表現する（歯数 = 実際の本数）。
 * 回転アニメーションは Step 12 で追加予定。
 */
@Composable
fun GearDiagram(
    pinion: Int,
    spur: Int,
    modifier: Modifier = Modifier
) {
    val pinionColor = MaterialTheme.colorScheme.tertiary   // 小ギア: オレンジ
    val spurColor = MaterialTheme.colorScheme.primary      // 大ギア: ティール
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "ギア構成  PINION ${pinion}T / SPUR ${spur}T",
                style = MaterialTheme.typography.labelMedium,
                color = textColor
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .height(140.dp)
            ) {
                // ---- レイアウト計算 ----
                // 2つのギアを横に並べ、接するように配置する。
                // 半径は歯数に比例させ、合計が描画幅に収まるようスケールする。
                val toothLen = 6.dp.toPx()
                val margin = 8.dp.toPx()

                val totalTeeth = (pinion + spur).toFloat()
                // 直径合計 + 歯の高さ + マージンが width に収まる半径スケールを求める
                val availableW = size.width - margin * 2f - toothLen * 4f
                val availableH = size.height - margin * 2f - toothLen * 2f
                // スパー（大きい方）の直径が高さも超えないように制約
                val scaleByWidth = availableW / (2f * totalTeeth)
                val scaleByHeight = availableH / (2f * maxOf(pinion, spur).toFloat())
                val unit = minOf(scaleByWidth, scaleByHeight)

                val rPinion = pinion * unit
                val rSpur = spur * unit

                // 全体を中央寄せ
                val contentW = (rPinion + rSpur) * 2f
                val startX = (size.width - contentW) / 2f
                val centerY = size.height / 2f
                val pinionCenter = Offset(startX + rPinion, centerY)
                val spurCenter = Offset(startX + rPinion * 2f + rSpur, centerY)

                drawGear(pinionCenter, rPinion, pinion, toothLen, pinionColor)
                drawGear(spurCenter, rSpur, spur, toothLen, spurColor)
            }
        }
    }
}

/**
 * ギア1枚の描画: 本体円 + 歯（放射線） + 中心軸穴。
 */
private fun DrawScope.drawGear(
    center: Offset,
    radius: Float,
    teeth: Int,
    toothLen: Float,
    color: Color
) {
    val bodyStroke = Stroke(width = 2.dp.toPx())

    // 本体円
    drawCircle(color = color, radius = radius, center = center, style = bodyStroke)

    // 歯: 円周を歯数で等分した放射線
    val toothStroke = 1.5.dp.toPx()
    repeat(teeth) { i ->
        val angle = 2.0 * Math.PI * i / teeth
        val cosA = cos(angle).toFloat()
        val sinA = sin(angle).toFloat()
        drawLine(
            color = color,
            start = Offset(center.x + radius * cosA, center.y + radius * sinA),
            end = Offset(center.x + (radius + toothLen) * cosA, center.y + (radius + toothLen) * sinA),
            strokeWidth = toothStroke
        )
    }

    // 中心の軸穴
    drawCircle(color = color, radius = 3.dp.toPx(), center = center)
}
