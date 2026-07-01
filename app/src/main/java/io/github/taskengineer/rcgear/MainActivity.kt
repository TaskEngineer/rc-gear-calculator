package io.github.taskengineer.rcgear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import io.github.taskengineer.rcgear.core.designsystem.theme.RcGearTheme
import io.github.taskengineer.rcgear.core.designsystem.theme.ThemeMode
import io.github.taskengineer.rcgear.navigation.RcGearApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // DataStore からテーマモードが読み込まれるまでスプラッシュを保持する。
        // 読み込みはミリ秒オーダーなので体感の待ちは発生しない。
        splashScreen.setKeepOnScreenCondition {
            viewModel.uiState.value is MainUiState.Loading
        }

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            val themeMode = when (val s = uiState) {
                is MainUiState.Success -> s.themeMode
                // Loading 中はスプラッシュに隠れているため何を返しても見えないが、
                // デフォルトのダークにしておく
                is MainUiState.Loading -> ThemeMode.DARK
            }

            RcGearTheme(themeMode = themeMode) {
                RcGearApp()
            }
        }
    }
}
