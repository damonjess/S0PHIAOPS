package com.sophia.ops.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sophia.ops.ui.dashboard.DashboardScreen
import com.sophia.ops.ui.devices.DeviceDetailsScreen
import com.sophia.ops.ui.devices.DevicesScreen
import com.sophia.ops.ui.history.HistoryScreen
import com.sophia.ops.ui.more.MoreScreen
import com.sophia.ops.ui.radar.RadarScreen
import com.sophia.ops.ui.settings.SettingsScreen
import com.sophia.ops.ui.statistics.StatisticsScreen
import com.sophia.ops.viewmodel.DashboardViewModel
import com.sophia.ops.viewmodel.DeviceDetailsViewModel
import com.sophia.ops.viewmodel.DevicesViewModel
import com.sophia.ops.viewmodel.HistoryViewModel
import com.sophia.ops.viewmodel.StatisticsViewModel

object Routes {
    const val DASHBOARD = "dashboard"
    const val RADAR = "radar"
    const val DEVICES = "devices"
    const val DEVICE_DETAILS = "device_details/{type}/{address}"
    const val HISTORY = "history"
    const val MORE = "more"
    const val STATISTICS = "statistics"
    const val SETTINGS = "settings"
}

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen(Routes.DASHBOARD, "Home", Icons.Default.Home)
    object Atlas : Screen(Routes.RADAR, "Atlas", Icons.Default.Radar)
    object Devices : Screen(Routes.DEVICES, "Devices", Icons.Default.Devices)
    object History : Screen(Routes.HISTORY, "History", Icons.Default.History)
    object More : Screen(Routes.MORE, "More", Icons.Default.MoreHoriz)
}

@Composable
fun AppNavigation(
    viewModel: DashboardViewModel,
) {
    val navController = rememberNavController()
    // Four primary destinations plus a "More" hub keeps the bar uncluttered;
    // Statistics and Settings live one level deeper.
    val items = listOf(
        Screen.Home,
        Screen.Atlas,
        Screen.Devices,
        Screen.History,
        Screen.More,
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            if (currentDestination?.route != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id)
                                    launchSingleTop = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    vm = viewModel,
                    onNavigateToAtlas = { navController.navigate(Routes.RADAR) },
                )
            }
            composable(Routes.RADAR) {
                RadarScreen(
                    vm = viewModel,
                    onDeviceClick = { device ->
                        navController.navigate("device_details/${device.type.name}/${device.address}")
                    }
                )
            }
            composable(Routes.DEVICES) {
                val devicesVm: DevicesViewModel = viewModel()
                DevicesScreen(
                    vm = devicesVm,
                    dashboardVm = viewModel,
                    onDeviceClick = { device ->
                        navController.navigate("device_details/${device.type}/${device.address}")
                    }
                )
            }
            composable(Routes.HISTORY) {
                val historyVm: HistoryViewModel = viewModel()
                HistoryScreen(vm = historyVm)
            }
            composable(Routes.MORE) {
                MoreScreen(
                    onOpenStatistics = { navController.navigate(Routes.STATISTICS) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.DEVICE_DETAILS) { backStackEntry ->
                val type = backStackEntry.arguments?.getString("type") ?: ""
                val address = backStackEntry.arguments?.getString("address") ?: ""
                val detailsVm: DeviceDetailsViewModel = viewModel()
                DeviceDetailsScreen(
                    type = type,
                    address = address,
                    vm = detailsVm,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.STATISTICS) {
                val statsVm: StatisticsViewModel = viewModel()
                StatisticsScreen(vm = statsVm, onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(vm = viewModel, onBack = { navController.popBackStack() })
            }
        }
    }
}
