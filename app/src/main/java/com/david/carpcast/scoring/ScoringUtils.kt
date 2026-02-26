package com.david.carpcast.scoring

/**
 * Decide un mix W/C ajustado en función del horizonte (horas).
 * - Para horizontes cortos (< = 6h) devuelve el mix base.
 * - Para horizontes más largos aumenta el peso de C (acumulados) progresivamente.
 */
fun meteoMixForHorizon(species: FishSpecies, base: Mix, horizonHours: Double): Mix {
    if (!horizonHours.isFinite() || horizonHours <= 6.0) return base

    // Aumentamos gradualmente el peso de C hasta +0.5 sobre el valor base en 72 horas.
    val extra = clamp01((horizonHours - 6.0) / 72.0) * 0.5
    var c = (base.c + extra).coerceAtMost(1.0)
    var w = (1.0 - c).coerceAtLeast(0.0)
    // Normalizar por si hay redondeos
    val sum = w + c
    if (sum <= 0.0) return base
    w /= sum
    c /= sum
    return Mix(w, c)
}

private fun clamp01(x: Double): Double = when {
    x.isNaN() -> 0.0
    x <= 0.0 -> 0.0
    x >= 1.0 -> 1.0
    else -> x
}

object ScoringUtils {
    fun computeDerivedFeatures(weatherData: List<WeatherData>, index: Int): DerivedWeatherFeatures {
        val w = weatherData.getOrNull(index) ?: return DerivedWeatherFeatures()
        val prev1 = if (index - 1 >= 0) weatherData[index - 1] else null
        val prev3 = if (index - 3 >= 0) weatherData[index - 3] else null

        val deltaPressure1h = prev1?.let { w.pressure - it.pressure }
        val deltaTemp1h = prev1?.let { w.temperature - it.temperature }

        var deltaPressure3hAvg: Double? = null
        if (prev3 != null && index - 1 >= 0) {
            val deltas = mutableListOf<Double>()
            for (i in (index - 3)..index) {
                if (i - 1 < 0) continue
                val cur = weatherData[i]
                val prev = weatherData[i - 1]
                deltas.add(cur.pressure - prev.pressure)
            }
            if (deltas.isNotEmpty()) deltaPressure3hAvg = deltas.sum() / deltas.size
        }

        fun sumPrecip(fromInclusive: Int, toInclusive: Int): Double {
            var sum = 0.0
            for (i in maxOf(0, fromInclusive) .. minOf(weatherData.size - 1, toInclusive)) {
                sum += weatherData.getOrNull(i)?.precipitation ?: 0.0
            }
            return sum
        }

        val rainPrev6h = if (index - 1 >= 0) sumPrecip(index - 6, index - 1) else null
        val rainSum24h = if (index - 1 >= 0) sumPrecip(index - 24, index - 1) else null

        var windStability3h: Double? = null
        if (index - 2 >= 0) {
            val speeds = listOf(
                weatherData[index - 2].windSpeed,
                weatherData[index - 1].windSpeed,
                weatherData[index].windSpeed
            )
            val avg = speeds.sum() / speeds.size
            val variance = speeds.fold(0.0) { acc, v -> acc + (v - avg) * (v - avg) } / speeds.size
            val std = kotlin.math.sqrt(variance)
            windStability3h = kotlin.math.max(0.0, kotlin.math.min(1.0, 1 - std / kotlin.math.max(1.0, avg)))
        }

        return DerivedWeatherFeatures(
            deltaPressure1h = deltaPressure1h,
            deltaPressure3hAvg = deltaPressure3hAvg,
            deltaTemp1h = deltaTemp1h,
            rainPrev6h = rainPrev6h,
            rainSum24h = rainSum24h,
            windStability3h = windStability3h
        )
    }
}
