package com.connorhansen.publiclandmap.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PublicLand(
    val id: String,
    @SerialName("unit_name") val unitName: String,
    @SerialName("managing_agency") val managingAgency: String,
    @SerialName("agency_category") val agencyCategory: String? = null,
    @SerialName("designation_type") val designationType: String,
    @SerialName("public_access") val publicAccess: String? = null,
    val state: String,
    @SerialName("gis_acres") val gisAcres: Float? = null,
    @SerialName("centroid_lat") val centroidLat: Double? = null,
    @SerialName("centroid_lng") val centroidLng: Double? = null,
    val geometry: JsonElement? = null,
    @SerialName("last_updated") val lastUpdated: Long? = null
)
