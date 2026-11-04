package com.connorhansen.publiclandmap.data.remote.api

import com.connorhansen.publiclandmap.data.remote.dto.PaginatedResponse
import com.connorhansen.publiclandmap.data.remote.dto.RegulationDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface RegulationApi {

    @GET("api/v1/regulations")
    suspend fun getByBbox(
        @Query("bbox") bbox: String,
        @Query("type") type: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 200
    ): PaginatedResponse<RegulationDto>

    @GET("api/v1/public-lands/{id}/regulations")
    suspend fun getForArea(@Path("id") areaId: String): List<RegulationDto>
}
