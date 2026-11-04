package com.connorhansen.publiclandmap.domain.model

data class Parcel(
    val id: String,
    val parcelNumber: String,
    val ownerName: String?,
    val county: String,
    val state: String,
    val acreage: Double?,
    val landUseCode: String?,
    val landUseDesc: String?,
    val centroidLat: Double,
    val centroidLng: Double,
    val geojson: String?
)
