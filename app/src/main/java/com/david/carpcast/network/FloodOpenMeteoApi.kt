package com.david.carpcast.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface FloodOpenMeteoApi {
    // Endpoint example: v1/flood?latitude=...&longitude=...&daily=river_discharge&forecast_days=30&cell_selection=land
    @GET("flood")
    suspend fun getFlood(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("daily") daily: String = "river_discharge",
        @Query("forecast_days") forecastDays: Int = 30,
        @Query("cell_selection") cellSelection: String = "land"
    ): Response<ResponseBody>
}
