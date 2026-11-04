package com.connorhansen.publiclandmap.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SettingsScreen(
    onNavigateToOffline: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.cacheCleared) {
        if (state.cacheCleared) {
            snackbarHostState.showSnackbar("Cache cleared successfully")
            viewModel.dismissCacheCleared()
        }
    }

    val coordFormats = listOf("Decimal Degrees", "Degrees Minutes", "Degrees Minutes Seconds")
    val basemaps = listOf("Topographic", "Satellite", "Hybrid", "OpenStreetMap")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )

        // Units section
        SectionHeader("Units")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Imperial Units", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (state.useImperial) "Miles, feet, acres" else "Kilometers, meters, hectares",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.useImperial,
                onCheckedChange = { viewModel.setUseImperial(it) }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        // Coordinate Format section
        SectionHeader("Coordinate Format")
        coordFormats.forEachIndexed { index, label ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setCoordinateFormat(index) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = state.coordinateFormat == index,
                    onClick = { viewModel.setCoordinateFormat(index) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        // Default Basemap section
        SectionHeader("Default Basemap")
        basemaps.forEachIndexed { index, label ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setDefaultBasemap(index) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = state.defaultBasemap == index,
                    onClick = { viewModel.setDefaultBasemap(index) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        // Offline Maps
        SectionHeader("Data")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToOffline() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Map,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text("Offline Maps", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }

        // Clear Cache
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showClearCacheDialog = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.DeleteSweep,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Clear Cache", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Remove cached parcel and land data",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.isClearingCache) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        // About section
        SectionHeader("About")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showAboutDialog = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Public Land Map", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Version 1.0.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Legal disclaimers
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "This app is for informational purposes only. Property boundary data may not reflect current legal boundaries. Public land designations and regulations may change. Always verify information with official sources before entering land. Not affiliated with the agencies whose data it shows.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        SnackbarHost(hostState = snackbarHostState)
    }

    // Clear cache confirmation dialog
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear Cache") },
            text = { Text("This will remove cached parcel and public land data. Your waypoints and tracks will not be affected.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearCacheDialog = false
                    viewModel.clearCache()
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // About dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About Public Land Map") },
            text = {
                Column {
                    Text("Version 1.0.0")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("A prototype map of public land and property boundaries in Washington and Oregon. Offline maps are unverified and boundaries are approximate.")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Data Sources:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text("• PAD-US (Public Lands)")
                    Text("• County Assessor (Parcels)")
                    Text("• USFS (Roads & Recreation)")
                    Text("• PLSS (Survey Grid)")
                    Text("• USGS National Map, OpenStreetMap contributors and OpenTopoMap (basemaps)")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Disclaimer: Boundary data is approximate. Always verify with official sources.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
