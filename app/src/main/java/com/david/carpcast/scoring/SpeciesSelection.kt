package com.david.carpcast.scoring

import java.util.Locale

/** Shared by forecast generation and display, including legacy JSON without a mode. */
object SpeciesSelection {
    fun fromId(id: String): FishSpecies = when (id.trim().lowercase(Locale.ROOT)) {
        "carp", "carpa" -> FishSpecies.carp
        "barbel", "barbo" -> FishSpecies.barbel
        "bass", "blackbass", "black bass" -> FishSpecies.bass
        "pike", "lucio" -> FishSpecies.pike
        "catfish", "siluro" -> FishSpecies.catfish
        else -> FishSpecies.general
    }

    fun modeFor(species: FishSpecies): FishingMode = when (species) {
        FishSpecies.bass, FishSpecies.pike, FishSpecies.catfish -> FishingMode.predator
        FishSpecies.carp, FishSpecies.barbel, FishSpecies.general -> FishingMode.carpfishing
    }
}
