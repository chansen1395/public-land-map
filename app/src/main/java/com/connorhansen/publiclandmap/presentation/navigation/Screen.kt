package com.connorhansen.publiclandmap.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector? = null
) {
    data object Map : Screen(
        route = "map",
        title = "Map",
        icon = Icons.Filled.Map
    )

    data object Marks : Screen(
        route = "marks",
        title = "Marks",
        icon = Icons.Filled.Place
    )

    data object Waypoints : Screen(
        route = "waypoints",
        title = "Waypoints"
    )

    data object Tracks : Screen(
        route = "tracks",
        title = "Tracks"
    )

    data object Tools : Screen(
        route = "tools",
        title = "Tools",
        icon = Icons.Filled.Explore
    )

    data object Measure : Screen(
        route = "measure",
        title = "Measure"
    )

    data object Compass : Screen(
        route = "compass",
        title = "Compass"
    )

    data object WaypointEdit : Screen(
        route = "waypoint_edit",
        title = "Edit Waypoint"
    ) {
        fun routeWithArgs(waypointId: String? = null, lat: Double = 0.0, lng: Double = 0.0): String {
            return "waypoint_edit?waypointId=${waypointId ?: ""}&lat=$lat&lng=$lng"
        }
        const val routePattern = "waypoint_edit?waypointId={waypointId}&lat={lat}&lng={lng}"
    }

    data object TrackDetail : Screen(
        route = "track_detail",
        title = "Track Detail"
    ) {
        fun routeWithArgs(trackId: String): String = "track_detail/$trackId"
        const val routePattern = "track_detail/{trackId}"
    }

    data object Settings : Screen(
        route = "settings",
        title = "Settings",
        icon = Icons.Filled.Settings
    )

    data object Onboarding : Screen(
        route = "onboarding",
        title = "Onboarding"
    )

    data object Offline : Screen(
        route = "offline",
        title = "Offline Maps"
    )

    companion object {
        val bottomNavItems: List<Screen> get() = listOf(Map, Marks, Tools, Settings)
    }
}
