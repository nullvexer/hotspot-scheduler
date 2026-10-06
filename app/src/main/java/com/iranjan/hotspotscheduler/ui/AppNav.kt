package com.iranjan.hotspotscheduler.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.MedicalInformation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.iranjan.hotspotscheduler.R
import com.iranjan.hotspotscheduler.ui.dashboard.DashboardScreen
import com.iranjan.hotspotscheduler.ui.diagnostics.DiagnosticsScreen
import com.iranjan.hotspotscheduler.ui.routines.RoutinesScreen
import com.iranjan.hotspotscheduler.ui.setup.SetupScreen
import com.iranjan.hotspotscheduler.ui.editor.RoutineEditorScreen
import com.iranjan.hotspotscheduler.ui.calibration.CalibrationScreen

private data class BottomItem(val route: String, val icon: ImageVector, val labelRes: Int)

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val items = listOf(
        BottomItem("dashboard", Icons.Filled.DataUsage, R.string.nav_dashboard),
        BottomItem("routines", Icons.Filled.AccessTime, R.string.nav_routines),
        BottomItem("diagnostics", Icons.Filled.MedicalInformation, R.string.nav_diagnostics),
        BottomItem("setup", Icons.Filled.Settings, R.string.nav_setup)
    )
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute in items.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    items.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(padding)
        ) {
            composable("dashboard") {
                DashboardScreen(
                    onNavigateToRoutines = { navController.navigate("routines") },
                    onNavigateToDiagnostics = { navController.navigate("diagnostics") },
                    onNavigateToSetup = { navController.navigate("setup") }
                )
            }
            composable("routines") {
                RoutinesScreen(
                    onNewRoutine = { navController.navigate("editor/-1") },
                    onEditRoutine = { routine -> navController.navigate("editor/${routine.id}") }
                )
            }
            composable("diagnostics") {
                DiagnosticsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable("setup") {
                SetupScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCalibration = { navController.navigate("calibration") }
                )
            }
            composable("calibration") {
                CalibrationScreen(onDone = { navController.popBackStack() })
            }
            composable(
                route = "editor/{routineId}",
                arguments = listOf(androidx.navigation.navArgument("routineId") { type = androidx.navigation.NavType.LongType })
            ) { entry ->
                val id = entry.getLong("routineId", -1)
                RoutineEditorScreen(
                    routineId = id,
                    onDone = { navController.popBackStack() }
                )
            }
        }
    }
}