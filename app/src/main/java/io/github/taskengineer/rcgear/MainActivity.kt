package io.github.taskengineer.rcgear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
// ↓ Step 2 で追加するインポート
import io.github.taskengineer.rcgear.core.designsystem.theme.RcGearTheme
import io.github.taskengineer.rcgear.core.designsystem.theme.ThemeMode

/**
 * アプリのメイン画面。
 * Step 2: RcGearTheme を適用し、HUD調のカラー・タイポグラフィの動作確認を行う。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // SplashScreen API（installSplashScreen は super.onCreate() の前に）
        installSplashScreen()

        super.onCreate(savedInstanceState)

        // エッジ・ツー・エッジ表示
        enableEdgeToEdge()

        setContent {
            // ★ ここを RcGearTheme に変更
            // themeMode は Step 6 で DataStore から読むようにするが、
            // 今は動作確認のため固定で DARK を指定
            RcGearTheme(themeMode = ThemeMode.DARK) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        ThemePreviewContent()
                    }
                }
            }
        }
    }
}

/**
 * テーマシステムが効いているかの動作確認用画面。
 * Step 7 でナビゲーションに置き換える際に削除する。
 */
@Composable
private fun ThemePreviewContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // タイトル系（日本語、プロポーショナル）
        Text("RC ギア比計算機", style = MaterialTheme.typography.titleLarge)
        Text("Step 2: テーマシステム動作確認", style = MaterialTheme.typography.bodyMedium)

        // 大型数値表示（拡張タイポグラフィ）
        // ★ 等幅フォント (Roboto Mono) が効いているかの確認ポイント
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("TOP SPEED", style = MaterialTheme.typography.labelMedium)
                Text("48.32", style = RcGearTheme.extendedTypography.hudMega)
                Text("km/h", style = RcGearTheme.extendedTypography.hudUnit)
            }
        }

        // 派生メトリック（数値が等幅で揃うかの確認）
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("FDR     7.83", style = RcGearTheme.extendedTypography.hudMetric)
                Text("RPM 28492", style = RcGearTheme.extendedTypography.hudMetric)
            }
        }

        // ボタン群（アクセント色の確認）
        Button(onClick = {}) { Text("PRIMARY ACTION") }
        OutlinedButton(onClick = {}) { Text("SECONDARY") }
    }
}

@Preview(showBackground = true, name = "Dark")
@Composable
private fun PreviewDark() {
    RcGearTheme(themeMode = ThemeMode.DARK) {
        Surface { ThemePreviewContent() }
    }
}

@Preview(showBackground = true, name = "Light")
@Composable
private fun PreviewLight() {
    RcGearTheme(themeMode = ThemeMode.LIGHT) {
        Surface { ThemePreviewContent() }
    }
}