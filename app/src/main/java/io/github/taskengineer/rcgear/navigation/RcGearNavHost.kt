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
import io.github.taskengineer.rcgear.feature.garage.CarDetailScreen
import io.github.taskengineer.rcgear.feature.garage.CarEditScreen
import io.github.taskengineer.rcgear.feature.garage.GarageScreen

/**
 * アプリ全体の NavHost。
 * トップレベル 4 画面 + 派生画面（車の新規作成・編集、シャーシ編集）。
 * シート系の画面は G-2 以降で入る。
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
        startDestination = Calc,
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
        composable<Garage> {
            GarageScreen(
                onCarClick = { carId -> navController.navigate(CarDetail(carId)) },
                onAddCarClick = { navController.navigate(CarCreate) }
            )
        }

        // 新規作成と編集は同じ画面。ルート引数の有無で ViewModel が振る舞いを変える
        composable<CarCreate> {
            CarEditScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable<CarEdit> {
            CarEditScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable<CarDetail> {
            // 引数のキー名を知っているのは Routes.kt だけにしたいので、
            // carId は NavBackStackEntry から読まずに画面（＝その ViewModel）から受け取る
            CarDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onEditCarClick = { carId -> navController.navigate(CarEdit(carId)) }
            )
        }

        composable<Calc> { CalcScreen() }

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
