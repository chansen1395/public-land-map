package com.connorhansen.publiclandmap.presentation.tools

import androidx.lifecycle.ViewModel
import com.connorhansen.publiclandmap.domain.usecase.MeasureDistanceUseCase
import com.connorhansen.publiclandmap.domain.usecase.MeasurePoint
import com.connorhansen.publiclandmap.domain.usecase.MeasureResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

enum class MeasureMode {
    LINE,
    POLYGON
}

data class MeasureUiState(
    val mode: MeasureMode = MeasureMode.LINE,
    val points: List<MeasurePoint> = emptyList(),
    val result: MeasureResult? = null
)

@HiltViewModel
class MeasureViewModel @Inject constructor(
    private val measureDistanceUseCase: MeasureDistanceUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(MeasureUiState())
    val state: StateFlow<MeasureUiState> = _state.asStateFlow()

    fun setMode(mode: MeasureMode) {
        _state.update { it.copy(mode = mode) }
        recalculate()
    }

    fun addPoint(lat: Double, lng: Double) {
        val newPoints = _state.value.points + MeasurePoint(lat, lng)
        _state.update { it.copy(points = newPoints) }
        recalculate()
    }

    fun undoLastPoint() {
        val points = _state.value.points
        if (points.isEmpty()) return
        val newPoints = points.dropLast(1)
        _state.update { it.copy(points = newPoints) }
        recalculate()
    }

    fun clearAll() {
        _state.update {
            MeasureUiState(mode = it.mode)
        }
    }

    fun formatDistance(meters: Double): String {
        return measureDistanceUseCase.formatDistance(meters)
    }

    fun formatArea(squareMeters: Double): String {
        return measureDistanceUseCase.formatArea(squareMeters)
    }

    private fun recalculate() {
        val current = _state.value
        val result = when (current.mode) {
            MeasureMode.LINE -> measureDistanceUseCase.measureLine(current.points)
            MeasureMode.POLYGON -> measureDistanceUseCase.measurePolygon(current.points)
        }
        _state.update { it.copy(result = result) }
    }
}
