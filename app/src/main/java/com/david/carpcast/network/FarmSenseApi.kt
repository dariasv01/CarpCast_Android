package com.david.carpcast.network

import retrofit2.http.GET
import retrofit2.http.Query

interface FarmSenseApi {
    // Example endpoint: /v1/moonphases/?d=DATE_EPOCH
    @GET("v1/moonphases/")
    suspend fun getMoonPhase(
        @Query("d") epochSeconds: Long
    ): List<FarmSenseMoonResponse>
}

// DTOs based on FarmSense responses - made tolerant to different field casings
data class FarmSenseMoonResponse(
    val date: String? = null,
    val phase: String? = null,
    val illumination: Double? = null
) {
}
