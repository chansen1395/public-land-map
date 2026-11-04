package com.connorhansen.publiclandmap.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Parcel(
    val id: String,
    @SerialName("parcel_number") val parcelNumber: String,
    @SerialName("owner_name") val ownerName: String? = null,
    val county: String,
    val state: String,
    val acreage: Float? = null,
    @SerialName("land_use_code") val landUseCode: String? = null,
    @SerialName("land_use_desc") val landUseDesc: String? = null,
    @SerialName("centroid_lat") val centroidLat: Double,
    @SerialName("centroid_lng") val centroidLng: Double,
    val geometry: JsonElement? = null,
    @SerialName("last_updated") val lastUpdated: Long? = null
)
