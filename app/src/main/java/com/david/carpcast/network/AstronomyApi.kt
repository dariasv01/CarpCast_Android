// File: src/main/java/com/david/carpcast/network/AstronomyApi.kt
package com.david.carpcast.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface AstronomyApi {
    @GET("json")
    suspend fun getSunriseSunset(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("date") date: String = "today",
        @Query("formatted") formatted: Int = 0
    ): Response<ResponseBody>
}