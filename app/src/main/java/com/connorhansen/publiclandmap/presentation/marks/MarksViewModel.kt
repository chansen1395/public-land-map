package com.connorhansen.publiclandmap.presentation.marks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.connorhansen.publiclandmap.domain.usecase.RecordTrackUseCase
import com.connorhansen.publiclandmap.domain.usecase.SaveWaypointUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MarksViewModel @Inject constructor(
    saveWaypointUseCase: SaveWaypointUseCase,
    recordTrackUseCase: RecordTrackUseCase
) : ViewModel() {

    val waypointCount: StateFlow<Int> = saveWaypointUseCase.getAll()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val trackCount: StateFlow<Int> = recordTrackUseCase.getAllTracks()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)
}
