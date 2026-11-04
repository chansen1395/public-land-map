package com.connorhansen.publiclandmap.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RegulationDto(
    val id: Long?,
    @Json(name = "area_id") val areaId: String?,
    @Json(name = "area_name") val areaName: String?,
    @Json(name = "regulation_type") val regulationType: String,
    @Json(name = "managing_agency") val managingAgency: String?,
    val state: String?,
    val title: String,
    val description: String?,
    @Json(name = "restriction_level") val restrictionLevel: String?,
    @Json(name = "start_date") val startDate: String?,
    @Json(name = "end_date") val endDate: String?,
    val geometry: String?,
    @Json(name = "source_url") val sourceUrl: String?
)
