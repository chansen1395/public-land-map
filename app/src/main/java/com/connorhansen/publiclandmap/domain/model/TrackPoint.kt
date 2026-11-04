package com.connorhansen.publiclandmap.domain.model

data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double?,
    val timestamp: Long,
    val accuracy: Float?,
    val sequence: Int
)
