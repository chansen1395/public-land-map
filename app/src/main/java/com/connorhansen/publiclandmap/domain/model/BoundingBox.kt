package com.connorhansen.publiclandmap.domain.model

data class BoundingBox(
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double
) {
    fun toBboxString(): String = "$minLng,$minLat,$maxLng,$maxLat"
}
