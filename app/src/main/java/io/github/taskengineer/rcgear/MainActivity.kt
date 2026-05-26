package io.github.taskengineer.rcgear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import io.github.taskengineer.rcgear.core.designsystem.theme.RcGearTheme
import io.github.taskengineer.rcgear.core.designsystem.theme.ThemeMode
import io.github.taskengineer.rcgear.data.local.asset.ChassisJsonProvider
import io.github.taskengineer.rcgear.domain.model.Maker
import javax.inject.Inject

/**
 * [DEBUG - Step 4 動作確認用]
 * ChassisJsonProvider が assets/chassis-db.json を正しく読み込めるかを目視確認する。
 * Step 7（ナビゲーション骨組み）で本来の実装に置き換える。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Hilt による Field Injection
    // ChassisJsonProvider は @Inject constructor を持つので、ここで取得できる。
    // 取得できなければ Hilt のグラフが壊れている = DataModule 側に問題あり。
    @Inject
    lateinit var chassisJsonProvider: ChassisJsonProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RcGearTheme(themeMode = ThemeMode.DARK) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        ChassisDbDebugScreen(provider = chassisJsonProvider)
                    }
                }
            }
        }
    }
}

// =============================================================
// 以下、DEBUG 用 Composable
// =============================================================

/**
 * 読み込み状態を表す封印クラス。
 * sealed interface で書くと when 式の網羅性チェックが効いて安全。
 */
private sealed interface LoadState {
    data object Loading : LoadState
    data class Success(val makers: List<Maker>) : LoadState
    data class Error(val message: String) : LoadState
}

@Composable
private fun ChassisDbDebugScreen(provider: ChassisJsonProvider) {
    // 状態保持。remember + mutableStateOf で再コンポーズ時にも値を保つ。
    var state by remember { mutableStateOf<LoadState>(LoadState.Loading) }

    // LaunchedEffect(Unit) で「初回コンポーズ時に1回だけ」非同期処理を起動。
    // この Composable がツリーから外れるとコルーチンも自動キャンセルされる。
    LaunchedEffect(Unit) {
        state = try {
            val makers = provider.getMakers()
            LoadState.Success(makers)
        } catch (e: Exception) {
            // 例外は型と message を併記すると原因が掴みやすい
            LoadState.Error("${e::class.simpleName}: ${e.message}")
        }
    }

    when (val s = state) {
        is LoadState.Loading -> LoadingView()
        is LoadState.Error   -> ErrorView(s.message)
        is LoadState.Success -> SuccessView(s.makers)
    }
}

@Composable
private fun LoadingView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Loading chassis-db.json...", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorView(message: String) {
    // エラーは派手に出して見落とさないようにする
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "❌ 読み込み失敗",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.error
            )
        }
        Text(
            "考えられる原因:\n" +
                    "  • assets/chassis-db.json が配置されていない\n" +
                    "  • JSON 構文エラー\n" +
                    "  • DTO のフィールド名が JSON のキーと不一致",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SuccessView(makers: List<Maker>) {
    val totalChassis = makers.sumOf { it.chassis.size }

    // LazyColumn を使うのは、全エントリ（45件）を表示しても軽快に動かすため。
    // Column + verticalScroll でも動くが、エントリが増えた時に重くなる。
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. ヘッダ（サマリー情報）
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "✅ JSON 読み込み成功",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("メーカー数: ${makers.size}", style = MaterialTheme.typography.bodySmall)
                    Text("総シャーシ数: $totalChassis", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "(期待値: 9 メーカー / 45 エントリ)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. メーカー単位でグルーピング表示
        makers.forEach { maker ->
            // メーカーヘッダ
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "▼ ${maker.name}  (${maker.chassis.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider()
            }

            // 各シャーシエントリ
            items(maker.chassis, key = { it.id }) { chassis ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // 1行目: 表示名
                        Text(chassis.name, style = MaterialTheme.typography.titleSmall)

                        // 2行目: ID（小さく、等幅で）
                        Text(
                            chassis.id,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(4.dp))

                        // 3行目: 数値メトリクス（等幅フォントが揃って見えるかの確認ポイント）
                        Text(
                            "internalRatio=${chassis.internalRatio}  " +
                                    "tire=${chassis.defaultTireMm}mm",
                            style = RcGearTheme.extendedTypography.hudMetric.copy(fontSize = 13.sp)
                        )

                        // 4行目: note（あれば）
                        chassis.note?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}