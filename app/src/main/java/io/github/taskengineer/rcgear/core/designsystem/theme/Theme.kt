package io.github.taskengineer.rcgear.core.designsystem.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ============================================================
// テーマモード列挙型
// ============================================================
// DataStore 側でも同じ enum を使う前提。
// 後で domain/model/ に移しても良いが、designsystem 内に置くと
// Theme との結合が分かりやすい。
enum class ThemeMode { LIGHT, DARK, SYSTEM }

// ============================================================
// RcGearTheme - アプリ全体のテーマエントリポイント
// ============================================================
// 使い方:
//   setContent {
//       RcGearTheme(themeMode = ThemeMode.DARK) {
//           RcGearNavHost(...)
//       }
//   }
@Composable
fun RcGearTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    // SYSTEM 指定時は OS の設定に従う
    val useDark = when (themeMode) {
        ThemeMode.LIGHT  -> false
        ThemeMode.DARK   -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDark) DarkHudColorScheme else LightHudColorScheme

    // ステータスバー / ナビゲーションバーの色をテーマに合わせる
    // (エッジ・ツー・エッジ表示なのでバーは透過、アイコン色だけ調整)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // バーの背景色は透過のままにし、アイコン色のみ反転
            // useDark=true の場合 -> アイコンは明色 (false)
            // useDark=false の場合 -> アイコンは暗色 (true)
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars     = !useDark
                isAppearanceLightNavigationBars = !useDark
            }
        }
    }

    // CompositionLocalProvider で拡張タイポグラフィを配信
    CompositionLocalProvider(
        LocalRcGearTypography provides DefaultExtendedTypography
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = RcGearTypography,
            shapes      = RcGearShapes,
            content     = content
        )
    }
}

// ============================================================
// ショートカット - 拡張タイポグラフィへのアクセサ
// ============================================================
// 使い方:
//   Text("123", style = RcGearTheme.extendedTypography.hudMega)
object RcGearTheme {
    val extendedTypography: RcGearExtendedTypography
        @Composable get() = LocalRcGearTypography.current
}