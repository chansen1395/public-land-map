package com.connorhansen.publiclandmap.data.remote.api

import com.connorhansen.publiclandmap.data.remote.dto.PaginatedResponse
import com.connorhansen.publiclandmap.data.remote.dto.ParcelDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ParcelApi {

    @GET("api/v1/parcels")
    suspend fun getParcels(
        @Query("bbox") bbox: String,
        @Query("state") state: String?,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 500
    ): PaginatedResponse<ParcelDto>

    @GET("api/v1/parcels/{id}")
    suspend fun getParcelById(@Path("id") id: String): ParcelDto

    @GET("api/v1/parcels/search")
    suspend fun searchParcels(
        @Query("q") query: String,
        @Query("state") state: String? = null,
        @Query("limit") limit: Int = 50
    ): PaginatedResponse<ParcelDto>
}
