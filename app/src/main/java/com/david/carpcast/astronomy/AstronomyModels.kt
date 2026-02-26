package com.david.carpcast.astronomy

import com.david.carpcast.astronomy.AstronomyResult.Source

sealed class AstronomyResult {
    data class Success(
        val sunrise: String?, // ISO-8601 UTC
        val sunset: String?,  // ISO-8601 UTC
        val moonPhase: String?,
        val moonIllumination: Double?, // percentage 0.0 - 100.0
        val source: Source
    ) : AstronomyResult()

    data class Failure(val error: AstronomyError) : AstronomyResult()

    enum class Source {
        USNO, SUNRISE_SUNSET, FARMSENSE, COMBINED
    }
}

sealed class AstronomyError {
    object Network : AstronomyError()
    object Timeout : AstronomyError()
    data class Http(val code: Int) : AstronomyError()
    data class Parse(val cause: String) : AstronomyError()
    data class Aggregate(val reasons: List<AstronomyError>) : AstronomyError()
}
