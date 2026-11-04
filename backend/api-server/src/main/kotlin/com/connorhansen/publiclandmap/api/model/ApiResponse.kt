package com.connorhansen.publiclandmap.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PaginatedResponse<T>(
    val data: List<T>,
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
    val count: Int
)

@Serializable
data class ErrorResponse(
    val error: String
)
