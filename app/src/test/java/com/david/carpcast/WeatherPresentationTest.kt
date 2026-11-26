package com.david.carpcast

import com.david.carpcast.ui.*
import org.junit.Assert.*
import org.junit.Test

class WeatherPresentationTest {
    @Test fun officialCodesPreserveHazardousConditions() {
        for ((code, sky) in listOf(0 to Sky.CLEAR, 2 to Sky.PARTLY, 3 to Sky.CLOUDY, 45 to Sky.FOG, 55 to Sky.DRIZZLE, 66 to Sky.RAIN, 85 to Sky.SNOW, 99 to Sky.STORM)) {
            assertEquals(sky, skyFor(HourlyEntry("", weatherCode = code, cloudCover = 0.0)))
        }
    }
    @Test fun probabilityAloneDoesNotBecomeCertainRain() {
        assertEquals(Sky.CLEAR, skyFor(HourlyEntry("", cloudCover = 0.0, precipitationProbability = 0.8)))
    }
    @Test fun oldForecastsUseAvailableValuesAndMissingIsNotSunny() {
        assertEquals(Sky.RAIN, skyFor(HourlyEntry("", precipitation = 0.2)))
        assertEquals(Sky.CLOUDY, skyFor(HourlyEntry("", cloudCover = 90.0)))
        assertEquals(Sky.UNKNOWN, skyFor(HourlyEntry("")))
    }
}
