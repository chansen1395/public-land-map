package com.connorhansen.publiclandmap.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OfflineRegion(
    val id: String,
    val name: String,
    val state: String,
    val county: String? = null,
    @SerialName("min_lat") val minLat: Double,
    @SerialName("min_lng") val minLng: Double,
    @SerialName("max_lat") val maxLat: Double,
    @SerialName("max_lng") val maxLng: Double,
    @SerialName("min_zoom") val minZoom: Int = 8,
    @SerialName("max_zoom") val maxZoom: Int = 15,
    @SerialName("size_bytes") val sizeBytes: Long? = null,
    @SerialName("tile_file_path") val tileFilePath: String? = null
)
