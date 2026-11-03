package com.david.carpcast.repository

import android.os.Build
import androidx.annotation.RequiresApi
import com.david.carpcast.astronomy.AstronomyClientFactory
import com.david.carpcast.astronomy.AstronomyResult
import com.david.carpcast.network.*
import com.david.carpcast.hydrology.HydrologyClientFactory
import com.david.carpcast.util.TimeUtils
import kotlinx.coroutines.CancellationException
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Response
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import com.david.carpcast.scoring.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import android.util.Log

class ForecastRepository {
    private val openMeteo = NetworkModule.create(NetworkModule.openMeteo, OpenMeteoApi::class.java)
    private val geocoding = NetworkModule.create(NetworkModule.nominatim, GeocodingApi::class.java)
    private val astronomy = NetworkModule.create(NetworkModule.sunriseSunset, AstronomyApi::class.java)
    private val hydrologyClient = HydrologyClientFactory.create()

    // Helper: parse robusto de cadenas ISO datetime (acepta sin segundos y sin offset)
    // Retorna epoch millis o null si no se pudo parsear
    private fun parseTimeMs(timeIso: String?): Long? {
        return TimeUtils.parseTimeMs(timeIso)
    }

    // Nuevo: wrapper para el AstronomyClient con fallbacks (USNO -> Sunrise-Sunset -> FarmSense)
    suspend fun getAstronomyResult(dateIso: String, lat: Double, lon: Double): AstronomyResult = withContext(Dispatchers.IO) {
        val client = AstronomyClientFactory.create()
        client.getAstronomy(dateIso, lat, lon)
    }

    // Rate limiter para Nominatim: 1 request / segundo
    private val lastNominatimCall = AtomicLong(0L)
    private suspend fun ensureNominatimRate() {
        val now = System.currentTimeMillis()
        val prev = lastNominatimCall.get()
        val elapsed = now - prev
        if (elapsed < 1100) {
            delay(1100 - elapsed)
        }
        lastNominatimCall.set(System.currentTimeMillis())
    }

