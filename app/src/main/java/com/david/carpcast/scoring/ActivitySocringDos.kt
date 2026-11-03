package com.david.carpcast.scoring

import android.os.Build
import androidx.annotation.RequiresApi

/** Compatibility facade: all scoring and window selection live in ActivityScoring. */
object ScoringTsPort {
    @RequiresApi(Build.VERSION_CODES.O)
    fun calculate(args: CalculateArgsInstant): ActivityScore {
        return ActivityScoring.calculate(ActivityCalculateArgs(
            weather = args.weather, astro = args.astro, hydro = args.hydro, marine = args.marine,
            mode = args.mode, species = args.species,
            context = args.context?.let { ScoringContextMs(
                nowMs = (it.now ?: java.time.Instant.ofEpochMilli(requireNotNull(com.david.carpcast.util.TimeUtils.parseTimeMs(args.weather.time)))).toEpochMilli(),
                anchorNowMs = it.anchorNow?.toEpochMilli(), lat = it.lat, derived = it.derived,
                waterTemp = it.waterTemp, waterTempSource = it.waterTempSource, dataCoverage = it.dataCoverage
            ) }
        ))
    }

    fun estimateWaterTempSeries(weatherHours: List<WeatherData>, seedTempC: Double? = null): List<Double> =
        ActivityScoring.estimateWaterTempSeries(weatherHours, seedTempC)

    data class TimeScore(val time: String, val score: Double?)

    fun findBestWindowsDetails(items: List<TimeScore>?, windowHours: Int = 3, topN: Int = 3): List<ActivityScore.BestWindow> =
        ActivityScoring.findBestWindowsDetails(items?.map { ActivityScoring.TimeScore(it.time, it.score) }, windowHours, topN)

    fun findBestWindows(items: List<Pair<String, Double>>, windowHours: Int = 3, topN: Int = 3): List<ActivityScore.BestWindow> =
        ActivityScoring.findBestWindows(items, windowHours, topN)

    data class ScoringContextInstant(
        val now: java.time.Instant? = null,
        val anchorNow: java.time.Instant? = null,
        val lat: Double = 0.0,
        val derived: DerivedWeatherFeatures? = null,
        val waterTemp: Double? = null,
        val waterTempSource: WaterTemperatureSource = WaterTemperatureSource.UNKNOWN,
        val dataCoverage: Double? = null
    )

    data class CalculateArgsInstant(
        val weather: WeatherData,
        val astro: AstroData,
        val hydro: HydroData? = null,
        val marine: MarineData? = null,
        val mode: FishingMode = FishingMode.carpfishing,
        val species: FishSpecies = FishSpecies.general,
        val context: ScoringContextInstant? = null
    )

}
