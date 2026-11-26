package com.david.carpcast.scoring

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class HydrologyScoringTest {
    private fun args(species: FishSpecies): ActivityCalculateArgs {
        val time = "2026-03-15T12:00:00Z"
        return ActivityCalculateArgs(
            weather = WeatherData(time, 20.0, 70.0, 1008.0,
                windSpeed = 10.0, precipitation = 0.0, cloudCover = 60.0),
            astro = AstroData("2026-03-15T06:00:00Z", "2026-03-15T18:00:00Z", 50.0, 0.25),
            species = species, mode = SpeciesSelection.modeFor(species),
            context = ScoringContextMs(Instant.parse(time).toEpochMilli(), lat = 40.0,
                waterTemp = 20.0, waterTempSource = WaterTemperatureSource.MEASURED))
    }

    private val otherSpecies = listOf(FishSpecies.barbel, FishSpecies.bass,
        FishSpecies.pike, FishSpecies.catfish)

    @Test fun hydrologyAvailabilityAndValuesDoNotCreateActivityForOtherSpecies() {
        for (species in otherSpecies) {
            val input = args(species)
            val baseline = ActivityScoring.calculate(input)
            for (hydro in listOf(HydroData(), HydroData(waterLevel = 2.0),
                HydroData(waterFlow = 40.0), HydroData(waterTemp = 20.0),
                HydroData(2.0, 40.0, 20.0), HydroData(20.0, 4000.0, 3.0),
                HydroData(Double.NaN, Double.POSITIVE_INFINITY, Double.NaN))) {
                assertEquals("$species: $hydro", baseline,
                    ActivityScoring.calculate(input.copy(hydro = hydro)))
            }
        }
    }

    @Test fun remainingBlocksAreRenormalizedWithoutHydrologyBaseline() {
        for (species in otherSpecies) {
            val score = ActivityScoring.calculate(args(species).copy(hydro = HydroData()))
            val weights = score.breakdown.weightsUsed
            assertNull(score.breakdown.subscores.hydro)
            assertEquals(0.0, weights.hydro, 0.0)
            assertEquals(4.0 / 7.0, weights.meteo, 0.000001)
            assertEquals(3.0 / 7.0, weights.astro, 0.000001)
            assertEquals(1.0, weights.meteo + weights.astro + weights.marine, 0.000001)
            assertFalse(score.reasons.any { it.contains("Datos de caudal") || it.contains("Datos de nivel") })
        }
    }

    @Test fun carpKeepsItsMeteorologicalModelAndExistingMultipliers() {
        val input = args(FishSpecies.carp)
        val score = ActivityScoring.calculate(input)
        assertEquals(score, ActivityScoring.calculate(input.copy(hydro = HydroData(2.0, 40.0, 20.0))))
        assertEquals(1.0, score.breakdown.weightsUsed.meteo, 0.0)
        assertEquals(0.0, score.breakdown.weightsUsed.astro, 0.0)
        assertEquals(0.0, score.breakdown.weightsUsed.hydro, 0.0)
        assertEquals(ScoreBreakdown.BioMultipliers(1.0, 1.0, 1.0, 1.0), score.breakdown.bioMultipliers)
    }
}
