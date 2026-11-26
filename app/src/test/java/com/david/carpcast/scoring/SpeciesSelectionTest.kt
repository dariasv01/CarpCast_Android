package com.david.carpcast.scoring

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class SpeciesSelectionTest {
    @Test fun eachSelectableSpeciesHasItsCorrectMode() {
        for ((id, species, mode) in listOf(
            Triple("carp", FishSpecies.carp, FishingMode.carpfishing),
            Triple("barbel", FishSpecies.barbel, FishingMode.carpfishing),
            Triple("bass", FishSpecies.bass, FishingMode.predator),
            Triple("pike", FishSpecies.pike, FishingMode.predator),
            Triple("catfish", FishSpecies.catfish, FishingMode.predator)
        )) {
            assertEquals(id, species, SpeciesSelection.fromId(id))
            assertEquals(id, mode, SpeciesSelection.modeFor(SpeciesSelection.fromId(id)))
        }
    }

    @Test fun localizedAndLegacyIdsResolveToTheSameSpecies() {
        for ((id, species) in listOf(" CARPA " to FishSpecies.carp, "Barbo" to FishSpecies.barbel,
            "blackbass" to FishSpecies.bass, "Black Bass" to FishSpecies.bass,
            "LUCIO" to FishSpecies.pike, "siluro" to FishSpecies.catfish)) {
            assertEquals(id, species, SpeciesSelection.fromId(id))
        }
        assertEquals(FishSpecies.general, SpeciesSelection.fromId("unknown"))
    }

    @Test fun selectableSpeciesActivateSpecificModelsInBothScoringEntryPoints() {
        val weather = WeatherData(time = "2026-03-15T12:00:00Z", temperature = 20.0,
            humidity = 70.0, pressure = 1008.0, windSpeed = 10.0, precipitation = 0.0,
            cloudCover = 60.0, isDay = true)
        val astro = AstroData("2026-03-15T06:00:00Z", "2026-03-15T18:00:00Z", 50.0, 0.25)
        val now = Instant.parse(weather.time)
        for (id in listOf("carp", "barbel", "bass", "pike", "catfish")) {
            val species = SpeciesSelection.fromId(id)
            val mode = SpeciesSelection.modeFor(species)
            val result = ActivityScoring.calculate(ActivityCalculateArgs(weather, astro,
                mode = mode, species = species,
                context = ScoringContextMs(now.toEpochMilli(), lat = 40.0, waterTemp = 20.0)))
            // The generic fallback has no species meteo model; it must never be used here.
            assertNotNull(id, result.breakdown.meteoModel)
            assertTrue(id, result.reasons.any { it.startsWith("Especie:") && !it.contains("General") })
            val displayed = ScoringTsPort.calculate(ScoringTsPort.CalculateArgsInstant(weather, astro,
                mode = mode, species = species,
                context = ScoringTsPort.ScoringContextInstant(now = now, lat = 40.0, waterTemp = 20.0)))
            assertEquals(id, result, displayed)
        }
    }
}
