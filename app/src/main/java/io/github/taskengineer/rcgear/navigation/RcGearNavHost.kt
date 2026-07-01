package io.github.taskengineer.rcgear.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.taskengineer.rcgear.feature.calc.CalcScreen
import io.github.taskengineer.rcgear.feature.config.ConfigScreen
import io.github.taskengineer.rcgear.feature.db.ChassisEditScreen
import io.github.taskengineer.rcgear.feature.db.DbScreen
import io.github.taskengineer.rcgear.feature.setups.SetupDetailScreen
import io.github.taskengineer.rcgear.feature.setups.SetupsScreen

/**
 * アプリ全体の NavHost。
 * トップレベル4画面 + 派生画面（セッティング詳細、シャーシ編集）。
 */
@Composable
fun RcGearNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.CALC,
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
        composable(Routes.CALC) { CalcScreen() }

        composable(Routes.SETUPS) {
            SetupsScreen(
                onSetupClick = { setupId ->
                    navController.navigate("setups/$setupId")
                }
            )
        }

        composable(
            route = Routes.SETUP_DETAIL,
            arguments = listOf(navArgument("setupId") { type = NavType.LongType })
        ) {
            SetupDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onLoadToCalc = {
                    // 「CALC に流し込む」特殊遷移（PLAN 5.3）:
                    // 値の受け渡しは CalcRequestBus 経由。ここではタブを CALC に切り替えるだけ
                    navController.navigate(Routes.CALC) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        composable(Routes.DB) {
            DbScreen(
                onChassisClick = { chassisId ->
                    navController.navigate("db/$chassisId")
                }
            )
        }

        composable(
            route = Routes.CHASSIS_EDIT,
            arguments = listOf(navArgument("chassisId") { type = NavType.StringType })
        ) {
            ChassisEditScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CONFIG) { ConfigScreen() }
    }
}
