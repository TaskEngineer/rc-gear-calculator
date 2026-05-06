package io.github.taskengineer.rcgear.core.designsystem.theme

import androidx.compose.ui.graphics.Color

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

// ============================================================
// Raw Color Palette - HUD調のベースカラー
// ============================================================
// これらの色は ColorScheme から参照される「素材」。
// 直接 Composable から使うのは原則禁止（MaterialTheme.colorScheme 経由で使う）。

// --- ダークテーマ用（メイン想定） ---
val HudBgBase       = Color(0xFF0A0E14)  // 背景: 黒に近い濃紺
val HudSurface      = Color(0xFF0D1D2C)  // カード等のサーフェス
val HudSurfaceHigh  = Color(0xFF142A3D)  // 一段明るいサーフェス（ダイアログ等）
val HudOutline      = Color(0xFF2A4055)  // 枠線

val HudTeal         = Color(0xFF5DCAA5)  // プライマリアクセント（最高速等）
val HudGreen        = Color(0xFF97C459)  // セカンダリ（トルク寄り表示等）
val HudOrange       = Color(0xFFEF9F27)  // 警告/強調（最高速寄り表示等）
val HudRed          = Color(0xFFE26464)  // エラー

val HudTextPrimary    = Color(0xFFE8F1F8) // 主要テキスト（ほぼ白）
val HudTextSecondary  = Color(0xFFA0B4C5) // 補助テキスト

// --- ライトテーマ用 ---
// ダークと同じアクセントを使いつつ、背景を明るくする
val HudLightBg          = Color(0xFFF4F7FA)
val HudLightSurface     = Color(0xFFFFFFFF)
val HudLightSurfaceHigh = Color(0xFFE8EEF4)
val HudLightOutline     = Color(0xFFC4D0DC)
val HudLightTextPrimary   = Color(0xFF0A1520)
val HudLightTextSecondary = Color(0xFF4A5C6E)

// ============================================================
// Material 3 ColorScheme - HUD調にカスタマイズ
// ============================================================
// Material 3 の標準色トークンに HUD カラーをマッピング。
// 各トークンの意味:
//   primary       : 主要なアクセント（FAB, ボタン, 強調表示）
//   secondary     : 補助アクセント
//   tertiary      : 第3アクセント（警告ではない強調）
//   surface       : カードやシートの背景
//   surfaceVariant: 一段違うサーフェス（区切り感を出す）
//   outline       : 枠線
//   onXxx         : Xxx の上に置くテキスト/アイコン色

val DarkHudColorScheme = darkColorScheme(
    primary           = HudTeal,
    onPrimary         = HudBgBase,
    primaryContainer  = HudSurfaceHigh,
    onPrimaryContainer = HudTeal,

    secondary         = HudGreen,
    onSecondary       = HudBgBase,

    tertiary          = HudOrange,
    onTertiary        = HudBgBase,

    background        = HudBgBase,
    onBackground      = HudTextPrimary,

    surface           = HudSurface,
    onSurface         = HudTextPrimary,
    surfaceVariant    = HudSurfaceHigh,
    onSurfaceVariant  = HudTextSecondary,

    outline           = HudOutline,
    error             = HudRed,
    onError           = HudBgBase
)

val LightHudColorScheme = lightColorScheme(
    primary           = HudTeal,
    onPrimary         = HudLightSurface,
    primaryContainer  = HudLightSurfaceHigh,
    onPrimaryContainer = HudTeal,

    secondary         = HudGreen,
    onSecondary       = HudLightSurface,

    tertiary          = HudOrange,
    onTertiary        = HudLightSurface,

    background        = HudLightBg,
    onBackground      = HudLightTextPrimary,

    surface           = HudLightSurface,
    onSurface         = HudLightTextPrimary,
    surfaceVariant    = HudLightSurfaceHigh,
    onSurfaceVariant  = HudLightTextSecondary,

    outline           = HudLightOutline,
    error             = HudRed,
    onError           = HudLightSurface
)