// File: src/main/java/com/david/carpcast/network/OpenMeteoApi.kt
package com.david.carpcast.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String = "temperature_2m,relative_humidity_2m,dewpoint_2m,precipitation,precipitation_probability,cloud_cover,cloud_cover_low,cloud_cover_mid,cloud_cover_high,wind_speed_10m,wind_direction_10m,wind_gusts_10m,surface_pressure,shortwave_radiation,is_day,visibility,uv_index",
        @Query("forecast_days") forecastDays: Int = 7,
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null,
        @Query("timezone") timezone: String = "auto"
    ): Response<ResponseBody>
}