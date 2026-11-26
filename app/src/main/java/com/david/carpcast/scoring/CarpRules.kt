package com.david.carpcast.scoring

/** Provisional rules, not fitted capture probabilities. No new feature weights. */
object CarpRules {
    // Linear interpolation preserves the old broad comfort range without threshold jumps.
    fun temperature(t: Double): Double {
        if (!t.isFinite()) return 0.5
        val points = listOf(2.0 to 0.1, 8.0 to 0.1, 15.0 to 0.4, 18.0 to 1.0,
            30.0 to 1.0, 32.0 to 0.7, 34.0 to 0.4, 43.0 to 0.1)
        if (t <= points.first().first) return points.first().second
        for ((a, b) in points.zipWithNext()) {
            if (t <= b.first) return a.second + (b.second - a.second) * (t - a.first) / (b.first - a.first)
        }
        return points.last().second
    }

    fun temperatureChange(currentWater: Double?, deltaWater: Double?): Double {
        if (currentWater == null || deltaWater == null || !currentWater.isFinite() || !deltaWater.isFinite()) return 0.5
        return (0.5 + temperature(currentWater) - temperature(currentWater - deltaWater)).coerceIn(0.0, 1.0)
    }

    // With unknown thermal context, rain has no assumed benefit. Cold water reverses
    // the old rain bonus; between 12 and 18 C it transitions continuously to that bonus.
    fun rain(base: Double, water: Double?): Double {
        if (!base.isFinite()) return 0.5
        // Cold water reverses benefits, never the penalty for excessive rainfall.
        if (base <= 0.5) return base.coerceIn(0.0, 1.0)
        if (water == null || !water.isFinite()) return 0.5
        val context = ((water - 15.0) / 3.0).coerceIn(-1.0, 1.0)
        return (0.5 + (base - 0.5) * context).coerceIn(0.0, 1.0)
    }

    /** Fraction of available inputs; estimates and measured water are separate inputs. */
    fun dataCoverage(weather: WeatherData, source: WaterTemperatureSource, derived: DerivedWeatherFeatures?): Double {
        val present = listOf(weather.temperature.isFinite(), weather.pressure.isFinite(),
            weather.humidity.isFinite(), weather.windSpeed.isFinite(), weather.precipitation.isFinite(),
            weather.cloudCover.isFinite(), weather.isDay != null,
            derived?.deltaPressure3hAvg?.isFinite() == true,
            source != WaterTemperatureSource.UNKNOWN, source == WaterTemperatureSource.MEASURED)
        return present.count { it }.toDouble() / present.size
    }
}
