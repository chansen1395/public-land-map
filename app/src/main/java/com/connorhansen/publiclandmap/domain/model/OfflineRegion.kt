package com.connorhansen.publiclandmap.domain.model

data class OfflineRegion(
    val id: String,
    val name: String,
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double,
    val minZoom: Int = 8,
    val maxZoom: Int = 15,
    val sizeBytes: Long?,
    val downloadedAt: Long?,
    val status: String = "pending"
)
