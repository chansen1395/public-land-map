package com.connorhansen.publiclandmap.domain.model

data class Track(
    val id: String,
    val name: String,
    val description: String?,
    val distanceMeters: Double?,
    val elevationGain: Double?,
    val durationMs: Long?,
    val createdAt: Long,
    val points: List<TrackPoint> = emptyList()
)
