package com.david.carpcast.scoring

// Tipos compartidos para scoring

enum class FishingMode { carpfishing, predator }

enum class FishSpecies { general, carp, barbel, bass, pike, catfish }

data class WeatherData(
    val time: String,                       // ISO8601
    val temperature: Double,                // maps from "temperature_2m"
    val humidity: Double,                   // % (maps from "relative_humidity_2m")
    val pressure: Double,                   // hPa (original primary pressure)
    val surfacePressure: Double? = null,    // hPa (maps from "surface_pressure")
    val windSpeed: Double,                  // km/h (maps from "wind_speed_10m")
    val windDirection: Double? = null,      // degrees (maps from "wind_direction_10m")
    val gustSpeed: Double? = null,          // km/h (maps from "wind_gusts_10m")
    val precipitation: Double,              // mm
    val precipitationProbability: Double? = null, // %
    val cloudCover: Double,                 // %
    val cloudCoverLow: Double? = null,      // %
    val cloudCoverMid: Double? = null,      // %
    val cloudCoverHigh: Double? = null,     // %
    val shortwaveRadiation: Double? = null, // W/m2
    val uvIndex: Double? = null,
    val isDay: Boolean? = null,
    val dewPoint: Double? = null,           // °C
    val visibility: Double? = null          // meters (maps from "visibility")
)

data class AstroData(
    val sunrise: String,                    // ISO8601
    val sunset: String,                     // ISO8601
    val moonIllumination: Double? = null,   // 0..100
    val moonPhase: Double                   // 0..1
)

data class HydroData(
    val waterLevel: Double? = null,
    val waterFlow: Double? = null,
    val waterTemp: Double? = null
)

data class MarineData(
    val waveHeight: Double
)

data class DerivedWeatherFeatures(
    val deltaPressure1h: Double? = null,
    val deltaPressure3hAvg: Double? = null,
    val deltaTemp1h: Double? = null,
    val rainPrev6h: Double? = null,
    val rainSum24h: Double? = null,
    val windStability3h: Double? = null
)

// Modelo guardado para inspección
data class MeteoModelInfo(
    val wNorm: Double?,
    val cNorm: Double?,
    val mix: Mix?
)

// Resultado de scoring con desglose
data class ScoreBreakdown(
    val subscores: Subscores,
    val meteoModel: MeteoModelInfo?,
    val weightsUsed: WeightsUsed,
    val bioMultipliers: BioMultipliers
) {
    data class Subscores(
        val meteo: Int,
        val astro: Int,
        val hydro: Int?,
        val marine: Int?
    )
    data class WeightsUsed(val meteo: Double, val astro: Double, val hydro: Double, val marine: Double)
    data class BioMultipliers(val seasonFactor: Double, val spawnPenalty: Double, val nightFactor: Double, val moonFactor: Double)
}

data class ActivityScore(
    val overall: Int,
    val factors: Factors,
    val reasons: List<String>,
    val bestWindows: List<BestWindow>,
    val recommendation: String,
    val confidence: Double,
    val breakdown: ScoreBreakdown
) {
    data class Factors(
        val weather: Int,
        val astronomy: Int,
        val pressure: Double,
        val wind: Double,
        val moon: Double,
        val hydro: Int
    )

    data class BestWindow(
        val start: String,
        val end: String,
        val score: Int,
        val reason: String
    )
}

// Nota: ScoringContext y CalculateArgs pueden variar entre implementaciones (epoch ms vs Instant).
// Mantenerlos en cada implementación si son distintos.
