package com.connorhansen.publiclandmap.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MetadataEntryDto(
    val key: String,
    val value: String?,
    @Json(name = "updated_at") val updatedAt: String?
)

@JsonClass(generateAdapter = true)
data class MetadataResponse(
    @Json(name = "last_updated") val lastUpdated: String?,
    val entries: List<MetadataEntryDto> = emptyList()
)
