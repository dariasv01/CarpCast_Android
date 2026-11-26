package com.david.carpcast.scoring

import com.david.carpcast.util.TimeUtils
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CarpRulesTest {
    private val weather = WeatherData(time = "2026-03-15T12:00:00Z", temperature = 20.0,
        humidity = 70.0, pressure = 1008.0, windSpeed = 10.0, precipitation = 0.0,
        cloudCover = 60.0, isDay = true)
    private val astro = AstroData("2026-03-15T06:00:00Z", "2026-03-15T18:00:00Z", 50.0, 0.25)
    private fun args(hydro: HydroData? = null, moon: Double = 0.25, month: Int = 3): ActivityCalculateArgs {
        val day = "2026-${month.toString().padStart(2, '0')}-15"
        return ActivityCalculateArgs(weather.copy(time = "${day}T12:00:00Z"),
            astro.copy(sunrise = "${day}T06:00:00Z", sunset = "${day}T18:00:00Z", moonPhase = moon),
            hydro = hydro, species = FishSpecies.carp,
            context = ScoringContextMs(Instant.parse("${day}T12:00:00Z").toEpochMilli(), lat = 40.0,
                waterTemp = 20.0, waterTempSource = WaterTemperatureSource.ESTIMATED))
    }

    @Test fun temperatureHasNoJumpsAtOldThresholds() {
        for (t in listOf(8.0, 15.0, 18.0, 30.0, 32.0, 34.0)) {
            assertEquals(CarpRules.temperature(t - 0.0001), CarpRules.temperature(t + 0.0001), 0.001)
        }
    }

    @Test fun coolingColdWaterHurtsWhileCoolingOverheatedWaterHelps() {
        assertTrue(CarpRules.temperatureChange(14.0, -1.0) < 0.5)
        assertTrue(CarpRules.temperatureChange(32.0, -1.0) > 0.5)
        assertEquals(0.5, CarpRules.temperatureChange(null, -1.0), 0.0)
    }

    @Test fun rainDependsOnThermalContext() {
        assertTrue(CarpRules.rain(1.0, 10.0) < 0.5)
        assertTrue(CarpRules.rain(1.0, 22.0) > 0.5)
        assertEquals(0.5, CarpRules.rain(1.0, null), 0.0)
        assertTrue(CarpRules.rain(0.15, 10.0) < 0.5)
        assertTrue(CarpRules.rain(0.15, 22.0) < 0.5)
    }

    @Test fun moreHydrologyDataDoesNotCreateMoreCarpActivity() {
        val empty = ActivityScoring.calculate(args())
        val full = ActivityScoring.calculate(args(HydroData(2.0, 40.0, 20.0)))
        assertEquals(empty.overall, full.overall)
        assertEquals(0.0, full.breakdown.weightsUsed.hydro, 0.0)
    }

    @Test fun unvalidatedMoonSeasonAndSpawningDoNotMultiplyCarpScore() {
        val baseline = ActivityScoring.calculate(args())
        val changed = ActivityScoring.calculate(args(moon = 0.5, month = 6))
        assertEquals(baseline.overall, changed.overall)
        assertEquals(1.0, changed.breakdown.bioMultipliers.moonFactor, 0.0)
        assertEquals(1.0, changed.breakdown.bioMultipliers.spawnPenalty, 0.0)
        assertEquals(1.0, changed.breakdown.bioMultipliers.seasonFactor, 0.0)
    }

    @Test fun estimatesDoNotCountAsMeasuredWater() {
        val estimated = CarpRules.dataCoverage(weather, WaterTemperatureSource.ESTIMATED, null)
        val measured = CarpRules.dataCoverage(weather, WaterTemperatureSource.MEASURED, null)
        assertTrue(measured > estimated)
        assertEquals(0.1, measured - estimated, 0.0001)
    }

    @Test fun destinationOffsetsRespectSummerAndWinterTime() {
        val zone = ZoneId.of("Europe/Madrid")
        assertEquals("2026-01-15T12:00+01:00", TimeUtils.withOffset("2026-01-15T12:00", zone))
        assertEquals("2026-07-15T12:00+02:00", TimeUtils.withOffset("2026-07-15T12:00", zone))
        assertEquals(Instant.parse("2026-07-15T10:00:00Z").toEpochMilli(), TimeUtils.parseTimeMs("2026-07-15T12:00", zone))
    }

    @Test fun instantFacadeUsesTheSameEngine() {
        val a = args(HydroData(waterTemp = 20.0))
        val result = ScoringTsPort.calculate(ScoringTsPort.CalculateArgsInstant(a.weather, a.astro,
            hydro = a.hydro, species = a.species,
            context = ScoringTsPort.ScoringContextInstant(now = Instant.ofEpochMilli(a.context!!.nowMs),
                lat = 40.0, waterTemp = 20.0, waterTempSource = WaterTemperatureSource.ESTIMATED)))
        assertEquals(ActivityScoring.calculate(a), result)
    }
}
