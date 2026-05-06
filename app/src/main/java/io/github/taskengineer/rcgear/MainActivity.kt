package io.github.taskengineer.rcgear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint

/**
 * アプリのメイン画面。
 * Step 1 の段階では起動確認用に "Hello, RC Gear!" を表示するだけ。
 * Step 2 以降でテーマシステム・ナビゲーションに置き換えていく。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // SplashScreen API（Android 12+ ではシステムが自動表示、それ以下は互換実装）
        // installSplashScreen() は super.onCreate() の前に呼ぶ必要がある
        installSplashScreen()

        super.onCreate(savedInstanceState)

        // エッジ・ツー・エッジ表示（システムバーをコンテンツの裏に透過）
        enableEdgeToEdge()

        setContent {
            // TODO: Step 2 で RcGearTheme に差し替え
            MaterialTheme {
                HelloScreen()
            }
        }
    }
}

@Composable
private fun HelloScreen() {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Hello, RC Gear!")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HelloScreenPreview() {
    MaterialTheme {
        HelloScreen()
    }
}
