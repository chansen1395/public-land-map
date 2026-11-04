package com.connorhansen.publiclandmap.data.remote.api

import com.connorhansen.publiclandmap.data.remote.dto.PaginatedResponse
import com.connorhansen.publiclandmap.data.remote.dto.PublicLandDto
import com.connorhansen.publiclandmap.data.remote.dto.RegulationDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface PublicLandApi {

    @GET("api/v1/public-lands")
    suspend fun getPublicLands(
        @Query("bbox") bbox: String,
        @Query("state") state: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 500
    ): PaginatedResponse<PublicLandDto>

    @GET("api/v1/public-lands/{id}")
    suspend fun getById(@Path("id") id: String): PublicLandDto

    @GET("api/v1/public-lands/{id}/regulations")
    suspend fun getRegulationsForLand(@Path("id") id: String): List<RegulationDto>

    @GET("api/v1/public-lands/search")
    suspend fun searchPublicLands(
        @Query("q") query: String,
        @Query("state") state: String? = null,
        @Query("limit") limit: Int = 50
    ): PaginatedResponse<PublicLandDto>
}
