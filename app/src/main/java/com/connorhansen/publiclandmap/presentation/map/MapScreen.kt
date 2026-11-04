package com.connorhansen.publiclandmap.presentation.map

import android.Manifest
import android.annotation.SuppressLint
import android.os.Looper
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.connorhansen.publiclandmap.presentation.map.components.CompassButton
import com.connorhansen.publiclandmap.presentation.map.components.GpsButton
import com.connorhansen.publiclandmap.presentation.map.components.LayerPanel
import com.connorhansen.publiclandmap.presentation.map.components.ParcelDetailSheet
import com.connorhansen.publiclandmap.presentation.map.components.PublicLandSheet
import com.connorhansen.publiclandmap.presentation.map.components.SearchBar

@SuppressLint("MissingPermission")
@Composable
fun MapScreen(
    viewModel: MapViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    viewModel.updateUserLocation(location)
                }
            }
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.setLocationPermissionGranted(fineGranted || coarseGranted)
    }

    // Request permissions on first composition
    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    // Start/stop location updates based on permission
    DisposableEffect(state.locationPermissionGranted) {
        if (state.locationPermissionGranted) {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                5000L
            ).setMinUpdateIntervalMillis(2000L).build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }

        onDispose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Full-screen map
        MapLibreView(
            state = state,
            onCameraChanged = { lat, lng, zoom, bearing, tilt ->
                viewModel.updateCamera(lat, lng, zoom, bearing, tilt)
            },
            onMapClick = { layerId, featureId, properties ->
                viewModel.onMapClick(layerId, featureId, properties)
            },
            onRecenterConsumed = viewModel::onRecenterConsumed,
            onResetBearingConsumed = viewModel::onResetBearingConsumed,
            onFlyToConsumed = viewModel::onFlyToConsumed,
            modifier = Modifier.fillMaxSize()
        )

        // Search bar at top
        if (state.showSearchBar) {
            SearchBar(
                query = state.searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                searchResults = state.searchResults,
                isSearching = state.isSearching,
                onResultClick = viewModel::onSearchResultSelected,
                onClearSearch = viewModel::clearSearch,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
            )
        }

        // Right-side control buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Compass
            CompassButton(
                bearing = state.cameraBearing,
                onClick = { viewModel.resetBearing() }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Layer toggle
            FloatingActionButton(
                onClick = { viewModel.toggleLayerPanel() },
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 8.dp
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Layers,
                    contentDescription = "Toggle layer panel"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // GPS button
            GpsButton(
                isFollowingUser = state.isFollowingUser,
                hasLocation = state.userLocation != null,
                onClick = { viewModel.centerOnUser() }
            )
        }
    }

    // Layer panel bottom sheet
    if (state.isLayerPanelVisible) {
        LayerPanel(
            selectedBasemap = state.selectedBasemap,
            visibleLayers = state.visibleLayers,
            onBasemapSelected = { viewModel.setBasemap(it) },
            onLayerToggled = { viewModel.toggleLayer(it) },
            onDismiss = { viewModel.hideLayerPanel() }
        )
    }

    // Feature detail bottom sheets
    when (val feature = state.selectedFeature) {
        is MapViewModel.SelectedFeature.PublicLand -> {
            PublicLandSheet(
                feature = feature,
                publicLand = state.selectedPublicLand,
                regulations = state.selectedRegulations,
                isLoading = state.isLoadingFeatureDetails,
                onSaveWaypoint = {
                    if (viewModel.saveWaypointFromFeature()) {
                        Toast.makeText(context, "Waypoint saved", Toast.LENGTH_SHORT).show()
                    }
                },
                onDirections = {
                    viewModel.getDirectionsUri()?.let { uri ->
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        context.startActivity(intent)
                    }
                },
                onDismiss = { viewModel.clearSelectedFeature() }
            )
        }
        is MapViewModel.SelectedFeature.Parcel -> {
            ParcelDetailSheet(
                feature = feature,
                parcel = state.selectedParcel,
                isLoading = state.isLoadingFeatureDetails,
                onSaveWaypoint = {
                    if (viewModel.saveWaypointFromFeature()) {
                        Toast.makeText(context, "Waypoint saved", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { viewModel.clearSelectedFeature() }
            )
        }
        is MapViewModel.SelectedFeature.RecreationSite -> {
            PublicLandSheet(
                feature = MapViewModel.SelectedFeature.PublicLand(
                    id = feature.id,
                    properties = feature.properties
                ),
                publicLand = null,
                regulations = emptyList(),
                isLoading = false,
                onSaveWaypoint = {
                    if (viewModel.saveWaypointFromFeature()) {
                        Toast.makeText(context, "Waypoint saved", Toast.LENGTH_SHORT).show()
                    }
                },
                onDirections = {
                    viewModel.getDirectionsUri()?.let { uri ->
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        context.startActivity(intent)
                    }
                },
                onDismiss = { viewModel.clearSelectedFeature() }
            )
        }
        null -> { /* no selection */ }
    }
}
