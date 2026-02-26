package com.david.carpcast.network

import retrofit2.http.GET
import retrofit2.http.Query

interface SunriseSunsetApi {
    @GET("json")
    suspend fun getForDate(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("date") date: String, // YYYY-MM-DD
        @Query("formatted") formatted: Int = 0 // 0 => ISO 8601 / UTC
    ): SunriseSunsetResponse
}

// DTOs matching https://sunrise-sunset.org/api
data class SunriseSunsetResponse(
    val results: SunriseSunsetResults?,
    val status: String?
)

data class SunriseSunsetResults(
    val sunrise: String?,
    val sunset: String?,
    val solar_noon: String?,
    val day_length: Long?
)
