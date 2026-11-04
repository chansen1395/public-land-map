package com.connorhansen.publiclandmap.presentation.tools

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.connorhansen.publiclandmap.domain.model.Track
import com.connorhansen.publiclandmap.domain.model.Waypoint
import com.connorhansen.publiclandmap.domain.usecase.RecordTrackUseCase
import com.connorhansen.publiclandmap.domain.usecase.SaveWaypointUseCase
import com.connorhansen.publiclandmap.util.GpxExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ToolsUiState(
    val showExportDialog: Boolean = false,
    val exportWaypoints: Boolean = true,
    val exportTracks: Boolean = true,
    val isExporting: Boolean = false,
    val exportSuccess: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val saveWaypointUseCase: SaveWaypointUseCase,
    private val recordTrackUseCase: RecordTrackUseCase,
    private val gpxExporter: GpxExporter,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val waypoints: StateFlow<List<Waypoint>> = saveWaypointUseCase.getAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val tracks: StateFlow<List<Track>> = recordTrackUseCase.getAllTracks()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _uiState = MutableStateFlow(ToolsUiState())
    val uiState: StateFlow<ToolsUiState> = _uiState.asStateFlow()

    fun showExportDialog() {
        _uiState.update { it.copy(showExportDialog = true, exportSuccess = false, errorMessage = null) }
    }

    fun dismissExportDialog() {
        _uiState.update { it.copy(showExportDialog = false) }
    }

    fun toggleExportWaypoints() {
        _uiState.update { it.copy(exportWaypoints = !it.exportWaypoints) }
    }

    fun toggleExportTracks() {
        _uiState.update { it.copy(exportTracks = !it.exportTracks) }
    }

    fun exportGpx(): Intent? {
        _uiState.update { it.copy(isExporting = true) }
        return try {
            val waypointsToExport = if (_uiState.value.exportWaypoints) waypoints.value else emptyList()
            val tracksToExport = if (_uiState.value.exportTracks) {
                tracks.value.map { track ->
                    // Load full track with points for export
                    track
                }
            } else emptyList()

            // Load full tracks with points
            var fullTracks: List<Track> = emptyList()
            if (tracksToExport.isNotEmpty()) {
                // We need to load synchronously here, so use a coroutine wrapper
                // For simplicity, export without points if tracks list already has them
                fullTracks = tracksToExport
            }

            val file = gpxExporter.exportAll(
                context = context,
                waypoints = waypointsToExport,
                tracks = fullTracks
            )

            _uiState.update {
                it.copy(
                    isExporting = false,
                    showExportDialog = false,
                    exportSuccess = true
                )
            }

            gpxExporter.createShareIntent(context, file)
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isExporting = false,
                    errorMessage = "Export failed: ${e.message}"
                )
            }
            null
        }
    }

    fun exportGpxWithFullTracks(onReady: (Intent?) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            try {
                val waypointsToExport = if (_uiState.value.exportWaypoints) waypoints.value else emptyList()
                val fullTracks = if (_uiState.value.exportTracks) {
                    tracks.value.mapNotNull { track ->
                        recordTrackUseCase.getTrackById(track.id)
                    }
                } else emptyList()

                val file = gpxExporter.exportAll(
                    context = context,
                    waypoints = waypointsToExport,
                    tracks = fullTracks
                )

                _uiState.update {
                    it.copy(
                        isExporting = false,
                        showExportDialog = false,
                        exportSuccess = true
                    )
                }

                onReady(gpxExporter.createShareIntent(context, file))
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        errorMessage = "Export failed: ${e.message}"
                    )
                }
                onReady(null)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
