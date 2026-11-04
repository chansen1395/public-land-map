package com.connorhansen.publiclandmap.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Regulation(
    val id: Int,
    @SerialName("area_id") val areaId: String? = null,
    @SerialName("area_name") val areaName: String? = null,
    @SerialName("regulation_type") val regulationType: String,
    @SerialName("managing_agency") val managingAgency: String? = null,
    val state: String? = null,
    val title: String,
    val description: String? = null,
    @SerialName("restriction_level") val restrictionLevel: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val geometry: JsonElement? = null,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("last_updated") val lastUpdated: Long? = null
)
