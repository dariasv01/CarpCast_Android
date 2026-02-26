package com.david.carpcast.network

import com.google.gson.JsonElement
import retrofit2.http.GET
import retrofit2.http.Query

// Nota: USNO API tiene varios endpoints; aquí se modela una llamada "oneday" aproximada.
interface USNOApi {
    // Example: /rstt/oneday?date=01/27/2026&coords=40.0,-74.0
    // Usamos JsonElement para mayor flexibilidad porque el formato real puede variar.
    @GET("rstt/oneday")
    suspend fun oneDay(
        @Query("date") date: String,
        @Query("coords") coords: String,
        @Query("tz") tz: String = "1"
    ): JsonElement
}
