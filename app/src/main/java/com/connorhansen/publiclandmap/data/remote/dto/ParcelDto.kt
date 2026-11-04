package com.connorhansen.publiclandmap.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ParcelDto(
    val id: String,
    @Json(name = "parcel_number") val parcelNumber: String,
    @Json(name = "owner_name") val ownerName: String?,
    val county: String,
    val state: String,
    val acreage: Double?,
    @Json(name = "land_use_code") val landUseCode: String?,
    @Json(name = "land_use_desc") val landUseDesc: String?,
    @Json(name = "centroid_lat") val centroidLat: Double,
    @Json(name = "centroid_lng") val centroidLng: Double,
    val geometry: String?
)
