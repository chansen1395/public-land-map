package com.connorhansen.publiclandmap.presentation.tools

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.connorhansen.publiclandmap.domain.model.Waypoint
import com.connorhansen.publiclandmap.domain.usecase.SaveWaypointUseCase
import com.connorhansen.publiclandmap.util.DistanceCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class CompassUiState(
    val selectedWaypoint: Waypoint? = null,
    val currentLat: Double? = null,
    val currentLng: Double? = null,
    val bearingDegrees: Double? = null,
    val distanceMeters: Double? = null
)

@HiltViewModel
class CompassViewModel @Inject constructor(
    private val saveWaypointUseCase: SaveWaypointUseCase,
    private val distanceCalculator: DistanceCalculator
) : ViewModel() {

    val waypoints: StateFlow<List<Waypoint>> = saveWaypointUseCase.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val _state = MutableStateFlow(CompassUiState())
    val state: StateFlow<CompassUiState> = _state.asStateFlow()

    fun selectWaypoint(waypoint: Waypoint) {
        _state.update { it.copy(selectedWaypoint = waypoint) }
        recalculate()
    }

    fun updateLocation(location: Location) {
        _state.update {
            it.copy(
                currentLat = location.latitude,
                currentLng = location.longitude
            )
        }
        recalculate()
    }

    fun updateLocation(lat: Double, lng: Double) {
        _state.update {
            it.copy(currentLat = lat, currentLng = lng)
        }
        recalculate()
    }

    fun formatBearing(degrees: Double): String {
        val cardinal = when {
            degrees < 22.5 || degrees >= 337.5 -> "N"
            degrees < 67.5 -> "NE"
            degrees < 112.5 -> "E"
            degrees < 157.5 -> "SE"
            degrees < 202.5 -> "S"
            degrees < 247.5 -> "SW"
            degrees < 292.5 -> "W"
            else -> "NW"
        }
        return "%.0f° %s".format(degrees, cardinal)
    }

    fun formatDistance(meters: Double): String {
        val miles = distanceCalculator.metersToMiles(meters)
        return if (miles < 0.1) {
            "%.0f ft".format(distanceCalculator.metersToFeet(meters))
        } else {
            "%.2f mi".format(miles)
        }
    }

    private fun recalculate() {
        val current = _state.value
        val waypoint = current.selectedWaypoint ?: return
        val lat = current.currentLat ?: return
        val lng = current.currentLng ?: return

        val bearing = distanceCalculator.bearing(lat, lng, waypoint.latitude, waypoint.longitude)
        val distance = distanceCalculator.haversineMeters(lat, lng, waypoint.latitude, waypoint.longitude)

        _state.update {
            it.copy(
                bearingDegrees = bearing,
                distanceMeters = distance
            )
        }
    }
}
