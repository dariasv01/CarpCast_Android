// File: src/main/java/com/david/carpcast/network/OpenMeteoApi.kt
package com.david.carpcast.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {
    @GET("https://archive-api.open-meteo.com/v1/archive")
    suspend fun getHistoricalWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("hourly") hourly: String = "weather_code,temperature_2m,relative_humidity_2m,dew_point_2m,precipitation,cloud_cover,cloud_cover_low,cloud_cover_mid,cloud_cover_high,wind_speed_10m,wind_direction_10m,wind_gusts_10m,pressure_msl,surface_pressure,shortwave_radiation,is_day",
        @Query("daily") daily: String = "sunrise,sunset",
        @Query("timezone") timezone: String = "auto",
        @Query("models") model: String = "era5"
    ): Response<ResponseBody>

    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String = "weather_code,temperature_2m,relative_humidity_2m,dew_point_2m,precipitation,precipitation_probability,cloud_cover,cloud_cover_low,cloud_cover_mid,cloud_cover_high,wind_speed_10m,wind_direction_10m,wind_gusts_10m,pressure_msl,surface_pressure,shortwave_radiation,is_day,visibility,uv_index",
        @Query("current_weather") currentWeather: Boolean = true,
        @Query("daily") daily: String = "sunrise,sunset",
        @Query("forecast_days") forecastDays: Int = 7,
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null,
        @Query("timezone") timezone: String = "auto"
    ): Response<ResponseBody>
}
