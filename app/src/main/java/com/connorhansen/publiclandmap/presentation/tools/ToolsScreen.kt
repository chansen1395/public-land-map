package com.connorhansen.publiclandmap.presentation.tools

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ToolsScreen(
    onNavigateToMeasure: () -> Unit,
    onNavigateToCompass: () -> Unit,
    viewModel: ToolsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Tools",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        ToolCard(
            icon = Icons.Filled.Straighten,
            title = "Measure Distance",
            description = "Measure distances and areas on the map",
            onClick = onNavigateToMeasure
        )

        Spacer(modifier = Modifier.height(12.dp))

        ToolCard(
            icon = Icons.Filled.Navigation,
            title = "Compass",
            description = "Navigate to a saved waypoint",
            onClick = onNavigateToCompass
        )

        Spacer(modifier = Modifier.height(12.dp))

        ToolCard(
            icon = Icons.Filled.FileDownload,
            title = "GPX Export",
            description = "Export waypoints and tracks to GPX file",
            onClick = { viewModel.showExportDialog() }
        )
    }

    // Export dialog
    if (uiState.showExportDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissExportDialog() },
            title = { Text("Export GPX") },
            text = {
                Column {
                    Text(
                        text = "Choose what to export:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = uiState.exportWaypoints,
                            onCheckedChange = { viewModel.toggleExportWaypoints() }
                        )
                        Text("Waypoints")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = uiState.exportTracks,
                            onCheckedChange = { viewModel.toggleExportTracks() }
                        )
                        Text("Tracks")
                    }
                    if (uiState.isExporting) {
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Exporting...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    uiState.errorMessage?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.exportGpxWithFullTracks { intent ->
                            if (intent != null) {
                                context.startActivity(
                                    Intent.createChooser(intent, "Export GPX")
                                )
                            }
                        }
                    },
                    enabled = !uiState.isExporting &&
                        (uiState.exportWaypoints || uiState.exportTracks)
                ) {
                    Text("Export & Share")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissExportDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ToolCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
