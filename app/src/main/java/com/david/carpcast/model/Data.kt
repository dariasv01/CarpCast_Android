package com.david.carpcast.model

import androidx.annotation.DrawableRes

data class SpeciesOption(
    val id: String,
    val label: String,
    val subtitle: String,
    @param:DrawableRes val iconRes: Int
)

data class LocationModel(val name: String, val latitude: Double, val longitude: Double)
