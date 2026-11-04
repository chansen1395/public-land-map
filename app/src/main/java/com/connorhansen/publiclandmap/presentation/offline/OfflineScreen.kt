package com.connorhansen.publiclandmap.presentation.offline

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.connorhansen.publiclandmap.domain.model.OfflineRegion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val LOW_SPACE_THRESHOLD = 500L * 1024 * 1024 // 500 MB

@Composable
fun OfflineScreen(
    viewModel: OfflineViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val combined by viewModel.combinedState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val mergedState = uiState.copy(
        downloadedRegions = combined.downloadedRegions,
        downloadProgress = combined.downloadProgress,
        activeDownloads = combined.activeDownloads,
        totalStorageUsed = combined.totalStorageUsed
    )

    LaunchedEffect(mergedState.error) {
        mergedState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header
            Text(
                text = "Offline Maps",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )

            // Low space warning
            if (mergedState.deviceFreeSpace < LOW_SPACE_THRESHOLD) {
                LowSpaceWarning(freeSpace = mergedState.deviceFreeSpace)
            }

            // Tab Row
            TabRow(selectedTabIndex = mergedState.selectedTab) {
                Tab(
                    selected = mergedState.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("Download") }
                )
                Tab(
                    selected = mergedState.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text("My Maps") }
                )
            }

            when (mergedState.selectedTab) {
                0 -> DownloadTab(
                    state = mergedState,
                    onDownload = { viewModel.startDownload(it) },
                    onCancel = { viewModel.cancelDownload(it) },
                    onRetry = { viewModel.loadAvailableRegions() }
                )
                1 -> MyMapsTab(
                    state = mergedState,
                    onDelete = { viewModel.deleteRegion(it) },
                    onCheckUpdates = { viewModel.checkForUpdates() },
                    onRedownload = { viewModel.redownloadRegion(it) }
                )
            }
        }
    }
}

@Composable
private fun LowSpaceWarning(freeSpace: Long) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Low storage: ${formatFileSize(freeSpace)} free. Downloads may fail.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun DownloadTab(
    state: OfflineViewModel.UiState,
    onDownload: (OfflineRegion) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: () -> Unit
) {
    if (state.isLoading && state.availableRegions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Loading available regions...")
            }
        }
        return
    }

    if (state.availableRegions.isEmpty() && !state.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "No regions available",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Retry")
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(state.availableRegions, key = { it.id }) { region ->
            val isDownloading = state.activeDownloads.contains(region.id)
            val progress = state.downloadProgress[region.id]
            val existingRegion = state.downloadedRegions.find { it.id == region.id }
            val isComplete = existingRegion?.status == "complete"
            val isError = existingRegion?.status == "error"

            AvailableRegionCard(
                region = region,
                isDownloading = isDownloading,
                progress = progress,
                isComplete = isComplete,
                isError = isError,
                onDownload = { onDownload(region) },
                onCancel = { onCancel(region.id) }
            )
        }
    }
}

@Composable
private fun AvailableRegionCard(
    region: OfflineRegion,
    isDownloading: Boolean,
    progress: Float?,
    isComplete: Boolean,
    isError: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Map,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = region.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    region.sizeBytes?.let { size ->
                        Text(
                            text = "~${formatFileSize(size)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                when {
                    isComplete -> {
                        Icon(
                            Icons.Default.DownloadDone,
                            contentDescription = "Downloaded",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    isDownloading -> {
                        IconButton(onClick = onCancel) {
                            Icon(
                                Icons.Default.Cancel,
                                contentDescription = "Cancel download",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    isError -> {
                        Row {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(onClick = onDownload) {
                                Text("Retry")
                            }
                        }
                    }
                    else -> {
                        Button(onClick = onDownload) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download")
                        }
                    }
                }
            }

            // Progress bar
            if (isDownloading && progress != null) {
                Spacer(modifier = Modifier.height(8.dp))
                if (progress >= 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun MyMapsTab(
    state: OfflineViewModel.UiState,
    onDelete: (String) -> Unit,
    onCheckUpdates: () -> Unit,
    onRedownload: (OfflineRegion) -> Unit
) {
    val completedRegions = state.downloadedRegions.filter { it.status == "complete" }
    val downloadingRegions = state.downloadedRegions.filter { it.status == "downloading" }

    if (completedRegions.isEmpty() && downloadingRegions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Map,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No maps downloaded",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Download a region for offline use.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Update available banner
        if (state.updateAvailable) {
            item(key = "update_banner") {
                UpdateAvailableBanner()
            }
        }

        // Check for updates button
        item(key = "check_updates") {
            OutlinedButton(
                onClick = onCheckUpdates,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isCheckingUpdates
            ) {
                if (state.isCheckingUpdates) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Checking...")
                } else {
                    Icon(Icons.Default.Update, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Check for Updates")
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Downloaded regions
        items(completedRegions, key = { "complete_${it.id}" }) { region ->
            DownloadedRegionCard(
                region = region,
                hasUpdate = state.updateAvailable && state.serverLastUpdated > (region.downloadedAt ?: 0L),
                onDelete = { onDelete(region.id) },
                onRedownload = { onRedownload(region) }
            )
        }

        // In-progress downloads
        items(downloadingRegions, key = { "dl_${it.id}" }) { region ->
            val progress = state.downloadProgress[region.id]
            DownloadingRegionCard(region = region, progress = progress)
        }

        // Storage summary
        item(key = "storage_summary") {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            StorageSummary(totalUsed = state.totalStorageUsed)
        }
    }
}

@Composable
private fun UpdateAvailableBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Update,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Updated map data is available. Re-download regions to get the latest data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun DownloadedRegionCard(
    region: OfflineRegion,
    hasUpdate: Boolean,
    onDelete: () -> Unit,
    onRedownload: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Region") },
            text = { Text("Delete \"${region.name}\"? This will remove ${formatFileSize(region.sizeBytes ?: 0L)} of offline map data.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Map,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = region.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (hasUpdate) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "Update",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                    Row {
                        Text(
                            text = formatFileSize(region.sizeBytes ?: 0L),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        region.downloadedAt?.let { ts ->
                            Text(
                                text = " · ${formatDate(ts)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (hasUpdate) {
                    IconButton(onClick = onRedownload) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Update region",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete region",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadingRegionCard(region: OfflineRegion, progress: Float?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = region.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (progress != null && progress >= 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

@Composable
private fun StorageSummary(totalUsed: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Storage,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Total offline storage: ${formatFileSize(totalUsed)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> String.format("%.0f MB", bytes / 1_048_576.0)
        bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
        bytes == 0L -> "0 B"
        else -> "$bytes B"
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
