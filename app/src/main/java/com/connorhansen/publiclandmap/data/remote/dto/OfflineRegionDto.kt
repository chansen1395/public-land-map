package com.connorhansen.publiclandmap.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OfflineRegionDto(
    val id: String,
    val name: String,
    @Json(name = "min_lat") val minLat: Double,
    @Json(name = "min_lng") val minLng: Double,
    @Json(name = "max_lat") val maxLat: Double,
    @Json(name = "max_lng") val maxLng: Double,
    @Json(name = "min_zoom") val minZoom: Int,
    @Json(name = "max_zoom") val maxZoom: Int,
    @Json(name = "size_bytes") val sizeBytes: Long?
)
