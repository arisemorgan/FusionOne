package com.fusionone.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fusionone.app.feature.football.FootballScreen
import com.fusionone.app.feature.photoanalysis.PhotoAnalysisScreen
import com.fusionone.app.feature.urlscanner.UrlScannerScreen
import com.fusionone.app.home.DashboardScreen
import com.fusionone.app.home.HistoryScreen
import com.fusionone.app.home.SettingsScreen

sealed class Destination(val route: String, val label: String) {
    data object Dashboard : Destination("dashboard", "Dashboard")
    data object Scanner : Destination("scanner", "Scanner")
    data object Live : Destination("live", "Live")
    data object Protect : Destination("protect", "Protect")
    data object Settings : Destination("settings", "Settings")
    data object History : Destination("history", "History") // pushed, not a bottom-nav tab
}

private val bottomNavItems = listOf(
    Destination.Dashboard,
    Destination.Scanner,
    Destination.Live,
    Destination.Protect,
    Destination.Settings
)

@Composable
fun FusionOneNavGraph(sharedUrl: String? = null) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination
                // Hide the bottom bar while on the pushed History screen so it reads as a
                // detail screen, not a sixth tab.
                val onHistory = currentDestination?.hierarchy?.any { it.route == Destination.History.route } == true
                if (!onHistory) {
                    bottomNavItems.forEach { destination ->
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    when (destination) {
                                        Destination.Dashboard -> Icons.Default.Dashboard
                                        Destination.Scanner -> Icons.Default.Search
                                        Destination.Live -> Icons.Default.SportsSoccer
                                        Destination.Protect -> Icons.Default.PhotoCamera
                                        else -> Icons.Default.Settings
                                    },
                                    contentDescription = destination.label
                                )
                            },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (sharedUrl != null) Destination.Scanner.route else Destination.Dashboard.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Destination.Dashboard.route) {
                DashboardScreen(
                    onOpenScanner = {
                        navController.navigate(Destination.Scanner.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenLive = {
                        navController.navigate(Destination.Live.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenSettings = {
                        navController.navigate(Destination.Settings.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenHistory = { navController.navigate(Destination.History.route) }
                )
            }
            composable(Destination.Scanner.route) {
                UrlScannerScreen(
                    sharedUrl = sharedUrl,
                    onNavigateHome = {
                        navController.navigate(Destination.Dashboard.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Destination.Live.route) {
                FootballScreen(
                    onNavigateHome = {
                        navController.navigate(Destination.Dashboard.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Destination.Protect.route) {
                PhotoAnalysisScreen(
                    onNavigateHome = {
                        navController.navigate(Destination.Dashboard.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Destination.Settings.route) { SettingsScreen() }
            composable(Destination.History.route) {
                HistoryScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
