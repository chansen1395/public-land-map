package com.connorhansen.publiclandmap.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PaginatedResponse<T>(
    val data: List<T>,
    @Json(name = "next_cursor") val nextCursor: String?,
    @Json(name = "has_more") val hasMore: Boolean,
    val count: Int = 0
)