    suspend fun geocode(query: String): List<NominatimResult> = withContext(Dispatchers.IO) {
        ensureNominatimRate()
        try {
            geocoding.search(q = query)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun reverseGeocode(lat: Double, lon: Double): NominatimResult? = withContext(Dispatchers.IO) {
        ensureNominatimRate()
        try {
            geocoding.reverse(lat = lat, lon = lon)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getWeather(lat: Double, lon: Double): Response<ResponseBody> = withContext(Dispatchers.IO) {
        openMeteo.getForecast(latitude = lat, longitude = lon)
    }

    suspend fun getAstronomy(lat: Double, lon: Double): Response<ResponseBody> = withContext(Dispatchers.IO) {
        astronomy.getSunriseSunset(lat = lat, lng = lon)
    }

    // Hidrología: delegada a HydrologyClient

    @RequiresApi(Build.VERSION_CODES.O)
    @Throws(IOException::class)
    suspend fun buildForecastJsonReal(lat: Double, lon: Double, species: String, historicalApi: JSONObject? = null, historicalDate: java.time.LocalDate? = null): JSONObject {
        val selectedSpecies = SpeciesSelection.fromId(species)
        val selectedMode = SpeciesSelection.modeFor(selectedSpecies)
        require((historicalApi == null) == (historicalDate == null))
        val api = if (historicalApi != null) JSONObject(historicalApi.toString()) else {
            val resp = getWeather(lat, lon)
            if (!resp.isSuccessful) throw IOException("Weather API error: ${resp.code()}")
            JSONObject(resp.body()?.string() ?: throw IOException("Empty body from weather API"))
        }
        val forecastZone = ZoneId.of(api.getString("timezone"))
        // API local times are normalized once, at the boundary, with date-specific offsets.
        for ((section, keys) in listOf("hourly" to listOf("time"), "daily" to listOf("sunrise", "sunset"))) {
            val obj = api.optJSONObject(section) ?: continue
            for (key in keys) {
                val arr = obj.optJSONArray(key) ?: continue
                for (i in 0 until arr.length()) if (!arr.isNull(i)) arr.put(i, TimeUtils.withOffset(arr.getString(i), forecastZone))
            }
        }
        api.optJSONObject("current_weather")?.let { current ->
            current.put("time", TimeUtils.withOffset(current.getString("time"), forecastZone))
        }

        val dateIso = (historicalDate ?: java.time.LocalDate.now(forecastZone)).toString()

        val respAstroResult = getAstronomyResult(dateIso, lat, lon)

        // Ahora obtenemos la hidrología desde HydrologyClient
        val hydroResult = try {
            if (historicalDate != null) HydrologyFetchResult.Failure(HydrologyFetchResult.FailureType.UNKNOWN, "Historical hydrology unavailable")
            else hydrologyClient.getHydrologyByLocation(lat, lon)
        } catch (t: Exception) {
            if (t is CancellationException) throw t
            HydrologyFetchResult.Failure(HydrologyFetchResult.FailureType.UNKNOWN, t.message)
        }

        val hydroSamples: List<HydrologySample> = when (hydroResult) {
            is HydrologyFetchResult.Success -> hydroResult.samples
            else -> emptyList()
        }


        // Estructura de salida
        val root = JSONObject()
        root.put("lat", lat)
        root.put("lon", lon)
        root.put("species", selectedSpecies.name)
        root.put("mode", selectedMode.name)
        root.put("timezone", forecastZone.id)

        // current_weather (contiene time, temperature, windspeed, winddirection)
        val current = JSONObject()
        val currentWeather = api.optJSONObject("current_weather")
        val currentTimeIso = currentWeather?.optString("time") ?: historicalDate?.atTime(12, 0)?.atZone(forecastZone)?.toOffsetDateTime()?.toString() ?: Instant.now().toString()
        current.put("time", currentTimeIso)

        // Extraer arrays horarios
        val hourly = api.optJSONObject("hourly")
        val times = hourly?.optJSONArray("time")
        val nowMs = historicalDate?.atTime(12, 0)?.atZone(forecastZone)?.toInstant()?.toEpochMilli() ?: System.currentTimeMillis()
        val idx = (0 until (times?.length() ?: 0)).minByOrNull {
            abs((parseTimeMs(times?.optString(it)) ?: Long.MIN_VALUE / 2) - nowMs)
        } ?: throw IOException("Weather API returned no hourly data")

        // Temperatura
        val tArr = hourly?.optJSONArray("temperature_2m")
        val temp = if (currentWeather != null) currentWeather.optDouble("temperature", Double.NaN) else tArr?.optDouble(idx, Double.NaN) ?: Double.NaN
        current.put("temp", if (temp.isNaN()) JSONObject.NULL else temp)

        // Humedad relativa
        val hum = hourly?.optJSONArray("relative_humidity_2m")?.optDouble(idx, Double.NaN) ?: Double.NaN
        current.put("humidity", if (hum.isNaN()) JSONObject.NULL else hum)

        // Viento
        val wind = hourly?.optJSONArray("wind_speed_10m")?.optDouble(idx, Double.NaN) ?: currentWeather?.optDouble("windspeed", Double.NaN) ?: Double.NaN
        current.put("wind_speed", if (wind.isNaN()) JSONObject.NULL else wind)

        val gust = hourly?.optJSONArray("wind_gusts_10m")?.optDouble(idx, Double.NaN) ?: Double.NaN
        if (!gust.isNaN()) current.put("gust", gust)

        // Precipitación y probabilidad
        val precip = hourly?.optJSONArray("precipitation")?.optDouble(idx, Double.NaN) ?: Double.NaN
        current.put("precip", if (precip.isNaN()) 0.0 else precip)

        val pop = hourly?.optJSONArray("precipitation_probability")?.optDouble(idx, Double.NaN) ?: Double.NaN
        if (!pop.isNaN()) current.put("pop", pop)

        // Presión (pressure_msl)
        val pressure = hourly?.optJSONArray("pressure_msl")?.optDouble(idx, Double.NaN) ?: Double.NaN
        current.put("pressure", if (pressure.isNaN()) JSONObject.NULL else pressure)

        // Nubosidad / UV / SW radiation
        val clouds = hourly?.optJSONArray("cloud_cover")?.optDouble(idx, Double.NaN) ?: Double.NaN
        if (!clouds.isNaN()) current.put("clouds", clouds)
        val cloudsLow = hourly?.optJSONArray("cloud_cover_low")?.optDouble(idx, Double.NaN) ?: Double.NaN
        if (!cloudsLow.isNaN()) current.put("clouds_low", cloudsLow)
        val cloudsMid = hourly?.optJSONArray("cloud_cover_mid")?.optDouble(idx, Double.NaN) ?: Double.NaN
        if (!cloudsMid.isNaN()) current.put("clouds_mid", cloudsMid)
        val cloudsHigh = hourly?.optJSONArray("cloud_cover_high")?.optDouble(idx, Double.NaN) ?: Double.NaN
        if (!cloudsHigh.isNaN()) current.put("clouds_high", cloudsHigh)

        val uvi = hourly?.optJSONArray("uv_index")?.optDouble(idx, Double.NaN) ?: Double.NaN
        if (!uvi.isNaN()) current.put("uvi", uvi)

        val sw = hourly?.optJSONArray("shortwave_radiation")?.optDouble(idx, Double.NaN) ?: Double.NaN
        if (!sw.isNaN()) current.put("shortwave_radiation", sw)

        // is_day: comparar con sunrise/sunset del día (daily arrays)
        val daily = api.optJSONObject("daily")
        val sunriseArr = daily?.optJSONArray("sunrise")
        val sunsetArr = daily?.optJSONArray("sunset")
        val sunrise = sunriseArr?.optString(0)
        val sunset = sunsetArr?.optString(0)
        val isDay = currentWeather?.optInt("is_day", -1)?.takeIf { it >= 0 }?.let { it == 1 }
            ?: hourly?.optJSONArray("is_day")?.optInt(idx, -1)?.takeIf { it >= 0 }?.let { it == 1 }

        current.put("is_day", isDay ?: JSONObject.NULL)

        root.put("current", current)

        // Astro: preferimos datos combinados desde AstronomyClient, fallback a daily arrays si es necesario
        val astro = JSONObject()
        when (respAstroResult) {
            is AstronomyResult.Success -> {
                astro.put("sunrise", respAstroResult.sunrise ?: JSONObject.NULL)
                astro.put("sunset", respAstroResult.sunset ?: JSONObject.NULL)
                if (respAstroResult.moonPhase != null) astro.put("moon_phase", respAstroResult.moonPhase)
                if (respAstroResult.moonIllumination != null) astro.put("moon_illumination", respAstroResult.moonIllumination)
                astro.put("astro_source", respAstroResult.source.name)
            }
            is AstronomyResult.Failure -> {
                // fallback: usar sunrise/sunset del JSON "daily" como se hacía antes
                if (sunrise != null) astro.put("sunrise", sunrise)
                if (sunset != null) astro.put("sunset", sunset)
                astro.put("astro_source", "daily_fallback")
            }
        }
        sunrise?.let { astro.put("sunrise", it) }
        sunset?.let { astro.put("sunset", it) }
        root.put("astro", astro)

        // Hidrología: incluir lista de muestras (puede estar vacía)
        val hydroArr = JSONArray()
        for (h in hydroSamples) {
            val ho = JSONObject()
            ho.put("site_id", h.siteId ?: JSONObject.NULL)
            ho.put("parameter", h.parameter ?: JSONObject.NULL)
            ho.put("value", if (h.value == null) JSONObject.NULL else h.value)
            ho.put("unit", h.unit ?: JSONObject.NULL)
            ho.put("time", h.time ?: JSONObject.NULL)
            hydroArr.put(ho)
        }
        root.put("hydrology", hydroArr)

        // Si hubo fallo al obtener hidrología, incluir razón
        if (hydroResult is HydrologyFetchResult.Failure) {
            val err = JSONObject()
            err.put("type", hydroResult.type.name)
            err.put("message", hydroResult.message ?: JSONObject.NULL)
            root.put("hydrology_error", err)
        }

        // Opcional: incluir raw API para debugging
        root.put("raw_api", api)

        // =================================================================
        // Nueva: construir forecasts por hora y calcular scoring similar al servidor
        // =================================================================
        try {
            // Construir lista de WeatherData por hora (si no hay hourly mantenemos comportamiento previo)
            val weatherHours = mutableListOf<WeatherData>()
            if (hourly != null && times != null) {
                val tempArr = hourly.optJSONArray("temperature_2m")
                val rhArr = hourly.optJSONArray("relative_humidity_2m")
                val dewPointArr = hourly.optJSONArray("dew_point_2m")
                val precipArr = hourly.optJSONArray("precipitation")
                val popArr = hourly.optJSONArray("precipitation_probability")
                val cloudArr = hourly.optJSONArray("cloud_cover")
                val cloudLowArr = hourly.optJSONArray("cloud_cover_low")
                val cloudMidArr = hourly.optJSONArray("cloud_cover_mid")
                val cloudHighArr = hourly.optJSONArray("cloud_cover_high")
                val wsArr = hourly.optJSONArray("wind_speed_10m")
                val wdArr = hourly.optJSONArray("wind_direction_10m")
                val gustArr = hourly.optJSONArray("wind_gusts_10m")
                val presArr = hourly.optJSONArray("pressure_msl")
                val swArr = hourly.optJSONArray("shortwave_radiation")
                val isDayArr = hourly.optJSONArray("is_day")
                val visibilityArr = hourly.optJSONArray("visibility")
                val uviArr = hourly.optJSONArray("uv_index")

                for (i in 0 until times.length()) {
                    val tIso = times.optString(i)
                    val tempV = tempArr?.optDouble(i, Double.NaN) ?: Double.NaN
                    val dewPointV = dewPointArr?.optDouble(i, Double.NaN) ?: Double.NaN
                    val rhV = rhArr?.optDouble(i, Double.NaN) ?: Double.NaN
                    val presV = presArr?.optDouble(i, Double.NaN) ?: Double.NaN
                    val wsV = wsArr?.optDouble(i, Double.NaN) ?: Double.NaN
                    val wdV = wdArr?.optDouble(i, Double.NaN) ?: Double.NaN
                    val gustV = gustArr?.optDouble(i, Double.NaN) ?: Double.NaN
                    val precipV = precipArr?.optDouble(i, Double.NaN) ?: Double.NaN
                    val isDayV = isDayArr?.optInt(i, -1)?.takeIf { it >= 0 }?.let { it == 1 }
                    val visibilityV = visibilityArr?.optDouble(i, Double.NaN) ?: Double.NaN
                    val popV = popArr?.optDouble(i, Double.NaN) ?: Double.NaN
                    val cloudV = cloudArr?.optDouble(i, Double.NaN) ?: Double.NaN
                    val cloudLowV = cloudLowArr?.optDouble(i, Double.NaN) ?: Double.NaN
                    val cloudMidV = cloudMidArr?.optDouble(i, Double.NaN) ?: Double.NaN
                    val cloudHighV = cloudHighArr?.optDouble(i, Double.NaN) ?: Double.NaN
                    val swV = swArr?.optDouble(i, Double.NaN) ?: Double.NaN
                    val uviV = uviArr?.optDouble(i, Double.NaN) ?: Double.NaN

                    val wd = WeatherData(
                        time = tIso,
                        temperature = if (tempV.isNaN()) 15.0 else tempV,
                        humidity = if (rhV.isNaN()) 75.0 else rhV,
                        pressure = if (presV.isNaN()) 1015.0 else presV,
                        windSpeed = if (wsV.isNaN()) 5.0 else wsV,
                        windDirection = if (wdV.isNaN()) 0.0 else wdV,
                        gustSpeed = if (gustV.isNaN()) null else gustV,
                        precipitation = if (precipV.isNaN()) 0.0 else precipV,
                        precipitationProbability = if (popV.isNaN()) null else popV,
                        cloudCover = if (cloudV.isNaN()) 0.0 else cloudV,
                        cloudCoverLow = if (cloudLowV.isNaN()) 0.0 else cloudLowV,
                        cloudCoverMid = if (cloudMidV.isNaN()) 0.0 else cloudMidV,
                        cloudCoverHigh = if (cloudHighV.isNaN()) 0.0 else cloudHighV,
                        shortwaveRadiation = if (swV.isNaN()) null else swV,
                        uvIndex = if (uviV.isNaN()) null else uviV,
                        isDay = isDayV,
                        dewPoint = if (dewPointV.isNaN()) null else dewPointV,
                        visibility = if (visibilityV.isNaN()) null else visibilityV
                    )
                    weatherHours.add(wd)
                }
            }

            // Anchor (momento de referencia) similar al servidor
            val anchorNowMs = nowMs

            // Construir HydroData a partir de muestras
            val hydroDataForScoring = HydroData(
                waterLevel = hydroSamples.firstOrNull { it.parameter?.contains("level", true) == true }?.value,
                waterFlow = hydroSamples.firstOrNull { val p = it.parameter?.lowercase(); p != null && (p.contains("discharge") || p.contains("flow") || p.contains("caudal")) }?.value,
                waterTemp = hydroSamples.firstOrNull { it.parameter?.lowercase()?.contains("temp") == true }?.value
            )

            // Estimar serie de temperaturas de agua
            val measuredWater = hydroSamples.filter {
                it.parameter?.contains("temp", true) == true && it.value?.isFinite() == true &&
                    it.unit?.lowercase(Locale.ROOT)?.replace(" ", "") in setOf("degc", "°c", "c", "celsius") &&
                    it.time?.let { time -> runCatching { OffsetDateTime.parse(time) }.isSuccess } == true &&
                    parseTimeMs(it.time)?.let { time -> abs(anchorNowMs - time) <= 2 * 3600_000L } == true
            }.maxByOrNull { parseTimeMs(it.time) ?: 0L }
            val estimatedWaterTemps = ActivityScoring.estimateWaterTempSeries(weatherHours)

            // Calcular scoring por hora
            val forecastsArr = JSONArray()
            val itemsForWindows = mutableListOf<Pair<String, Double>>()
            val debugScoringArr = JSONArray()

            for (i in weatherHours.indices) {
                val weatherPoint = weatherHours[i]
                val derived = ScoringUtils.computeDerivedFeatures(weatherHours, i).copy(
                    deltaWaterTemp1h = if (i > 0) estimatedWaterTemps[i] - estimatedWaterTemps[i - 1] else null
                )

                // decidir waterTemp para scoring: ±2h rule
                val pointMs = parseTimeMs(weatherPoint.time) ?: anchorNowMs
                val horizonAbsHours = abs(pointMs - anchorNowMs).toDouble() / (60.0 * 60.0 * 1000.0)
                val waterTempForScoring = if (horizonAbsHours <= 2.0 && measuredWater != null &&
                    abs(pointMs - (parseTimeMs(measuredWater.time) ?: 0L)) <= 2 * 3600_000L) {
                    measuredWater.value
                } else {
                    estimatedWaterTemps.getOrNull(i)
                }

                val waterSource = if (measuredWater != null && waterTempForScoring == measuredWater.value &&
                    horizonAbsHours <= 2.0 && abs(pointMs - (parseTimeMs(measuredWater.time) ?: 0L)) <= 2 * 3600_000L)
                    WaterTemperatureSource.MEASURED else WaterTemperatureSource.ESTIMATED
                val dateIndex = (0 until (daily?.optJSONArray("time")?.length() ?: 0)).firstOrNull {
                    daily?.optJSONArray("time")?.optString(it) == weatherPoint.time.take(10)
                }
                val pointAstro = JSONObject(astro.toString()).apply {
                    remove("sunrise")
                    remove("sunset")
                    if (dateIndex != null) {
                        put("sunrise", sunriseArr?.optString(dateIndex)?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                        put("sunset", sunsetArr?.optString(dateIndex)?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                    }
                }
                val ctx = ScoringContextMs(
                    nowMs = pointMs,
                    anchorNowMs = anchorNowMs,
                    lat = lat,
                    derived = if (waterSource == WaterTemperatureSource.MEASURED) derived.copy(deltaWaterTemp1h = null) else derived,
                    waterTemp = waterTempForScoring,
                    waterTempSource = waterSource,
                    dataCoverage = (listOf("temperature_2m", "pressure_msl", "relative_humidity_2m",
                        "wind_speed_10m", "precipitation", "cloud_cover").count {
                            hourly?.optJSONArray(it)?.optDouble(i, Double.NaN)?.isFinite() == true
                        } + (if (weatherPoint.isDay != null) 1 else 0) +
                        (if (derived.deltaPressure3hAvg != null) 1 else 0) +
                        (if (waterTempForScoring?.isFinite() == true) 1 else 0) +
                        (if (waterSource == WaterTemperatureSource.MEASURED) 1 else 0)) / 10.0
                )

                val args = ActivityCalculateArgs(
                    weather = weatherPoint,
                    astro = AstroData(
                        sunrise = pointAstro.optString("sunrise", ""),
                        sunset = pointAstro.optString("sunset", ""),
                        moonIllumination = if (astro.has("moon_illumination")) astro.optDouble("moon_illumination") else null,
                        moonPhase = if (astro.has("moon_phase")) astro.optDouble("moon_phase") else 0.5
                    ),
                    hydro = hydroDataForScoring,
                    marine = null,
                    mode = selectedMode,
                    species = selectedSpecies,
                    context = ctx
                )

                val activityScore = ActivityScoring.calculate(args)

                // Añadir entry de depuración
                try {
                    val dbg = JSONObject()
                    dbg.put("time", weatherPoint.time)
                    dbg.put("weather", JSONObject().apply {
                        put("temperature", weatherPoint.temperature)
                        put("pressure", weatherPoint.pressure)
                        put("humidity", weatherPoint.humidity)
                        put("windSpeed", weatherPoint.windSpeed)
                        put("precipitation", weatherPoint.precipitation)
                    })
                    dbg.put("derived", JSONObject().apply {
                        put("deltaPressure1h", derived.deltaPressure1h ?: JSONObject.NULL)
                        put("deltaPressure3hAvg", derived.deltaPressure3hAvg ?: JSONObject.NULL)
                        put("deltaTemp1h", derived.deltaTemp1h ?: JSONObject.NULL)
                        put("rainPrev6h", derived.rainPrev6h ?: JSONObject.NULL)
                        put("rainSum24h", derived.rainSum24h ?: JSONObject.NULL)
                        put("windStability3h", derived.windStability3h ?: JSONObject.NULL)
                    })
                    dbg.put("waterTempForScoring", if (waterTempForScoring == null) JSONObject.NULL else waterTempForScoring)
                    // añadir desglose completo para diagnóstico (subscores, weights, bio multipliers)
                    try {
                        val breakdown = JSONObject()
                        val subs = JSONObject().apply {
                            put("meteo", activityScore.breakdown.subscores.meteo)
                            put("astro", activityScore.breakdown.subscores.astro)
                            put("hydro", activityScore.breakdown.subscores.hydro ?: JSONObject.NULL)
                            put("marine", activityScore.breakdown.subscores.marine ?: JSONObject.NULL)
                        }
                        val weights = JSONObject().apply {
                            put("meteo", activityScore.breakdown.weightsUsed.meteo)
                            put("astro", activityScore.breakdown.weightsUsed.astro)
                            put("hydro", activityScore.breakdown.weightsUsed.hydro)
                            put("marine", activityScore.breakdown.weightsUsed.marine)
                        }
                        val bio = JSONObject().apply {
                            put("seasonFactor", activityScore.breakdown.bioMultipliers.seasonFactor)
                            put("spawnPenalty", activityScore.breakdown.bioMultipliers.spawnPenalty)
                            put("nightFactor", activityScore.breakdown.bioMultipliers.nightFactor)
                            put("moonFactor", activityScore.breakdown.bioMultipliers.moonFactor)
                        }
                        breakdown.put("subscores", subs)
                        breakdown.put("weights", weights)
                        breakdown.put("bio", bio)
                        dbg.put("breakdown", breakdown)
                    } catch (_: Exception) {
                    }
                    dbg.put("activity_overall", activityScore.overall)
                    dbg.put("activity_confidence", activityScore.confidence)
                    dbg.put("factors", JSONObject().apply {
                        put("weather", activityScore.factors.weather)
                        put("astronomy", activityScore.factors.astronomy)
                        put("pressure", activityScore.factors.pressure)
                        put("wind", activityScore.factors.wind)
                        put("moon", activityScore.factors.moon)
                        put("hydro", activityScore.factors.hydro)
                    })
                    debugScoringArr.put(dbg)
                    Log.d("DEBUG_SCORING", dbg.toString())
                } catch (_: Exception) {
                }

                 // agregar al array forecasts
                 val fObj = JSONObject()
                 fObj.put("time", weatherPoint.time)
                 fObj.put("weather", JSONObject().apply {
                     put("time", weatherPoint.time)
                     put("weather_code", hourly?.optJSONArray("weather_code")?.optInt(i, -1)?.takeIf { it >= 0 } ?: JSONObject.NULL)
                     put("temperature", weatherPoint.temperature)
                     put("is_day", weatherPoint.isDay ?: JSONObject.NULL)
                     put("wind_direction", weatherPoint.windDirection ?: JSONObject.NULL)
                     put("dew_point", weatherPoint.dewPoint ?: JSONObject.NULL)
                     put("humidity", weatherPoint.humidity)
                     put("wind_speed", weatherPoint.windSpeed)
                     put("gust", weatherPoint.gustSpeed ?: JSONObject.NULL)
                     put("precip", weatherPoint.precipitation)
                     put("pop", weatherPoint.precipitationProbability ?: JSONObject.NULL)
                     put("pressure", weatherPoint.pressure)
                     put("clouds", weatherPoint.cloudCover)
                     put("shortwave_radiation", weatherPoint.shortwaveRadiation ?: JSONObject.NULL)
                     put("uvi", weatherPoint.uvIndex ?: JSONObject.NULL)
                 })
                 fObj.put("activityBySpecies", JSONObject().apply {
                     for (fish in listOf(FishSpecies.carp, FishSpecies.barbel, FishSpecies.bass, FishSpecies.pike, FishSpecies.catfish)) {
                         val specific = if (fish == selectedSpecies) activityScore else ActivityScoring.calculate(
                             args.copy(species = fish, mode = SpeciesSelection.modeFor(fish)))
                         put(fish.name, JSONObject().put("overall", specific.overall))
                     }
                 })

                fObj.put("astronomy", pointAstro)
                fObj.put("hydro", JSONObject().apply {
                    put("waterLevel", hydroDataForScoring.waterLevel ?: JSONObject.NULL)
                    put("waterFlow", hydroDataForScoring.waterFlow ?: JSONObject.NULL)
                    put("waterTemp", waterTempForScoring ?: JSONObject.NULL)
                    put("waterTempSource", waterSource.name.lowercase(Locale.ROOT))
                })

                fObj.put("activity", JSONObject().apply {
                    put("overall", activityScore.overall)
                    put("factors", JSONObject().apply {
                        put("weather", activityScore.factors.weather)
                        put("astronomy", activityScore.factors.astronomy)
                        put("pressure", activityScore.factors.pressure)
                        put("wind", activityScore.factors.wind)
                        put("moon", activityScore.factors.moon)
                        put("hydro", activityScore.factors.hydro)
                    })
                    put("reasons", JSONArray().apply { activityScore.reasons.forEach { put(it) } })
                    put("recommendation", activityScore.recommendation)
                    put("confidence", activityScore.confidence) // compatibility: input coverage, not capture probability
                    put("influences", JSONArray().apply {
                        activityScore.breakdown.meteoModel?.influences?.forEach { influence ->
                            put(JSONObject().put("key", influence.key).put("contribution", influence.contribution))
                        }
                    })
                    put("dataCoverage", activityScore.confidence)
                    put("waterTempSource", waterSource.name.lowercase(Locale.ROOT))
                })

                fObj.put("location", JSONObject().apply {
                    put("latitude", lat)
                    put("longitude", lon)
                    put("name", String.format(Locale.US, "%.4f, %.4f", lat, lon))
                })

                forecastsArr.put(fObj)
                itemsForWindows.add(Pair(weatherPoint.time, activityScore.overall.toDouble()))
            }

            // Calcular bestWindows
            val bestWindowsList = ActivityScoring.findBestWindows(itemsForWindows)
            val bestWindowsArr = JSONArray()
            for (bw in bestWindowsList) {
                bestWindowsArr.put(JSONObject().apply {
                    put("start", bw.start)
                    put("end", bw.end)
                    put("score", bw.score)
                    put("reason", bw.reason)
                })
            }

            // ---------------------------
            // Nuevas: bestWindows filtradas para el día actual
            // ---------------------------
            // Determinar la fecha local del anchorNowMs para filtrar puntos del mismo día (soporta días con más puntos)
            val anchorDateLocal = try {
                java.time.LocalDate.ofInstant(Instant.ofEpochMilli(anchorNowMs), forecastZone)
            } catch (_: Exception) {
                java.time.LocalDate.now()
            }

            val itemsForToday = itemsForWindows.filter { pair ->
                val timeStr = pair.first
                val tMs = parseTimeMs(timeStr) ?: return@filter false
                val ld = java.time.LocalDate.ofInstant(Instant.ofEpochMilli(tMs), forecastZone)
                ld == anchorDateLocal
            }.sortedBy { parseTimeMs(it.first) ?: 0L }

            val bestWindowsTodayList = ActivityScoring.findBestWindows(itemsForToday)
            val bestWindowsTodayArr = JSONArray()
            for (bw in bestWindowsTodayList) {
                bestWindowsTodayArr.put(JSONObject().apply {
                    put("start", bw.start)
                    put("end", bw.end)
                    put("score", bw.score)
                    put("reason", bw.reason)
                })
            }

            val locationObj = JSONObject().apply {
                put("latitude", lat)
                put("longitude", lon)
                put("name", String.format(Locale.US, "%.4f, %.4f", lat, lon))
            }

            val response = JSONObject().apply {
                put("location", locationObj)
                put("mode", selectedMode.name)
                put("species", selectedSpecies.name)
                put("forecasts", forecastsArr)
                put("bestWindows", bestWindowsArr)
                put("bestWindowsToday", bestWindowsTodayArr)
                put("debug_scoring_dump", debugScoringArr)
                put("dataAvailability", JSONObject().apply {
                    put("weather", true)
                    put("astro", true)
                    put("hydro", hydroSamples.isNotEmpty())
                    put("marine", false)
                })
                put("generatedAt", Instant.now().toString())
            }

            // incluir en root
            root.put("forecasts", response.get("forecasts"))
            root.put("bestWindows", response.get("bestWindows"))
            root.put("bestWindowsToday", response.get("bestWindowsToday"))
            root.put("dataAvailability", response.get("dataAvailability"))
            root.put("generatedAt", response.get("generatedAt"))

        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw IOException("Unable to build hourly forecast", e)
        }

        return root
    }

}
