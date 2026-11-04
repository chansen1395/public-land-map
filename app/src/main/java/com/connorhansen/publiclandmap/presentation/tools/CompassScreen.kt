package com.connorhansen.publiclandmap.presentation.tools

import android.Manifest
import android.annotation.SuppressLint
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.math.cos
import kotlin.math.sin

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompassScreen(
    onNavigateBack: () -> Unit,
    viewModel: CompassViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val waypoints by viewModel.waypoints.collectAsState()
    val context = LocalContext.current
    var hasLocationPermission by remember { mutableStateOf(false) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { viewModel.updateLocation(it) }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission =
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    DisposableEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                3000L
            ).setMinUpdateIntervalMillis(1000L).build()

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compass") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Waypoint picker
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = state.selectedWaypoint?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Select Waypoint") },
                    placeholder = { Text("Choose a waypoint") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    if (waypoints.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No waypoints saved") },
                            onClick = { dropdownExpanded = false },
                            enabled = false
                        )
                    } else {
                        waypoints.forEach { waypoint ->
                            DropdownMenuItem(
                                text = { Text(waypoint.name) },
                                onClick = {
                                    viewModel.selectWaypoint(waypoint)
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (state.selectedWaypoint != null && state.bearingDegrees != null) {
                // Bearing display
                Text(
                    text = viewModel.formatBearing(state.bearingDegrees!!),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Distance display
                Text(
                    text = viewModel.formatDistance(state.distanceMeters ?: 0.0),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "to ${state.selectedWaypoint!!.name}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Compass rose with bearing arrow
                CompassRose(
                    bearingDegrees = state.bearingDegrees!!.toFloat(),
                    modifier = Modifier.size(240.dp)
                )
            } else if (state.selectedWaypoint != null) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Waiting for GPS location...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.weight(1f))
            } else {
                Spacer(modifier = Modifier.weight(1f))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Select a waypoint to see bearing and distance",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
            }

            // Current location info
            if (state.currentLat != null && state.currentLng != null) {
                Spacer(modifier = Modifier.weight(1f))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Current Location",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "%.6f, %.6f".format(state.currentLat, state.currentLng),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompassRose(
    bearingDegrees: Float,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val errorColor = MaterialTheme.colorScheme.error

    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val radius = minOf(centerX, centerY) * 0.85f

        // Draw outer circle
        drawCircle(
            color = onSurfaceVariant.copy(alpha = 0.2f),
            radius = radius,
            center = Offset(centerX, centerY),
            style = Stroke(width = 2f)
        )

        // Draw cardinal direction tick marks
        val cardinalAngles = listOf(0f, 90f, 180f, 270f)
        for (angle in cardinalAngles) {
            val radians = Math.toRadians(angle.toDouble() - 90.0)
            val innerR = radius * 0.85f
            val outerR = radius
            drawLine(
                color = onSurfaceVariant.copy(alpha = 0.5f),
                start = Offset(
                    centerX + innerR * cos(radians).toFloat(),
                    centerY + innerR * sin(radians).toFloat()
                ),
                end = Offset(
                    centerX + outerR * cos(radians).toFloat(),
                    centerY + outerR * sin(radians).toFloat()
                ),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }

        // Draw minor tick marks (every 30 degrees)
        for (i in 0 until 12) {
            val angle = i * 30f
            if (angle % 90f != 0f) {
                val radians = Math.toRadians(angle.toDouble() - 90.0)
                val innerR = radius * 0.9f
                val outerR = radius
                drawLine(
                    color = onSurfaceVariant.copy(alpha = 0.3f),
                    start = Offset(
                        centerX + innerR * cos(radians).toFloat(),
                        centerY + innerR * sin(radians).toFloat()
                    ),
                    end = Offset(
                        centerX + outerR * cos(radians).toFloat(),
                        centerY + outerR * sin(radians).toFloat()
                    ),
                    strokeWidth = 1.5f,
                    cap = StrokeCap.Round
                )
            }
        }

        // North indicator (red triangle at top)
        val northRadians = Math.toRadians(-90.0)
        val northTipR = radius + 8f
        val northTip = Offset(
            centerX + northTipR * cos(northRadians).toFloat(),
            centerY + northTipR * sin(northRadians).toFloat()
        )
        val northLeft = Offset(
            centerX + (radius - 10f) * cos(Math.toRadians(-95.0)).toFloat(),
            centerY + (radius - 10f) * sin(Math.toRadians(-95.0)).toFloat()
        )
        val northRight = Offset(
            centerX + (radius - 10f) * cos(Math.toRadians(-85.0)).toFloat(),
            centerY + (radius - 10f) * sin(Math.toRadians(-85.0)).toFloat()
        )
        drawPath(
            path = Path().apply {
                moveTo(northTip.x, northTip.y)
                lineTo(northLeft.x, northLeft.y)
                lineTo(northRight.x, northRight.y)
                close()
            },
            color = errorColor
        )

        // Bearing arrow
        rotate(bearingDegrees, pivot = Offset(centerX, centerY)) {
            val arrowLength = radius * 0.7f
            val arrowHeadSize = 20f

            // Arrow shaft
            drawLine(
                color = primaryColor,
                start = Offset(centerX, centerY),
                end = Offset(centerX, centerY - arrowLength),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )

            // Arrow head
            val tipY = centerY - arrowLength
            drawPath(
                path = Path().apply {
                    moveTo(centerX, tipY - arrowHeadSize)
                    lineTo(centerX - arrowHeadSize * 0.6f, tipY + arrowHeadSize * 0.3f)
                    lineTo(centerX + arrowHeadSize * 0.6f, tipY + arrowHeadSize * 0.3f)
                    close()
                },
                color = primaryColor
            )

            // Center dot
            drawCircle(
                color = primaryColor,
                radius = 6f,
                center = Offset(centerX, centerY)
            )
        }
    }
}
