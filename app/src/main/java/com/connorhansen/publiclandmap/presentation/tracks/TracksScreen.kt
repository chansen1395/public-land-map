package com.connorhansen.publiclandmap.presentation.tracks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.connorhansen.publiclandmap.domain.model.Track
import com.connorhansen.publiclandmap.service.TrackingService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracksScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (trackId: String) -> Unit,
    viewModel: TracksViewModel
) {
    val tracks by viewModel.tracks.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val trackingState by viewModel.trackingState.collectAsState()
    var showNameDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tracks") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (trackingState.isRecording) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.stopRecording() },
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    icon = { Icon(Icons.Filled.Stop, contentDescription = null) },
                    text = {
                        Column {
                            Text("Stop Recording")
                            Text(
                                text = "${viewModel.formatDistance(trackingState.distanceMeters)} · ${viewModel.formatDuration(trackingState.elapsedMs)}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                )
            } else {
                ExtendedFloatingActionButton(
                    onClick = { showNameDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    icon = { Icon(Icons.Filled.FiberManualRecord, contentDescription = null) },
                    text = { Text("Record") }
                )
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (tracks.isEmpty() && !trackingState.isRecording) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Route,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No recorded tracks",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap Record to start your first track",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (trackingState.isRecording) {
                    item(key = "recording") {
                        RecordingCard(
                            trackingState = trackingState,
                            formatDistance = viewModel::formatDistance,
                            formatDuration = viewModel::formatDuration
                        )
                    }
                }

                items(tracks, key = { it.id }) { track ->
                    TrackCard(
                        track = track,
                        formatDistance = viewModel::formatDistance,
                        formatDuration = viewModel::formatDuration,
                        onClick = { onNavigateToDetail(track.id) }
                    )
                }
            }
        }
    }

    // Name dialog for new recording
    if (showNameDialog) {
        var trackName by remember { mutableStateOf("") }
        val dateStr = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US).format(Date())
        val defaultName = "Track $dateStr"

        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Start Recording") },
            text = {
                OutlinedTextField(
                    value = trackName,
                    onValueChange = { trackName = it },
                    label = { Text("Track Name") },
                    placeholder = { Text(defaultName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNameDialog = false
                        viewModel.startRecording(trackName.ifBlank { defaultName })
                    }
                ) {
                    Text("Start")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete confirmation
    uiState.deleteConfirmTrack?.let { track ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text("Delete Track") },
            text = { Text("Delete \"${track.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun RecordingCard(
    trackingState: TrackingService.TrackingState,
    formatDistance: (Double?) -> String,
    formatDuration: (Long?) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.FiberManualRecord,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Recording...",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "${formatDistance(trackingState.distanceMeters)} · ${formatDuration(trackingState.elapsedMs)} · ${trackingState.pointCount} pts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun TrackCard(
    track: Track,
    formatDistance: (Double?) -> String,
    formatDuration: (Long?) -> String,
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
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Route,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = formatDistance(track.distanceMeters),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDuration(track.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = SimpleDateFormat("MMM d", Locale.US).format(Date(track.createdAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
