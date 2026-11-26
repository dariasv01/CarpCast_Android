package com.david.carpcast.journal

import com.david.carpcast.ui.HourlyEntry
import com.david.carpcast.util.TimeUtils
import org.json.JSONObject

/** An hourly forecast covers [start, start + 1h), matched by instant, including DST offsets. */
data class CatchForecast(val hour: HourlyEntry, val generatedAt: String, val species: String, val savedAfterCatch: Boolean, val historical: Boolean = false)

fun forecastAtCatch(snapshot: String?, atMs: Long, speciesId: String? = null): CatchForecast? = runCatching {
    val root = JSONObject(snapshot ?: return null)
    val hours = root.optJSONArray("forecasts") ?: return null
    val point = (0 until hours.length()).mapNotNull { index ->
        val value = hours.optJSONObject(index) ?: return@mapNotNull null
        val start = TimeUtils.parseTimeMs(value.optString("time")) ?: return@mapNotNull null
        if (atMs >= start && atMs < start + 3_600_000L) start to value else null
    }.maxByOrNull { it.first }?.second ?: return null
    val weather = point.optJSONObject("weather")
    fun number(key: String): Double? = weather?.optDouble(key, Double.NaN)?.takeIf { it.isFinite() }
    val generated = root.optString("generatedAt", "")
    val requested = speciesId ?: root.optString("species", "general")
    val fish = com.david.carpcast.scoring.SpeciesSelection.fromId(requested)
    val storedFish = com.david.carpcast.scoring.SpeciesSelection.fromId(root.optString("species", "general"))
    val supported = fish != com.david.carpcast.scoring.FishSpecies.general
    val activity = if (!supported && speciesId != null) null else
        point.optJSONObject("activityBySpecies")?.optJSONObject(fish.name)
            ?: if (fish == storedFish) point.optJSONObject("activity") else null
    CatchForecast(
        hour = HourlyEntry(time = point.getString("time"),
            score = activity?.optInt("overall", -1)?.takeIf { it in 0..100 },
            temperature = number("temperature"), precipitation = number("precip"),
            precipitationProbability = number("pop")?.div(100)?.coerceIn(0.0, 1.0),
            windSpeed = number("wind_speed"), windGust = number("gust"), pressure = number("pressure"),
            cloudCover = number("clouds"), weatherCode = weather?.optInt("weather_code", -1)?.takeIf { it >= 0 },
            isDay = if (weather?.isNull("is_day") == false) weather.optBoolean("is_day") else null),
        generatedAt = generated, species = if (supported) fish.name else requested,
        savedAfterCatch = (TimeUtils.parseTimeMs(generated) ?: Long.MIN_VALUE) > atMs,
        historical = root.optString("source") == "historical_reanalysis"
    )
}.getOrNull()
