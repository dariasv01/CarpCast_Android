package com.david.carpcast.scoring

import org.junit.Assert.*
import org.junit.Test

class ScoringUtilsTest {

    @Test
    fun testEstimateWaterTempSeries_basic() {
        val hours = mutableListOf<WeatherData>()
        for (i in 0 until 5) {
            hours.add(
                WeatherData(
                    time = "2026-02-01T0${i}:00",
                    temperature = 10.0 + i,
                    humidity = 80.0,
                    pressure = 1010.0,
                    surfacePressure = null,
                    windSpeed = 5.0 + i,
                    windDirection = 0.0,
                    gustSpeed = null,
                    precipitation = 0.0,
                    precipitationProbability = null,
                    cloudCover = 20.0,
                    cloudCoverLow = null,
                    cloudCoverMid = null,
                    cloudCoverHigh = null,
                    shortwaveRadiation = 200.0,
                    uvIndex = null,
                    isDay = true,
                    dewPoint = null,
                    visibility = null
                )
            )
        }

        val res = ActivityScoring.estimateWaterTempSeries(hours, seedTempC = 12.0)
        assertEquals(5, res.size)
        // Valores deben ser finitos y razonables
        for (v in res) {
            assertTrue(v >= 2.0 && v <= 34.0)
        }
    }

    @Test
    fun testComputeDerivedFeatures_basic() {
        val hours = mutableListOf<WeatherData>()
        // crear 4 horas con presión incremental
        for (i in 0 until 5) {
            hours.add(
                WeatherData(
                    time = "2026-02-01T0${i}:00",
                    temperature = 10.0,
                    humidity = 80.0,
                    pressure = 1000.0 + i,
                    surfacePressure = null,
                    windSpeed = 5.0 + i,
                    windDirection = 0.0,
                    gustSpeed = null,
                    precipitation = if (i == 2) 1.5 else 0.0,
                    precipitationProbability = null,
                    cloudCover = 20.0,
                    cloudCoverLow = null,
                    cloudCoverMid = null,
                    cloudCoverHigh = null,
                    shortwaveRadiation = null,
                    uvIndex = null,
                    isDay = true,
                    dewPoint = null,
                    visibility = null
                )
            )
        }

        val d = ScoringUtils.computeDerivedFeatures(hours, 3)
        assertNotNull(d.deltaPressure1h)
        assertEquals(1.0, d.deltaPressure1h!!, 1e-6)
        assertNotNull(d.deltaPressure3hAvg)
        assertTrue(d.deltaPressure3hAvg!! >= 0.0)
        assertEquals(1.5, d.rainPrev6h!!, 1e-6)
        assertTrue(d.rainSum24h!! >= 0.0)
        assertNotNull(d.windStability3h)
    }

    @Test
    fun testFindBestWindows_nonOverlapping() {
        val items = listOf(
            ActivityScoring.TimeScore("t1", 30.0),
            ActivityScoring.TimeScore("t2", 80.0),
            ActivityScoring.TimeScore("t3", 70.0),
            ActivityScoring.TimeScore("t4", 60.0),
            ActivityScoring.TimeScore("t5", 50.0),
            ActivityScoring.TimeScore("t6", 90.0)
        )

        val res = ActivityScoring.findBestWindowsDetails(items, windowHours = 3, topN = 3)
        // debe devolver hasta 3 ventanas
        assertTrue(res.size <= 3)
        // la mejor ventana debe tener score redondeado cercano a la media de [t4,t5,t6] o [t2,t3,t4]
        assertTrue(res.any { it.score >= 80 || it.score >= 70 })
    }
}

