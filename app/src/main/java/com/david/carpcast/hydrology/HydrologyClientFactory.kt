package com.david.carpcast.hydrology

import com.david.carpcast.network.NetworkModule

object HydrologyClientFactory {
    fun create(): HydrologyClient {
        val usgs = NetworkModule.create(NetworkModule.usgs, com.david.carpcast.network.UsgsApi::class.java)
        val usgsSite = NetworkModule.create(NetworkModule.usgsSite, com.david.carpcast.network.UsgsSiteApi::class.java)
        val flood = NetworkModule.create(NetworkModule.floodOpenMeteo, com.david.carpcast.network.FloodOpenMeteoApi::class.java)
        val geocoding = NetworkModule.create(NetworkModule.nominatim, com.david.carpcast.network.GeocodingApi::class.java)

        return HydrologyClient(usgs, usgsSite, flood, geocoding)
    }
}
