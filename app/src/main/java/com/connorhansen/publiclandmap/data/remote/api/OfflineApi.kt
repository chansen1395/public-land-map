package com.connorhansen.publiclandmap.data.remote.api

import com.connorhansen.publiclandmap.data.remote.dto.OfflineRegionDto
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Streaming

interface OfflineApi {

    @GET("api/v1/offline/regions")
    suspend fun getRegions(): List<OfflineRegionDto>

    @Streaming
    @GET("api/v1/offline/regions/{id}/download")
    suspend fun downloadRegion(@Path("id") id: String): ResponseBody

    @GET("api/v1/offline/regions/{id}/metadata")
    suspend fun getRegionMetadata(@Path("id") id: String): OfflineRegionDto
}
