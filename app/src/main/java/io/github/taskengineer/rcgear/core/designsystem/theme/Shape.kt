package io.github.taskengineer.rcgear.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Material 3 標準のシェイプロールをカスタマイズ。
// HUD調なので角丸は控えめ (4dp が中心)。
val RcGearShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small      = RoundedCornerShape(4.dp),  // カードはこれを想定
    medium     = RoundedCornerShape(8.dp),
    large      = RoundedCornerShape(12.dp), // ボトムシート等
    extraLarge = RoundedCornerShape(16.dp)
)