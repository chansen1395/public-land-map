package com.connorhansen.publiclandmap.data.remote.api

import com.connorhansen.publiclandmap.data.remote.dto.MetadataResponse
import retrofit2.http.GET

interface MetadataApi {

    @GET("api/v1/metadata/last-updated")
    suspend fun getLastUpdated(): MetadataResponse
}
