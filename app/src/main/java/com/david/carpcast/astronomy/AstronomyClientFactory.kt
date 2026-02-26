package com.david.carpcast.astronomy

import com.david.carpcast.network.NetworkModule

object AstronomyClientFactory {
    fun create(): AstronomyClient {
        val usno = NetworkModule.create(NetworkModule.usno, com.david.carpcast.network.USNOApi::class.java)
        val sunrise = NetworkModule.create(NetworkModule.sunriseSunset, com.david.carpcast.network.SunriseSunsetApi::class.java)
        val farmsense = NetworkModule.create(NetworkModule.farmsense, com.david.carpcast.network.FarmSenseApi::class.java)

        return AstronomyClient(usno, sunrise, farmsense)
    }
}
