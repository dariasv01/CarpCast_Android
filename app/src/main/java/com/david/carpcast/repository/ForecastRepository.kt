package com.david.carpcast.repository

import android.os.Build
import androidx.annotation.RequiresApi
import com.david.carpcast.astronomy.AstronomyClientFactory
import com.david.carpcast.astronomy.AstronomyResult
import com.david.carpcast.network.*
import com.david.carpcast.hydrology.HydrologyClientFactory
import com.david.carpcast.util.TimeUtils
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
    suspend fun buildForecastJsonReal(lat: Double, lon: Double, species: String): JSONObject {
        val resp = getWeather(lat, lon)

        val dateIso = Instant.now().toString().substring(0, 10)

        val respAstroResult = getAstronomyResult(dateIso, lat, lon)

        // Ahora obtenemos la hidrología desde HydrologyClient
        val hydroResult = try {
            hydrologyClient.getHydrologyByLocation(lat, lon)
        } catch (t: Throwable) {
            HydrologyFetchResult.Failure(HydrologyFetchResult.FailureType.UNKNOWN, t.message)
        }

        val hydroSamples: List<HydrologySample> = when (hydroResult) {
            is HydrologyFetchResult.Success -> hydroResult.samples
            else -> emptyList()
        }

        if (!resp.isSuccessful) throw IOException("Weather API error: ${resp.code()}")

        val body = resp.body()?.string() ?: throw IOException("Empty body from weather API")
        val api = JSONObject(body)

        // Estructura de salida
        val root = JSONObject()
        root.put("lat", lat)
        root.put("lon", lon)
        root.put("species", species)

        // current_weather (contiene time, temperature, windspeed, winddirection)
        val current = JSONObject()
        val currentWeather = api.optJSONObject("current_weather")
        val currentTimeIso = currentWeather?.optString("time") ?: api.optJSONObject("hourly")?.optJSONArray("time")?.optString(0) ?: ""
        current.put("time", currentTimeIso)

        // Extraer arrays horarios
        val hourly = api.optJSONObject("hourly")
        val times = hourly?.optJSONArray("time")
        fun indexOfTime(t: String?): Int {
            if (t == null || times == null) return 0
            for (i in 0 until times.length()) {
                if (t == times.optString(i)) return i
            }
            return 0
        }

        val idx = indexOfTime(currentTimeIso)

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

        val gust = hourly?.optJSONArray("windgusts_10m")?.optDouble(idx, Double.NaN) ?: Double.NaN
        if (!gust.isNaN()) current.put("gust", gust)

        // Precipitación y probabilidad
        val precip = hourly?.optJSONArray("precipitation")?.optDouble(idx, Double.NaN) ?: Double.NaN
        current.put("precip", if (precip.isNaN()) 0.0 else precip)

        val pop = hourly?.optJSONArray("precipitation_probability")?.optDouble(idx, Double.NaN) ?: Double.NaN
        if (!pop.isNaN()) current.put("pop", pop)

        // Presión (pressure_msl)
        val pressure = hourly?.optJSONArray("surface_pressure")?.optDouble(idx, Double.NaN) ?: Double.NaN
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
        val isDay = if (currentTimeIso.isNotBlank() && sunrise != null && sunset != null) {
            try {
                val nowMillis = parseTimeMs(currentTimeIso) ?: Instant.now().toEpochMilli()
                val sr = parseTimeMs(sunrise) ?: nowMillis
                val ss = parseTimeMs(sunset) ?: nowMillis
                nowMillis in (sr..ss)
            } catch (_: Exception) {
                true
            }
        } else true
        current.put("is_day", isDay)

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
                val dewPointArr = hourly.optJSONArray("dewpoint_2m")
                val precipArr = hourly.optJSONArray("precipitation")
                val popArr = hourly.optJSONArray("precipitation_probability")
                val cloudArr = hourly.optJSONArray("cloud_cover")
                val cloudLowArr = hourly.optJSONArray("cloud_cover_low")
                val cloudMidArr = hourly.optJSONArray("cloud_cover_mid")
                val cloudHighArr = hourly.optJSONArray("cloud_cover_high")
                val wsArr = hourly.optJSONArray("wind_speed_10m")
                val wdArr = hourly.optJSONArray("wind_direction_10m")
                val gustArr = hourly.optJSONArray("wind_gusts_10m")
                val presArr = hourly.optJSONArray("surface_pressure")
                val swArr = hourly.optJSONArray("shortwave_radiation")
                val isDayArr = hourly.optJSONArray("id_day")
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
                    val isDayV = isDayArr?.optBoolean(i) ?: false
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
            val anchorNowMs = Instant.now().toEpochMilli()

            // Construir HydroData a partir de muestras
            val hydroDataForScoring = HydroData(
                waterLevel = hydroSamples.firstOrNull { it.parameter?.contains("level", true) == true }?.value,
                waterFlow = hydroSamples.firstOrNull { val p = it.parameter?.lowercase(); p != null && (p.contains("discharge") || p.contains("flow") || p.contains("caudal")) }?.value,
                waterTemp = hydroSamples.firstOrNull { it.parameter?.lowercase()?.contains("temp") == true }?.value
            )

            // Estimar serie de temperaturas de agua
            val estimatedWaterTemps = ActivityScoring.estimateWaterTempSeries(weatherHours, hydroDataForScoring.waterTemp)

            // Calcular scoring por hora
            val forecastsArr = JSONArray()
            val itemsForWindows = mutableListOf<Pair<String, Double>>()
            val debugScoringArr = JSONArray()

            for (i in weatherHours.indices) {
                val weatherPoint = weatherHours[i]
                val derived = computeDerivedFeatures(weatherHours, i)

                // decidir waterTemp para scoring: ±2h rule
                val pointMs = parseTimeMs(weatherPoint.time) ?: anchorNowMs
                val horizonAbsHours = abs(pointMs - anchorNowMs).toDouble() / (60.0 * 60.0 * 1000.0)
                val waterTempForScoring = if (horizonAbsHours <= 2.0 && hydroDataForScoring.waterTemp != null) {
                    hydroDataForScoring.waterTemp
                } else {
                    estimatedWaterTemps.getOrNull(i)
                }

                val ctx = ScoringContextMs(
                    nowMs = pointMs,
                    anchorNowMs = anchorNowMs,
                    lat = lat,
                    derived = derived,
                    waterTemp = waterTempForScoring
                )

                val args = ActivityCalculateArgs(
                    weather = weatherPoint,
                    astro = AstroData(
                        sunrise = astro.optString("sunrise", Instant.now().toString()),
                        sunset = astro.optString("sunset", Instant.now().toString()),
                        moonIllumination = if (astro.has("moon_illumination")) astro.optDouble("moon_illumination") else null,
                        moonPhase = if (astro.has("moon_phase")) astro.optDouble("moon_phase") else 0.5
                    ),
                    hydro = hydroDataForScoring,
                    marine = null,
                    mode = FishingMode.carpfishing,
                    // mapear el species (String) recibido en la función a la enum local
                    species = when (species.lowercase(Locale.getDefault())) {
                        "carp", "carpa" -> FishSpecies.carp
                        "barbel", "barbo" -> FishSpecies.barbel
                        "bass", "blackbass" -> FishSpecies.bass
                        "pike", "lucio" -> FishSpecies.pike
                        "catfish", "siluro" -> FishSpecies.catfish
                        else -> FishSpecies.general
                    },
                    context = ctx
                )

                val activityScore = try {
                    ActivityScoring.calculate(args)
                } catch (e: Exception) {
                    // fallback mínimo
                    ActivityScore(
                        overall = 50,
                        factors = ActivityScore.Factors(50, 50, weatherPoint.pressure, weatherPoint.windSpeed, 50.0, 0),
                        reasons = listOf("Scoring failed"),
                        bestWindows = emptyList(),
                        recommendation = "N/A",
                        confidence = 0.65,
                        breakdown = ScoreBreakdown(
                            subscores = ScoreBreakdown.Subscores(50,50,null,null),
                            meteoModel = null,
                            weightsUsed = ScoreBreakdown.WeightsUsed(0.0,0.0,0.0,0.0),
                            bioMultipliers = ScoreBreakdown.BioMultipliers(1.0,1.0,1.0,1.0)
                        )
                    )
                }

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
                     put("temperature", weatherPoint.temperature)
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

                fObj.put("astronomy", astro)
                fObj.put("hydro", JSONObject().apply {
                    put("waterLevel", hydroDataForScoring.waterLevel ?: JSONObject.NULL)
                    put("waterFlow", hydroDataForScoring.waterFlow ?: JSONObject.NULL)
                    put("waterTemp", hydroDataForScoring.waterTemp ?: JSONObject.NULL)
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
                    put("confidence", activityScore.confidence)
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
                java.time.LocalDate.ofInstant(Instant.ofEpochMilli(anchorNowMs), java.time.ZoneId.systemDefault())
            } catch (_: Exception) {
                java.time.LocalDate.now()
            }

            val itemsForToday = itemsForWindows.filter { pair ->
                val timeStr = pair.first
                val tMs = parseTimeMs(timeStr) ?: return@filter false
                val ld = java.time.LocalDate.ofInstant(Instant.ofEpochMilli(tMs), java.time.ZoneId.systemDefault())
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
                put("mode", "carpfishing")
                put("species", species)
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

        } catch (_: Exception) {
            // si algo falla en la construcción avanzada, dejamos el comportamiento previo
        }

        return root
    }

    // computeDerivedFeatures similar to server TS implementation
    private fun computeDerivedFeatures(weatherData: List<WeatherData>, index: Int): DerivedWeatherFeatures {
        val w = weatherData.getOrNull(index) ?: return DerivedWeatherFeatures()
        val prev1 = if (index - 1 >= 0) weatherData[index - 1] else null
        val prev3 = if (index - 3 >= 0) weatherData[index - 3] else null

        val deltaPressure1h = prev1?.let { w.pressure - it.pressure }
        val deltaTemp1h = prev1?.let { w.temperature - it.temperature }

        var deltaPressure3hAvg: Double? = null
        if (prev3 != null && index - 1 >= 0) {
            val deltas = mutableListOf<Double>()
            for (i in (index - 3)..index) {
                if (i - 1 < 0) continue
                val cur = weatherData[i]
                val prev = weatherData[i - 1]
                deltas.add(cur.pressure - prev.pressure)
            }
            if (deltas.isNotEmpty()) deltaPressure3hAvg = deltas.sum() / deltas.size
        }

        fun sumPrecip(fromInclusive: Int, toInclusive: Int): Double {
            var sum = 0.0
            for (i in maxOf(0, fromInclusive) .. minOf(weatherData.size - 1, toInclusive)) {
                sum += weatherData.getOrNull(i)?.precipitation ?: 0.0
            }
            return sum
        }

        val rainPrev6h = if (index - 1 >= 0) sumPrecip(index - 6, index - 1) else null
        val rainSum24h = if (index - 1 >= 0) sumPrecip(index - 24, index - 1) else null

        var windStability3h: Double? = null
        if (index - 2 >= 0) {
            val speeds = listOf(
                weatherData[index - 2].windSpeed,
                weatherData[index - 1].windSpeed,
                weatherData[index].windSpeed
            )
            val avg = speeds.sum() / speeds.size
            val variance = speeds.fold(0.0) { acc, v -> acc + (v - avg) * (v - avg) } / speeds.size
            val std = kotlin.math.sqrt(variance)
            windStability3h = kotlin.math.max(0.0, kotlin.math.min(1.0, 1 - std / kotlin.math.max(1.0, avg)))
        }

        return DerivedWeatherFeatures(
            deltaPressure1h = deltaPressure1h,
            deltaPressure3hAvg = deltaPressure3hAvg,
            deltaTemp1h = deltaTemp1h,
            rainPrev6h = rainPrev6h,
            rainSum24h = rainSum24h,
            windStability3h = windStability3h
        )
    }
}
