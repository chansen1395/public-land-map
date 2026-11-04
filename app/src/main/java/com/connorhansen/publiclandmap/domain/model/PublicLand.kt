package com.connorhansen.publiclandmap.domain.model

data class PublicLand(
    val id: String,
    val unitName: String,
    val managingAgency: String,
    val agencyCategory: String?,
    val designationType: String,
    val publicAccess: String?,
    val state: String,
    val gisAcres: Double?,
    val centroidLat: Double?,
    val centroidLng: Double?,
    val geojson: String?
)
