package io.github.taskengineer.rcgear.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import io.github.taskengineer.rcgear.feature.calc.CalcScreen
import io.github.taskengineer.rcgear.feature.config.ConfigScreen
import io.github.taskengineer.rcgear.feature.db.DbScreen
import io.github.taskengineer.rcgear.feature.setups.SetupsScreen

/**
 * アプリ全体の NavHost。
 *
 * 現時点ではトップレベル4画面のみ。
 * 派生画面（セッティング詳細、シャーシ編集）は Step 9 / 10 でここに追加する。
 */
@Composable
fun RcGearNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.CALC,
        modifier = modifier
    ) {
        composable(Routes.CALC) { CalcScreen() }
        composable(Routes.SETUPS) { SetupsScreen() }
        composable(Routes.DB) { DbScreen() }
        composable(Routes.CONFIG) { ConfigScreen() }
    }
}
