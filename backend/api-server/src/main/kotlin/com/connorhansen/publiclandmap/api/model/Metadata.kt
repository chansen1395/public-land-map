package com.connorhansen.publiclandmap.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MetadataEntry(
    val key: String,
    val value: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class LastUpdatedResponse(
    @SerialName("last_updated") val lastUpdated: String? = null,
    val entries: List<MetadataEntry> = emptyList()
)
