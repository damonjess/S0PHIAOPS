package com.sophia.ops.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
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
import com.sophia.ops.ui.devices.DevicesScreen
import com.sophia.ops.ui.history.HistoryScreen
import com.sophia.ops.ui.radar.RadarScreen
import com.sophia.ops.ui.settings.SettingsScreen
import com.sophia.ops.viewmodel.DashboardViewModel
import com.sophia.ops.viewmodel.DevicesViewModel
import com.sophia.ops.viewmodel.HistoryViewModel

object Routes {
    const val DASHBOARD = "dashboard"
    const val RADAR = "radar"
    const val DEVICES = "devices"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard : Screen(Routes.DASHBOARD, "Dashboard", Icons.Default.Dashboard)
    object Radar : Screen(Routes.RADAR, "Radar", Icons.Default.Radar)
    object Devices : Screen(Routes.DEVICES, "Devices", Icons.Default.Devices)
    object History : Screen(Routes.HISTORY, "History", Icons.Default.History)
    object Settings : Screen(Routes.SETTINGS, "Settings", Icons.Default.Settings)
}

@Composable
fun AppNavigation(
    viewModel: DashboardViewModel
) {
    val navController = rememberNavController()
    val items = listOf(
        Screen.Dashboard,
        Screen.Radar,
        Screen.Devices,
        Screen.History,
        Screen.Settings
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
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
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
                DashboardScreen(vm = viewModel)
            }
            composable(Routes.RADAR) {
                RadarScreen(vm = viewModel)
            }
            composable(Routes.DEVICES) {
                val devicesVm: DevicesViewModel = viewModel()
                DevicesScreen(vm = devicesVm)
            }
            composable(Routes.HISTORY) {
                val historyVm: HistoryViewModel = viewModel()
                HistoryScreen(vm = historyVm)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
        }
    }
}
