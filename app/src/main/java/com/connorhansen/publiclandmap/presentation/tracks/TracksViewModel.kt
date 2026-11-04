package com.connorhansen.publiclandmap.presentation.tracks

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.connorhansen.publiclandmap.domain.model.Track
import com.connorhansen.publiclandmap.domain.usecase.RecordTrackUseCase
import com.connorhansen.publiclandmap.service.TrackingService
import com.connorhansen.publiclandmap.util.DistanceCalculator
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
import java.io.File
import javax.inject.Inject

data class TracksUiState(
    val isLoading: Boolean = true,
    val selectedTrack: Track? = null,
    val isLoadingDetail: Boolean = false,
    val deleteConfirmTrack: Track? = null,
    val exportedFile: File? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class TracksViewModel @Inject constructor(
    private val recordTrackUseCase: RecordTrackUseCase,
    val distanceCalculator: DistanceCalculator,
    private val gpxExporter: GpxExporter,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val tracks: StateFlow<List<Track>> = recordTrackUseCase.getAllTracks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val trackingState: StateFlow<TrackingService.TrackingState> = TrackingService.trackingState

    private val _uiState = MutableStateFlow(TracksUiState())
    val uiState: StateFlow<TracksUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            tracks.collect {
                _uiState.update { state -> state.copy(isLoading = false) }
            }
        }
    }

    fun loadTrackDetail(trackId: String) {
        _uiState.update { it.copy(isLoadingDetail = true) }
        viewModelScope.launch {
            val track = recordTrackUseCase.getTrackById(trackId)
            _uiState.update {
                it.copy(selectedTrack = track, isLoadingDetail = false)
            }
        }
    }

    fun startRecording(name: String) {
        viewModelScope.launch {
            val track = recordTrackUseCase.startRecording(name)
            val intent = Intent(context, TrackingService::class.java).apply {
                action = TrackingService.ACTION_START
                putExtra(TrackingService.EXTRA_TRACK_ID, track.id)
                putExtra(TrackingService.EXTRA_TRACK_NAME, track.name)
            }
            context.startForegroundService(intent)
        }
    }

    fun stopRecording() {
        val currentTrackId = trackingState.value.activeTrackId ?: return
        viewModelScope.launch {
            val intent = Intent(context, TrackingService::class.java).apply {
                action = TrackingService.ACTION_STOP
            }
            context.startService(intent)
            recordTrackUseCase.stopRecording(currentTrackId)
        }
    }

    fun requestDelete(track: Track) {
        _uiState.update { it.copy(deleteConfirmTrack = track) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(deleteConfirmTrack = null) }
    }

    fun confirmDelete() {
        val track = _uiState.value.deleteConfirmTrack ?: return
        viewModelScope.launch {
            recordTrackUseCase.deleteTrack(track.id)
            _uiState.update {
                it.copy(
                    deleteConfirmTrack = null,
                    selectedTrack = if (it.selectedTrack?.id == track.id) null else it.selectedTrack
                )
            }
        }
    }

    fun deleteTrackById(trackId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            recordTrackUseCase.deleteTrack(trackId)
            _uiState.update { it.copy(selectedTrack = null) }
            onSuccess()
        }
    }

    fun exportTrackGpx(track: Track): Intent? {
        return try {
            val file = gpxExporter.exportTrack(context, track)
            _uiState.update { it.copy(exportedFile = file) }
            gpxExporter.createShareIntent(context, file)
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Export failed: ${e.message}") }
            null
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearSelectedTrack() {
        _uiState.update { it.copy(selectedTrack = null) }
    }

    fun formatDistance(meters: Double?): String {
        if (meters == null || meters == 0.0) return "--"
        val miles = distanceCalculator.metersToMiles(meters)
        return if (miles < 0.1) {
            "%.0f ft".format(distanceCalculator.metersToFeet(meters))
        } else {
            "%.2f mi".format(miles)
        }
    }

    fun formatDuration(ms: Long?): String {
        if (ms == null || ms == 0L) return "--"
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "%dh %02dm".format(hours, minutes)
            minutes > 0 -> "%dm %02ds".format(minutes, seconds)
            else -> "%ds".format(seconds)
        }
    }

    fun formatElevation(meters: Double?): String {
        if (meters == null || meters == 0.0) return "--"
        return "%.0f ft".format(distanceCalculator.metersToFeet(meters))
    }

    fun formatSpeed(distanceMeters: Double?, durationMs: Long?): String {
        if (distanceMeters == null || durationMs == null || durationMs == 0L) return "--"
        val hours = durationMs / 3_600_000.0
        if (hours == 0.0) return "--"
        val miles = distanceCalculator.metersToMiles(distanceMeters)
        return "%.1f mph".format(miles / hours)
    }
}
