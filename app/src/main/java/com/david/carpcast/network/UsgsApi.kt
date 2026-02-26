// File: src/main/java/com/david/carpcast/network/UsgsApi.kt
package com.david.carpcast.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface UsgsApi {
    @GET("nwis/iv/")
    suspend fun getInstantValues(
        @Query("format") format: String = "json",
        @Query("sites") sites: String
    ): Response<ResponseBody>
}