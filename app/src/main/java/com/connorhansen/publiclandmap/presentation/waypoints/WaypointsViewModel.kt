package com.connorhansen.publiclandmap.presentation.waypoints

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.connorhansen.publiclandmap.domain.model.Waypoint
import com.connorhansen.publiclandmap.domain.usecase.SaveWaypointUseCase
import com.connorhansen.publiclandmap.util.CoordinateFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WaypointEditState(
    val id: String? = null,
    val name: String = "",
    val description: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val elevation: Double? = null,
    val icon: String = "pin",
    val color: String = "#FF0000",
    val isEditMode: Boolean = false,
    val nameError: String? = null,
    val isSaving: Boolean = false
)

data class WaypointsUiState(
    val isLoading: Boolean = true,
    val deleteConfirmWaypoint: Waypoint? = null
)

@HiltViewModel
class WaypointsViewModel @Inject constructor(
    private val saveWaypointUseCase: SaveWaypointUseCase,
    val coordinateFormatter: CoordinateFormatter
) : ViewModel() {

    val waypoints: StateFlow<List<Waypoint>> = saveWaypointUseCase.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val _uiState = MutableStateFlow(WaypointsUiState())
    val uiState: StateFlow<WaypointsUiState> = _uiState.asStateFlow()

    private val _editState = MutableStateFlow(WaypointEditState())
    val editState: StateFlow<WaypointEditState> = _editState.asStateFlow()

    fun loadForEdit(waypointId: String) {
        viewModelScope.launch {
            val waypoint = saveWaypointUseCase.getById(waypointId) ?: return@launch
            _editState.value = WaypointEditState(
                id = waypoint.id,
                name = waypoint.name,
                description = waypoint.description ?: "",
                latitude = waypoint.latitude,
                longitude = waypoint.longitude,
                elevation = waypoint.elevation,
                icon = waypoint.icon,
                color = waypoint.color,
                isEditMode = true
            )
        }
    }

    fun prepareNewWaypoint(latitude: Double, longitude: Double, elevation: Double? = null) {
        _editState.value = WaypointEditState(
            latitude = latitude,
            longitude = longitude,
            elevation = elevation,
            isEditMode = false
        )
    }

    fun onNameChange(name: String) {
        _editState.update { it.copy(name = name, nameError = null) }
    }

    fun onDescriptionChange(description: String) {
        _editState.update { it.copy(description = description) }
    }

    fun onIconChange(icon: String) {
        _editState.update { it.copy(icon = icon) }
    }

    fun onColorChange(color: String) {
        _editState.update { it.copy(color = color) }
    }

    fun saveWaypoint(onSuccess: () -> Unit) {
        val state = _editState.value
        if (state.name.isBlank()) {
            _editState.update { it.copy(nameError = "Name is required") }
            return
        }

        _editState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            if (state.isEditMode && state.id != null) {
                val waypoint = saveWaypointUseCase.getById(state.id)
                if (waypoint != null) {
                    saveWaypointUseCase(
                        waypoint.copy(
                            name = state.name.trim(),
                            description = state.description.ifBlank { null },
                            icon = state.icon,
                            color = state.color
                        )
                    )
                }
            } else {
                saveWaypointUseCase.create(
                    name = state.name.trim(),
                    latitude = state.latitude,
                    longitude = state.longitude,
                    description = state.description.ifBlank { null },
                    elevation = state.elevation,
                    icon = state.icon,
                    color = state.color
                )
            }
            _editState.update { it.copy(isSaving = false) }
            onSuccess()
        }
    }

    fun requestDelete(waypoint: Waypoint) {
        _uiState.update { it.copy(deleteConfirmWaypoint = waypoint) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(deleteConfirmWaypoint = null) }
    }

    fun confirmDelete() {
        val waypoint = _uiState.value.deleteConfirmWaypoint ?: return
        viewModelScope.launch {
            saveWaypointUseCase.delete(waypoint.id)
            _uiState.update { it.copy(deleteConfirmWaypoint = null) }
        }
    }

    fun deleteEditingWaypoint(onSuccess: () -> Unit) {
        val id = _editState.value.id ?: return
        viewModelScope.launch {
            saveWaypointUseCase.delete(id)
            onSuccess()
        }
    }

    fun resetEditState() {
        _editState.value = WaypointEditState()
    }

    init {
        viewModelScope.launch {
            waypoints.collect {
                _uiState.update { state -> state.copy(isLoading = false) }
            }
        }
    }
}
