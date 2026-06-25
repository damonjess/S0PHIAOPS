package com.sophia.ops.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sophia.ops.ui.dashboard.DashboardScreen
import com.sophia.ops.ui.radar.RadarScreen
import com.sophia.ops.viewmodel.DashboardViewModel

object Routes {
    const val DASHBOARD = "dashboard"
    const val RADAR = "radar"
}

@Composable
fun AppNavigation(
    viewModel: DashboardViewModel
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD
    ) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                vm = viewModel,
                onNavigateToRadar = {
                    navController.navigate(Routes.RADAR)
                }
            )
        }
        composable(Routes.RADAR) {
            RadarScreen(vm = viewModel)
        }
    }
}
