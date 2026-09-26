package io.github.taskengineer.rcgear.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.taskengineer.rcgear.R

// ============================================================
// 等幅フォント（アプリに同梱）
// ============================================================
// 以前は Google Fonts のダウンローダブルフォントで Roboto Mono を取得していたが、
// これは「完全オフライン」という設計方針と原理的に矛盾していた（DEBT-13 / S-8）。
// Play サービス非搭載の端末・初回起動時にネットワークが無い端末では、
// 何も告げずにプロポーショナルフォントへフォールバックし、HUD の桁が揃わない。
//
// 検証して様子を見るより同梱する方が安上がりなので、静的 TTF を res/font/ に置く。
// ライセンス: SIL Open Font License 1.1
//   本文は assets/licenses/RobotoMono-OFL.txt に同梱（OFL の頒布条件）
//   出典: https://github.com/googlefonts/RobotoMono

private val RobotoMono = FontFamily(
    Font(R.font.roboto_mono_regular, FontWeight.Normal),
    Font(R.font.roboto_mono_medium, FontWeight.Medium),
    Font(R.font.roboto_mono_bold, FontWeight.Bold)
)

// 通常テキスト用 (Roboto = プロポーショナル、Android標準)
// FontFamily.Default をそのまま使う
private val DefaultSans = FontFamily.Default

// ============================================================
// Material 3 Typography
// ============================================================
// 標準のロールに対してフォントを割り当てる。
// HUD調なので display 系は Roboto Mono、body 系は Default。

val RcGearTypography = Typography(
    // ディスプレイ系: 大きな数値表示に使う
    displayLarge  = TextStyle(fontFamily = RobotoMono, fontWeight = FontWeight.Bold,   fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontFamily = RobotoMono, fontWeight = FontWeight.Bold,   fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall  = TextStyle(fontFamily = RobotoMono, fontWeight = FontWeight.Medium, fontSize = 36.sp, lineHeight = 44.sp),

    // ヘッドライン: 中サイズの強調表示
    headlineLarge  = TextStyle(fontFamily = RobotoMono, fontWeight = FontWeight.Medium, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = RobotoMono, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall  = TextStyle(fontFamily = RobotoMono, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 32.sp),

    // タイトル系: 通常フォント（日本語含む）
    titleLarge  = TextStyle(fontFamily = DefaultSans, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = DefaultSans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall  = TextStyle(fontFamily = DefaultSans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),

    // ボディ系: 通常テキスト
    bodyLarge  = TextStyle(fontFamily = DefaultSans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = DefaultSans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall  = TextStyle(fontFamily = DefaultSans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),

    // ラベル系: ボタン・タブ等
    // ボトムナビは英大文字 + 等幅 (CALC / SETUPS / DB / CONFIG) の方針なので Mono を割り当て
    labelLarge  = TextStyle(fontFamily = RobotoMono, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.5.sp),
    labelMedium = TextStyle(fontFamily = RobotoMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall  = TextStyle(fontFamily = RobotoMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
)

// ============================================================
// 拡張タイポグラフィ - Material 3 標準に無い HUD 固有スタイル
// ============================================================
// 大型の最高速表示など、displayLarge より更に大きく/個性的にしたい場合のスロット。

data class RcGearExtendedTypography(
    val hudMega: TextStyle,   // 最高速の超大型表示用
    val hudMetric: TextStyle, // 派生メトリック（FDR, RPM等）の数値表示
    val hudUnit: TextStyle    // 単位（km/h, RPM 等）の小型表示
)

val DefaultExtendedTypography = RcGearExtendedTypography(
    hudMega   = TextStyle(fontFamily = RobotoMono, fontWeight = FontWeight.Bold,   fontSize = 72.sp, lineHeight = 80.sp, letterSpacing = (-1).sp),
    hudMetric = TextStyle(fontFamily = RobotoMono, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 28.sp),
    hudUnit   = TextStyle(fontFamily = RobotoMono, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.sp)
)

// CompositionLocal を使うと、Theme 配下の任意の Composable から
// `LocalRcGearTypography.current.hudMega` のようにアクセスできる。
val LocalRcGearTypography = staticCompositionLocalOf { DefaultExtendedTypography }