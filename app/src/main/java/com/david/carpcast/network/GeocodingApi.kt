// File: src/main/java/com/david/carpcast/network/GeocodingApi.kt
package com.david.carpcast.network

import retrofit2.http.GET
import retrofit2.http.Query

data class NominatimResult(
    val place_id: Long,
    val display_name: String,
    val lat: String,
    val lon: String,
    val boundingbox: List<String>?
)

interface GeocodingApi {
    @GET("search")
    suspend fun search(
        @Query("q") q: String,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressdetails: Int = 1,
        @Query("limit") limit: Int = 5
    ): List<NominatimResult>

    @GET("reverse")
    suspend fun reverse(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressdetails: Int = 1
    ): NominatimResult
}