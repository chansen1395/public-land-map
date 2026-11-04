package com.connorhansen.publiclandmap.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.connorhansen.publiclandmap.presentation.map.MapScreen
import com.connorhansen.publiclandmap.presentation.marks.MarksScreen
import com.connorhansen.publiclandmap.presentation.offline.OfflineScreen
import com.connorhansen.publiclandmap.presentation.onboarding.OnboardingScreen
import com.connorhansen.publiclandmap.presentation.settings.SettingsScreen
import com.connorhansen.publiclandmap.presentation.tools.CompassScreen
import com.connorhansen.publiclandmap.presentation.tools.MeasureScreen
import com.connorhansen.publiclandmap.presentation.tools.ToolsScreen
import com.connorhansen.publiclandmap.presentation.tracks.TrackDetailScreen
import com.connorhansen.publiclandmap.presentation.tracks.TracksScreen
import com.connorhansen.publiclandmap.presentation.tracks.TracksViewModel
import com.connorhansen.publiclandmap.presentation.waypoints.WaypointEditScreen
import com.connorhansen.publiclandmap.presentation.waypoints.WaypointsScreen
import com.connorhansen.publiclandmap.presentation.waypoints.WaypointsViewModel

@Composable
fun AppNavigation(
    navController: NavHostController,
    onOnboardingComplete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Map.route,
        modifier = modifier
    ) {
        composable(Screen.Map.route) {
            MapScreen()
        }
        composable(Screen.Marks.route) {
            MarksScreen(
                onNavigateToWaypoints = {
                    navController.navigate(Screen.Waypoints.route)
                },
                onNavigateToTracks = {
                    navController.navigate(Screen.Tracks.route)
                }
            )
        }
        composable(Screen.Waypoints.route) {
            val viewModel: WaypointsViewModel = hiltViewModel()
            WaypointsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { waypointId ->
                    navController.navigate(
                        Screen.WaypointEdit.routeWithArgs(waypointId = waypointId)
                    )
                },
                onNavigateToNew = {
                    // Use 0,0 as default; in a real app this would be current map center or GPS
                    navController.navigate(
                        Screen.WaypointEdit.routeWithArgs(lat = 47.0, lng = -120.5)
                    )
                },
                onNavigateToMap = { lat, lng ->
                    // Navigate back to map — the map will handle centering via the FlyToTarget
                    navController.navigate(Screen.Map.route) {
                        popUpTo(Screen.Map.route) { inclusive = true }
                    }
                },
                viewModel = viewModel
            )
        }
        composable(
            route = Screen.WaypointEdit.routePattern,
            arguments = listOf(
                navArgument("waypointId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("lat") {
                    type = NavType.FloatType
                    defaultValue = 0f
                },
                navArgument("lng") {
                    type = NavType.FloatType
                    defaultValue = 0f
                }
            )
        ) { backStackEntry ->
            val waypointId = backStackEntry.arguments?.getString("waypointId") ?: ""
            val lat = backStackEntry.arguments?.getFloat("lat")?.toDouble() ?: 0.0
            val lng = backStackEntry.arguments?.getFloat("lng")?.toDouble() ?: 0.0

            // Share ViewModel with parent waypoints route
            val parentEntry = navController.getBackStackEntry(Screen.Waypoints.route)
            val viewModel: WaypointsViewModel = hiltViewModel(parentEntry)

            if (waypointId.isNotEmpty()) {
                viewModel.loadForEdit(waypointId)
            } else {
                viewModel.prepareNewWaypoint(lat, lng)
            }

            WaypointEditScreen(
                onNavigateBack = { navController.popBackStack() },
                viewModel = viewModel
            )
        }
        composable(Screen.Tracks.route) {
            val viewModel: TracksViewModel = hiltViewModel()
            TracksScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { trackId ->
                    navController.navigate(Screen.TrackDetail.routeWithArgs(trackId))
                },
                viewModel = viewModel
            )
        }
        composable(
            route = Screen.TrackDetail.routePattern,
            arguments = listOf(
                navArgument("trackId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val trackId = backStackEntry.arguments?.getString("trackId") ?: ""
            val parentEntry = navController.getBackStackEntry(Screen.Tracks.route)
            val viewModel: TracksViewModel = hiltViewModel(parentEntry)

            TrackDetailScreen(
                trackId = trackId,
                onNavigateBack = { navController.popBackStack() },
                viewModel = viewModel
            )
        }
        composable(Screen.Tools.route) {
            ToolsScreen(
                onNavigateToMeasure = {
                    navController.navigate(Screen.Measure.route)
                },
                onNavigateToCompass = {
                    navController.navigate(Screen.Compass.route)
                }
            )
        }
        composable(Screen.Measure.route) {
            MeasureScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Compass.route) {
            CompassScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToOffline = {
                    navController.navigate(Screen.Offline.route)
                }
            )
        }
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinished = {
                    onOnboardingComplete()
                    navController.navigate(Screen.Map.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Offline.route) {
            OfflineScreen()
        }
    }
}
