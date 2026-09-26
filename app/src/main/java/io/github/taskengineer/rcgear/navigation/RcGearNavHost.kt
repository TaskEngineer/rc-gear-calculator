package io.github.taskengineer.rcgear.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import io.github.taskengineer.rcgear.feature.calc.CalcScreen
import io.github.taskengineer.rcgear.feature.config.ConfigScreen
import io.github.taskengineer.rcgear.feature.db.ChassisEditScreen
import io.github.taskengineer.rcgear.feature.db.DbScreen
import io.github.taskengineer.rcgear.feature.setups.SetupDetailScreen
import io.github.taskengineer.rcgear.feature.setups.SetupsScreen

/**
 * アプリ全体の NavHost。
 * トップレベル4画面 + 派生画面（セッティング詳細、シャーシ編集）。
 *
 * ルートは型安全（`@Serializable` なクラス）で指定する（S-12）。
 * 引数の型・名前は [Routes.kt] のクラス定義が唯一の宣言で、`navArgument` は要らない。
 */
@Composable
fun RcGearNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Calc(),
        modifier = modifier,
        // 画面遷移アニメーション（Step 12）:
        // タブ切替はフェード + わずかな縦スライドで軽快に見せる。
        // 派手なスライドはタブ UI では方向の意味が破綻するため使わない。
        enterTransition = {
            fadeIn(animationSpec = tween(200)) +
                slideInVertically(animationSpec = tween(200)) { it / 40 }
        },
        exitTransition = { fadeOut(animationSpec = tween(150)) },
        popEnterTransition = { fadeIn(animationSpec = tween(200)) },
        popExitTransition = { fadeOut(animationSpec = tween(150)) }
    ) {
        composable<Calc> { CalcScreen() }

        composable<Setups> {
            SetupsScreen(
                onSetupClick = { setupId -> navController.navigate(SetupDetail(setupId)) }
            )
        }

        composable<SetupDetail> {
            SetupDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onLoadToCalc = { setupId ->
                    // 「CALC に流し込む」特殊遷移（PLAN 5.3 / U-3）:
                    // 値はルート引数で渡す。
                    // 既存の CALC エントリは inclusive で破棄して置き換える。
                    // restoreState を付けると保存済みの状態（= 古い引数）が復元されて
                    // 新しい setupId が無視されるため、ここでは使わない。
                    navController.navigate(Calc(setupId = setupId)) {
                        popUpTo<Calc> { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<Db> {
            DbScreen(
                onChassisClick = { chassisId -> navController.navigate(ChassisEdit(chassisId)) }
            )
        }

        composable<ChassisEdit> {
            ChassisEditScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Config> { ConfigScreen() }
    }
}
