package com.connorhansen.publiclandmap.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PublicLandDto(
    val id: String,
    @Json(name = "unit_name") val unitName: String,
    @Json(name = "managing_agency") val managingAgency: String,
    @Json(name = "agency_category") val agencyCategory: String?,
    @Json(name = "designation_type") val designationType: String,
    @Json(name = "public_access") val publicAccess: String?,
    val state: String,
    @Json(name = "gis_acres") val gisAcres: Double?,
    @Json(name = "centroid_lat") val centroidLat: Double?,
    @Json(name = "centroid_lng") val centroidLng: Double?,
    val geometry: String?
)
