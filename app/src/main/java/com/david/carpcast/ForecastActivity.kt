// kotlin
package com.david.carpcast

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ExperimentalMaterial3Api
import com.david.carpcast.scoring.*
import com.david.carpcast.scoring.WeatherData
import com.david.carpcast.ui.SimpleForecast
import com.david.carpcast.ui.HourlyEntry
import com.david.carpcast.ui.ForecastHost
import com.david.carpcast.ui.theme.CarpCastTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import org.json.JSONObject
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ForecastActivity : ComponentActivity() {
    companion object {
        const val EXTRA_FORECAST_JSON = "forecast_json"
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val json = intent?.getStringExtra(EXTRA_FORECAST_JSON) ?: "{}"

        // Estado compartido entre Activity y Compose para mostrar transición de carga
        val forecastState: MutableState<SimpleForecast?> = mutableStateOf(null)
        // Estado que controla el banner de carga cuando el usuario pulsa el botón "refrescar"
        val loadingState: MutableState<Boolean> = mutableStateOf(false)

        // Acción para refrescar el forecast desde la UI: activa loadingState y recalcula en background
        val onRefreshLambda: () -> Unit = {
            // Solo iniciar si no hay ya una carga en curso
            if (!loadingState.value) {
                loadingState.value = true
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        val score = parseAndScore(json)
                        val forecastModel = mapScoreToSimpleForecast(json, score)
                        runOnUiThread {
                            forecastState.value = forecastModel
                        }
                    } catch (_: Exception) {
                        // ignore errors; keep previous forecast
                    } finally {
                        runOnUiThread { loadingState.value = false }
                    }
                }
            }
        }

        // Montar la UI inmediatamente: no mostramos el banner por defecto, solo cuando se pulse refresh
        setContent {
            CarpCastTheme {
                ForecastHost(forecastState = forecastState, loadingState = loadingState, onBack = { finish() }, onRefresh = onRefreshLambda)
            }
        }

        // Ejecutar parse + scoring inicial en background y actualizar el estado cuando esté listo
        CoroutineScope(Dispatchers.Default).launch {
            val score = parseAndScore(json)
            val forecastModel = mapScoreToSimpleForecast(json, score)
            runOnUiThread {
                forecastState.value = forecastModel
            }
        }
    }

    // kotlin
    @RequiresApi(Build.VERSION_CODES.O)
    private fun parseAndScore(json: String): ActivityScore {
        try {
            val root = JSONObject(json)
            // Intenta sacar un bloque "current" o el primer "hourly"
            val current = when {
                root.has("current") -> root.getJSONObject("current")
                root.has("hourly") -> root.getJSONArray("hourly").optJSONObject(0)
                else -> null
            }

            val weather = if (current != null) {
                WeatherData(
                    time = current.optString("time", Instant.now().toString()),
                    temperature = current.optDouble("temp", 15.0),
                    dewPoint = current.optDouble("dew_point", Double.NaN),
                    humidity = current.optDouble("humidity", 75.0),
                    windSpeed = current.optDouble("wind_speed", 5.0),
                    gustSpeed = current.optDouble("gust", Double.NaN).takeIf { !it.isNaN() },
                    precipitation = current.optDouble("rain", current.optDouble("precip", 0.0)),
                    precipitationProbability = current.optDouble("pop", Double.NaN)
                        .takeIf { !it.isNaN() },
                    pressure = current.optDouble("pressure", 1015.0),
                    cloudCover = current.optDouble("clouds", Double.NaN),
                    cloudCoverLow = current.optDouble("clouds_low", Double.NaN),
                    cloudCoverMid = current.optDouble("clouds_mid", Double.NaN),
                    cloudCoverHigh = current.optDouble("clouds_high", Double.NaN),
                    shortwaveRadiation = current.optDouble("shortwave_radiation", Double.NaN)
                        .takeIf { !it.isNaN() },
                    uvIndex = current.optDouble("uvi", Double.NaN).takeIf { !it.isNaN() },
                    isDay = if (current.has("is_day")) current.optBoolean("is_day") else true
                )
            } else {
                // fallback mínimo
                WeatherData(
                    time = Instant.now().toString(),
                    temperature = 15.0,
                    dewPoint = Double.NaN,
                    humidity = 75.0,
                    windSpeed = 5.0,
                    gustSpeed = null,
                    precipitation = 0.0,
                    precipitationProbability = null,
                    pressure = 1015.0,
                    cloudCover = 0.0,
                    cloudCoverLow = null,
                    cloudCoverMid = null,
                    cloudCoverHigh = null,
                    shortwaveRadiation = null,
                    uvIndex = null,
                    isDay = true
                )
            }

//
//            val astroObj = root.optJSONObject("astro") ?: JSONObject()
//                .put("sunrise", Instant.now().toString())
//                .put("sunset", Instant.now().toString())
//            val astro = AstroData(
//                sunrise = astroObj.optString("sunrise", Instant.now().toString()),
//                sunset = astroObj.optString("sunset", Instant.now().toString()),
//                moonPhase = astroObj.optDouble("moonPhase", Double.NaN),
//                moonIllumination = astroObj.optDouble("moonIllumination", Double.NaN)
//                    .takeIf { !it.isNaN() }
//            )
//
//            val hydroObj = root.optJSONObject("hydro")
//            val hydro = hydroObj?.let {
//                HydroData(
//                    waterLevel = it.optDouble("level", Double.NaN).takeIf { v -> !v.isNaN() },
//                    waterFlow = it.optDouble("flow", Double.NaN).takeIf { v -> !v.isNaN() },
//                    waterTemp = it.optDouble("waterTemp", Double.NaN).takeIf { v -> !v.isNaN() }
//                )
//            }

//            val weather = extractWeatherArrays(root)

            val astroInfo = extractAstroInfo(root)
            val astro = AstroData(
                sunrise = astroInfo.sunrise,
                sunset = astroInfo.sunset,
                moonIllumination = astroInfo.moonIllum,
                moonPhase = astroInfo.moonPhase ?: Double.NaN
            )

            // Reconstruir HydroData si existe: priorizar objeto JSON directo, si no intentar parsear hydrology
            val hydroInfo = extractHydroText(root)
            val hydroObj = root.optJSONObject("hydro")
                ?: hydroInfo.parsedTimeJson
            val hydro = hydroObj?.let {
                val daily = it.optJSONObject("daily")
                val riverDischargeArray = daily?.optJSONArray("river_discharge")
                val first = riverDischargeArray?.optDouble(0, Double.NaN) ?: Double.NaN
                 HydroData(
                     waterLevel = it.optDouble("level", Double.NaN).takeIf { v -> !v.isNaN() },
                     waterFlow = first.takeIf { v -> !v.isNaN() },
                     waterTemp = it.optDouble("waterTemp", Double.NaN).takeIf { v -> !v.isNaN() }
                 )
             }

            // Extraer mode/species/anchorNow/lat/waterTemp desde JSON
            val modeStr = root.optString("mode", "carpfishing")
            val modeEnum = when (modeStr.lowercase(Locale.ROOT)) {
                "predator" -> FishingMode.predator
                else -> FishingMode.carpfishing
            }
            val speciesStr = root.optString("species", "general")
            val speciesEnum = when (speciesStr.lowercase(Locale.ROOT)) {
                "carp" -> FishSpecies.carp
                "barbel" -> FishSpecies.barbel
                "bass" -> FishSpecies.bass
                "pike" -> FishSpecies.pike
                "catfish" -> FishSpecies.catfish
                else -> FishSpecies.general
            }
            val anchorNowStr = root.optString("anchorNow", "")
            val anchorNow = if (anchorNowStr.isBlank()) null else parseIso(anchorNowStr)
            val lat = root.optDouble("lat", 0.0)
            val waterTempOverride = if (root.has("waterTemp")) {
                val v = root.optDouble("waterTemp", Double.NaN)
                if (v.isNaN()) null else v
            } else null

            // Construir ScoringContext: ahora = anchorNow ?: weather.time
            val nowDate = anchorNow ?: parseIso(weather.time)
            val nowInstant = Instant.ofEpochMilli(nowDate.time)
            val anchorInstant = anchorNow?.let { Instant.ofEpochMilli(it.time) }
            val context = ScoringTsPort.ScoringContextInstant(
                now = nowInstant,
                anchorNow = anchorInstant,
                lat = lat,
                derived = null,
                waterTemp = waterTempOverride
            )

            // Llamada a calculate pasando UN solo objeto de argumentos (usar la clase de argumentos existente)
            val argsDos = ScoringTsPort.CalculateArgsInstant(
               weather = weather,
                astro = astro,
                hydro = hydro,
                marine = null,
                mode = modeEnum,
                species = speciesEnum,
                context = context
            )

            return ScoringTsPort.calculate(argsDos)
        } catch (_: Exception) {
            // Fallback: construir objetos mínimos y usar ActivityScoring.calculate para no inventar estructuras
            val fallbackWeather = WeatherData(
                time = Instant.now().toString(),
                temperature = 15.0,
                dewPoint = Double.NaN,
                humidity = 75.0,
                windSpeed = 5.0,
                gustSpeed = null,
                precipitation = 0.0,
                precipitationProbability = null,
                pressure = 1015.0,
                cloudCover = 0.0,
                cloudCoverLow = null,
                cloudCoverMid = null,
                cloudCoverHigh = null,
                shortwaveRadiation = null,
                uvIndex = null,
                isDay = true
            )
            val fallbackAstro = AstroData(
                sunrise = Instant.now().toString(),
                sunset = Instant.now().toString(),
                moonPhase = 0.0,
                moonIllumination = null
            )
            val fallbackContext = ScoringTsPort.ScoringContextInstant(
                now = Instant.ofEpochMilli(Date().time),
                anchorNow = null,
                lat = 0.0,
                derived = null,
                waterTemp = null
            )

            val args = ScoringTsPort.CalculateArgsInstant(
                weather = fallbackWeather,
                astro = fallbackAstro,
                hydro = null,
                marine = null,
                context = fallbackContext
            )

            return ScoringTsPort.calculate(args)
        }
    }

    private fun sumPrecipitation(root: JSONObject): Double {
        val arr = root.optJSONObject("raw_api")
            ?.optJSONObject("hourly")
            ?.optJSONArray("precipitation")
            ?: return 0.0

        var sum = 0.0
        for (i in 0 until 24) {
            sum += arr.optDouble(i, 0.0) // maneja Int/Double/null/strings numéricas
        }
        return sum
    }

    // Helpers para separar extracción de astro / hydrology / arrays meteorológicos
    private data class AstroInfo(
        val sunrise: String,
        val sunset: String,
        val moonPhase: Double?, // 0..1 fraction
        val moonIllum: Double?
    )

    // Nuevo data class para encapsular datos de hidrología extraídos
    private data class HydroInfo(
        val summary: String,
        val value: Double?,
        val unit: String?,
        val parsedTimeJson: JSONObject?
    )

     private data class WeatherArrays(
         val humidityArray: JSONArray?,
         val cloudArray: JSONArray?,
         val tempArray: JSONArray?,
         val windAvg: JSONArray?,
         val windGusts: JSONArray?,
         val pressureArray: JSONArray?
     )

    private fun extractAstroInfo(root: JSONObject): AstroInfo {
         val astro = root.optJSONObject("astro")
         val sunrise = astro?.optString("sunrise") ?: root.optString("sunrise", "—")
         val sunset = astro?.optString("sunset") ?: root.optString("sunset", "—")
         // Intenta leer moon phase como número o texto y convertir a fracción 0..1
         var phaseFraction: Double? = null

         // 1) Si la API ya da un número
         val pNum = astro?.optDouble("moonPhase", Double.NaN)
         if (pNum != null && !pNum.isNaN()) {
            phaseFraction = when {
                pNum > 1.0 && pNum <= 100.0 -> pNum / 100.0
                else -> pNum
            }
         } else {
            // 2) intentar leer campos textuales comunes
            val candidates = listOf("moon_phase", "moonPhase", "curphase", "phase")
            var s: String? = null
            for (c in candidates) {
                val tmp = astro?.optString(c)
                if (!tmp.isNullOrBlank()) { s = tmp; break }
            }
            if (!s.isNullOrBlank()) {
                val ss = s.trim()
                // Si tiene porcentaje "75%"
                if (ss.endsWith("%")) {
                    val num = ss.dropLast(1).replace("%", "").replace(" ", "")
                    phaseFraction = num.toDoubleOrNull()?.let { v -> (v.coerceIn(0.0,100.0))/100.0 }
                } else {
                    // Try parse as double string
                    val parsed = ss.toDoubleOrNull()
                    if (parsed != null) {
                        phaseFraction = if (parsed > 1.0 && parsed <= 100.0) parsed/100.0 else parsed
                    } else {
                        // Map common phase names to approximate fraction
                        val name = ss.lowercase(Locale.ROOT)
                        val map = mapOf(
                            "new moon" to 0.0,
                            "waxing crescent" to 0.125,
                            "first quarter" to 0.25,
                            "waxing gibbous" to 0.375,
                            "full moon" to 0.5,
                            "waning gibbous" to 0.625,
                            "last quarter" to 0.75,
                            "third quarter" to 0.75,
                            "waning crescent" to 0.875
                        )
                        phaseFraction = map[name]
                    }
                }
            }
        }

        val moonIllum = astro?.optDouble("moonIllumination", Double.NaN)?.takeIf { !it.isNaN() }
        return AstroInfo(
            sunrise = sunrise,
            sunset = sunset,
            moonPhase = phaseFraction,
            moonIllum = moonIllum
        )
    }

    // Ahora devuelve un objeto con más información para su uso en la UI y en el scoring
    private fun extractHydroText(root: JSONObject): HydroInfo {
        try {
            val hydrologyArray = root.optJSONArray("hydrology") ?: return HydroInfo("N/A", null, null, null)
            if (hydrologyArray.length() == 0) return HydroInfo("N/A", null, null, null)
            val hydrologyObj = hydrologyArray.optJSONObject(0) ?: return HydroInfo("N/A", null, null, null)
            val timeString = hydrologyObj.optString("time", "")
            if (timeString.isBlank()) return HydroInfo("N/A", null, null, null)
            val parsedTimeJson = try { JSONObject(timeString) } catch (_: Exception) { null }
            if (parsedTimeJson == null) return HydroInfo("N/A", null, null, null)
            val daily = parsedTimeJson.optJSONObject("daily") ?: return HydroInfo("N/A", null, null, parsedTimeJson)
            val riverDischargeArray = daily.optJSONArray("river_discharge") ?: return HydroInfo("N/A", null, null, parsedTimeJson)
            val dailyUnits = parsedTimeJson.optJSONObject("daily_units")
            val unit = dailyUnits?.optString("river_discharge") ?: ""
            val first = riverDischargeArray.optDouble(0, Double.NaN)
            if (first.isNaN()) return HydroInfo("N/A", null, unit, parsedTimeJson)
            val summary = "Caudal: ${"%.2f".format(first)} $unit"
            return HydroInfo(summary, first, unit, parsedTimeJson)
        } catch (_: Exception) {
            return HydroInfo("N/A", null, null, null)
        }
    }

    private fun extractWeatherArrays(root: JSONObject): WeatherArrays {
         val rawApi = root.optJSONObject("raw_api")
         val hourly = rawApi?.optJSONObject("hourly")
        val humidityArray = hourly?.optJSONArray("relative_humidity_2m")
         val cloudArray = hourly?.optJSONArray("cloud_cover")
         val tempArray = hourly?.optJSONArray("temperature_2m")
         val windAvg = hourly?.optJSONArray("wind_speed_10m")
         val windGusts = hourly?.optJSONArray("wind_gusts_10m")
         val pressureArray = hourly?.optJSONArray("surface_pressure")
         return WeatherArrays(
             humidityArray,
             cloudArray,
             tempArray,
             windAvg,
             windGusts,
             pressureArray
         )
     }

     // Mapea ActivityScore + JSON a SimpleForecast que usa la UI
     private fun mapScoreToSimpleForecast(json: String, score: ActivityScore): SimpleForecast {
        val root = try {
            JSONObject(json)
        } catch (_: Exception) {
            JSONObject()
        }

        val lat = root.optDouble("lat", Double.NaN)
        val lon = root.optDouble("lon", root.optDouble("lng", Double.NaN))
        val locationName = when {
            root.has("name") -> root.optString("name", "Desconocido")
            !lat.isNaN() && !lon.isNaN() -> String.format(Locale.US, "%.4f, %.4f", lat, lon)
            else -> "Desconocido"
        }

        val generatedAt =
            SimpleDateFormat("dd/MM/yyyy, HH:mm:ss", Locale.getDefault()).format(Date())

        // Temperaturas: intenta current, luego daily[0], fallback
        val current = root.optJSONObject("current")

        val weatherArrays = extractWeatherArrays(root)
        val humidityArray = weatherArrays.humidityArray
        var humiditySum = 0.0
        for (i in 0 until 24) {
            val v = humidityArray?.optDouble(i, Double.NaN) ?: Double.NaN
            humiditySum += v
        }

        val humidityAvg = humiditySum / 24.0

        val cloudArray = weatherArrays.cloudArray
        var cloudSum = 0.0
        for (i in 0 until 24) {
            val v = cloudArray?.optDouble(i, Double.NaN) ?: Double.NaN
            cloudSum += v
        }

        val cloudAvg = cloudSum / 24.0


        var tempMin = Double.POSITIVE_INFINITY
        var tempMax = Double.NEGATIVE_INFINITY

        val tempArray = weatherArrays.tempArray

        for (i in 0 until 24) {
            val v = tempArray?.optDouble(i, Double.NaN) ?: Double.NaN
            if (!v.isNaN()) {
                if (v < tempMin) tempMin = v
                if (v > tempMax) tempMax = v
            }
        }

        val precipSum = when {
            root.optJSONObject("raw_api")?.optJSONObject("hourly")
                ?.has("precipitation") == true -> sumPrecipitation(root)

            current?.has("rain") == true -> current.optDouble("rain", 0.0)
            else -> root.optDouble("precip", 0.0)
        }

        val windAvg = weatherArrays.windAvg
        val windGusts = weatherArrays.windGusts

        var windAvgMin = 0.0
        var windAvgMax = Double.NEGATIVE_INFINITY
        var windMax = 0.0

        for (i in 0 until 24) {
            val v = windAvg?.optDouble(i, Double.NaN) ?: Double.NaN
            windAvgMin += v
            if (!v.isNaN()) {
                if (v > windAvgMax) windAvgMax = v
            }
        }
        windAvgMin /= 24.0


        for (i in 0 until 24) {
            val v = windGusts?.optDouble(i, Double.NaN) ?: Double.NaN
            if (!v.isNaN()) {
                if (v > windMax) windMax = v
            }
        }

        val pressure = weatherArrays.pressureArray

        var pressureMin = Double.POSITIVE_INFINITY
        var pressureMax = Double.NEGATIVE_INFINITY

        for (i in 0 until 24) {
            val v = pressure?.optDouble(i, Double.NaN) ?: Double.NaN
            if (!v.isNaN()) {
                if (v < pressureMin) pressureMin = v
                if (v > pressureMax) pressureMax = v
            }
        }

        // Best / worst from score.bestWindows if disponible
        // Helper: formatea ISO a HH:mm
        fun isoToHHmm(s: String?): String {
            if (s.isNullOrBlank()) return "—"
            val fmt = DateTimeFormatter.ofPattern("HH:mm")
            try {
                val inst = try { Instant.parse(s) } catch (_: Exception) { null }
                if (inst != null) {
                    val ldt = LocalDateTime.ofInstant(inst, ZoneId.systemDefault())
                    return fmt.format(ldt.toLocalTime())
                }
            } catch (_: Exception) {}
            try {
                val odt = try { java.time.OffsetDateTime.parse(s) } catch (_: Exception) { null }
                if (odt != null) {
                    val local = odt.atZoneSameInstant(ZoneId.systemDefault()).toLocalTime()
                    return fmt.format(local)
                }
            } catch (_: Exception) {}
            // Fallback regex
            val generic = Regex("(\\d{1,2}:\\d{2})").find(s)
            if (generic != null) return generic.groupValues[1].padStart(5, '0')
            return if (s.length >= 5) s.substring(0,5) else s
        }

        // Best / worst from score.bestWindows if disponible (formatear a "HH:mm - HH:mm")
         val bestWindowsList: List<Pair<String, Int>> = try {
             score.bestWindows.map { bw ->
                 val start = bw.start
                 val end = bw.end
                 val range = if (start.contains(" - ") || start.contains("to")) {
                     // ya es texto legible
                     start
                 } else {
                     "${isoToHHmm(start)} - ${isoToHHmm(end)}"
                 }
                 range to bw.score
             }
         } catch (_: Exception) {
             emptyList()
         }
         val itemsForToday = mutableListOf<Pair<String, Double>>()
         val avgConfidence = mutableListOf<Double>()
         val hourlyEntries = mutableListOf<HourlyEntry>()
        // Calcular bestWindows para el día actual (si el JSON incluye forecasts)
        val bestWindowsTodayList: List<Pair<String, Int>> = try {
            val todayLocal = LocalDate.now(ZoneId.systemDefault()).toString() // YYYY-MM-DD local
             val forecastsArr = root.optJSONArray("forecasts")
             if (forecastsArr != null) {
                 for (i in 0 until forecastsArr.length()) {
                     val f = forecastsArr.optJSONObject(i) ?: continue
                     val time = f.optString("time", "")
                     // Intentar parsear el time a LocalDate local; si falla, fallback a startsWith
                    val timeLocalDate = try {
                        val parsed = parseIso(time)
                        val inst = Instant.ofEpochMilli(parsed.time)
                        LocalDateTime.ofInstant(inst, ZoneId.systemDefault()).toLocalDate().toString()
                    } catch (_: Exception) {
                        null
                    }
                    if (timeLocalDate != null) {
                        if (timeLocalDate != todayLocal) continue
                    } else {
                        if (!time.startsWith(todayLocal)) continue
                    }
                     val overall = f.optJSONObject("activity")?.optInt("overall", Int.MIN_VALUE) ?: Int.MIN_VALUE
                     if (overall != Int.MIN_VALUE) itemsForToday.add(time to overall.toDouble())
                     avgConfidence.add(f.optJSONObject("activity")?.optDouble("confidence", Double.MIN_VALUE) ?: Double.MIN_VALUE)
                     // Construir HourlyEntry si es posible
                     try {
                         val weatherObj = f.optJSONObject("weather")
                         val temp = weatherObj?.optDouble("temperature", Double.NaN) ?: f.optDouble("temperature", Double.NaN)
                         val precip = weatherObj?.optDouble("precip", Double.NaN) ?: f.optDouble("precipitation", Double.NaN)
                         val wind = weatherObj?.optDouble("wind_speed", Double.NaN) ?: f.optDouble("windSpeed", Double.NaN)

                         // Intentar extraer dirección del viento desde varios campos comunes
                         val windDirCandidates = listOf(
                             weatherObj?.optDouble("wind_direction", Double.NaN) ?: Double.NaN,
                             weatherObj?.optDouble("wind_deg", Double.NaN) ?: Double.NaN,
                             weatherObj?.optDouble("winddir", Double.NaN) ?: Double.NaN,
                             weatherObj?.optDouble("wind_direction_deg", Double.NaN) ?: Double.NaN,
                             f.optDouble("wind_direction", Double.NaN),
                             f.optDouble("wind_deg", Double.NaN),
                             f.optDouble("winddir", Double.NaN)
                         )
                         val windDirRaw = windDirCandidates.firstOrNull { it.isFinite() } ?: Double.NaN

                         // Probabilidad de precipitación: revisar keys comunes (pop, precipitation_probability)
                         val pop1 = weatherObj?.optDouble("pop", Double.NaN) ?: Double.NaN
                         val pop2 = f.optJSONObject("weather")?.optDouble("pop", Double.NaN) ?: Double.NaN
                         val popVal = when {
                             pop1.isFinite() -> pop1
                             pop2.isFinite() -> pop2
                             else -> Double.NaN
                         }

                         // Nubosidad
                         val cloudVal = weatherObj?.optDouble("clouds", Double.NaN) ?: f.optJSONObject("weather").optDouble("clouds", Double.NaN)

                         // isDay
                         val isDayVal = when {
                             weatherObj?.has("is_day") == true -> weatherObj.optBoolean("is_day")
                             f.has("is_day") -> f.optBoolean("is_day", true)
                             else -> null
                         }

                         // Confidence
                         val confD = f.optJSONObject("activity")?.optDouble("confidence", Double.NaN) ?: Double.NaN
                         val confPct = if (confD.isFinite()) (confD * 100).toInt() else null

                         val tempVal = if (temp.isFinite()) temp else null
                         val precipVal = if (precip.isFinite()) precip else null
                         val windVal = if (wind.isFinite()) wind else null
                         val windDirVal = if (windDirRaw.isFinite()) windDirRaw else null
                         val scoreVal = if (overall != Int.MIN_VALUE) overall else null

                         // Condición textual (si no viene explícita intentar inferirla)
                         val condRaw = weatherObj?.optString("condition")?.takeIf { it.isNotBlank() } ?: f.optString("condition", "").takeIf { it.isNotBlank() }
                         val condition = condRaw ?: run {
                             val isPrecip = (popVal.isFinite() && popVal >= 0.25)
                             when {
                                 isPrecip -> "rain"
                                 (cloudVal.isFinite() && cloudVal >= 60.0) -> "cloudy"
                                 isDayVal == false -> "night"
                                 else -> "clear"
                             }
                         }

                         hourlyEntries.add(
                             HourlyEntry(
                                 time = time,
                                 temperature = tempVal,
                                 precipitation = precipVal,
                                 windSpeed = windVal,
                                 windDirectionDeg = windDirVal,
                                 score = scoreVal,
                                 confidencePct = confPct,
                                 precipitationProbability = if (popVal.isFinite()) popVal else null,
                                 cloudCover = if (cloudVal.isFinite()) cloudVal else null,
                                 isDay = isDayVal,
                                 condition = condition
                             )
                         )
                     } catch (_: Exception) {}
                 }
             }
            // Ordenar la serie horaria por tiempo (si es posible) y limitar a 24
            try {
                hourlyEntries.sortBy { e -> try { parseIso(e.time).time } catch (_: Exception) { Long.MAX_VALUE } }
                if (hourlyEntries.size > 24) {
                    val keep = hourlyEntries.take(24)
                    hourlyEntries.clear()
                    hourlyEntries.addAll(keep)
                }
            } catch (_: Exception) {}
            val bestToday = ScoringTsPort.findBestWindows(itemsForToday)
            bestToday.map { bw -> "${isoToHHmm(bw.start)} - ${isoToHHmm(bw.end)}" to bw.score }
        } catch (_: Exception) {
             emptyList()
         }

         val worstHourTime = itemsForToday.minByOrNull { it.second }?.first?.split("T")?.lastOrNull() ?: "—"
         val worstHourScore = itemsForToday.minByOrNull { it.second }?.second?.toInt() ?: score.overall
         val bestHourTime =
             itemsForToday.maxByOrNull { it.second }?.first?.split("T")?.lastOrNull() ?: "—"
         val  bestHourScore = itemsForToday.maxByOrNull { it.second }?.second?.toInt() ?: score.overall

         val avgConfidencePct = (avgConfidence.average() * 100).toInt()

         val astroInfo = extractAstroInfo(root)
         val moonInfo = when {
            astroInfo.moonPhase != null && astroInfo.moonIllum != null -> {
                val phasePct = (astroInfo.moonPhase * 100.0)
                "Fase: ${"%.0f".format(phasePct)}% · ${"%.0f".format(astroInfo.moonIllum)}%"
            }
            astroInfo.moonIllum != null -> "Iluminación: ${"%.0f".format(astroInfo.moonIllum)}%"
            astroInfo.moonPhase != null -> {
                val phasePct = (astroInfo.moonPhase * 100.0)
                "Fase: ${"%.0f".format(phasePct)}%"
            }
            else -> root.optString("moonInfo", "N/A")
        }

        val hydroInfo = extractHydroText(root)
        val hydroText = hydroInfo.summary

        // Construir otherDays: agrupar forecasts por fecha local (YYYY-MM-DD) y generar SimpleForecasts compactos
        val otherDaysList = mutableListOf<SimpleForecast>()
        try {
            val forecastsArr = root.optJSONArray("forecasts")
            if (forecastsArr != null) {
                // agrupar por fecha local
                val groups = mutableMapOf<String, MutableList<JSONObject>>()
                for (i in 0 until forecastsArr.length()) {
                    val f = forecastsArr.optJSONObject(i) ?: continue
                    val time = f.optString("time", "")
                    val parsedDate = try {
                        val dt = parseIso(time)
                        val local = Instant.ofEpochMilli(dt.time).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                        local
                    } catch (_: Exception) {
                        // fallback: try prefix YYYY-MM-DD
                        if (time.length >= 10) time.substring(0, 10) else ""
                    }
                    if (parsedDate.isBlank()) continue
                    groups.getOrPut(parsedDate) { mutableListOf() }.add(f)
                }

                val todayLocal = LocalDate.now(ZoneId.systemDefault()).toString()
                for ((dateStr, list) in groups) {
                    if (dateStr == todayLocal) continue
                    // calcular métricas agregadas para el día
                    var sumScore = 0.0
                    var countScore = 0
                    var tMin = Double.POSITIVE_INFINITY
                    var tMax = Double.NEGATIVE_INFINITY
                    var precipSumDay = 0.0
                    var cloudSum = 0.0
                    var cloudCount = 0
                    for (f in list) {
                        val overall = f.optJSONObject("activity")?.optInt("overall", Int.MIN_VALUE) ?: Int.MIN_VALUE
                        if (overall != Int.MIN_VALUE) { sumScore += overall.toDouble(); countScore++ }
                        val weatherObj = f.optJSONObject("weather")
                        val temp = weatherObj?.optDouble("temperature", Double.NaN) ?: f.optDouble("temperature", Double.NaN)
                        if (temp.isFinite()) {
                            if (temp < tMin) tMin = temp
                            if (temp > tMax) tMax = temp
                        }
                        val precip = weatherObj?.optDouble("precip", Double.NaN) ?: f.optDouble("precipitation", Double.NaN)
                        if (precip.isFinite()) precipSumDay += precip
                        val cloud = weatherObj?.optDouble("clouds", Double.NaN) ?: f.optDouble("cloud_cover", Double.NaN)
                        if (cloud.isFinite()) { cloudSum += cloud; cloudCount++ }
                    }
                    val dayAvgScore = if (countScore > 0) (sumScore / countScore).toInt().coerceIn(0,100) else score.overall
                    val dayTempMin = if (tMin.isFinite()) tMin else tempMin
                    val dayTempMax = if (tMax.isFinite()) tMax else tempMax
                    val dayCloudAvg = if (cloudCount > 0) cloudSum / cloudCount else cloudAvg
                    val dayPrecip = precipSumDay

                    val displayGenerated = try {
                        // mostrar fecha en formato legible
                        val ld = LocalDate.parse(dateStr)
                        ld.format(DateTimeFormatter.ofPattern("dd/MM"))
                    } catch (_: Exception) {
                        dateStr
                    }

                    // Construir la serie horaria para este día (usar lógica similar a la del día principal)
                    val dailyHourlyEntries = mutableListOf<HourlyEntry>()
                    for (f2 in list) {
                        try {
                            val time = f2.optString("time", "")
                            val weatherObj = f2.optJSONObject("weather")
                            val temp = weatherObj?.optDouble("temperature", Double.NaN) ?: f2.optDouble("temperature", Double.NaN)
                            val precip = weatherObj?.optDouble("precip", Double.NaN) ?: f2.optDouble("precipitation", Double.NaN)
                            val wind = weatherObj?.optDouble("wind_speed", Double.NaN) ?: f2.optDouble("windSpeed", Double.NaN)

                            val windDirCandidates = listOf(
                                weatherObj?.optDouble("wind_direction", Double.NaN) ?: Double.NaN,
                                weatherObj?.optDouble("wind_deg", Double.NaN) ?: Double.NaN,
                                weatherObj?.optDouble("winddir", Double.NaN) ?: Double.NaN,
                                weatherObj?.optDouble("wind_direction_deg", Double.NaN) ?: Double.NaN,
                                f2.optDouble("wind_direction", Double.NaN),
                                f2.optDouble("wind_deg", Double.NaN),
                                f2.optDouble("winddir", Double.NaN)
                            )
                            val windDirRaw = windDirCandidates.firstOrNull { it.isFinite() } ?: Double.NaN

                            val pop1 = weatherObj?.optDouble("pop", Double.NaN) ?: Double.NaN
                            val pop2 = f2.optJSONObject("weather")?.optDouble("pop", Double.NaN) ?: Double.NaN
                            val popVal = when {
                                pop1.isFinite() -> pop1
                                pop2.isFinite() -> pop2
                                else -> Double.NaN
                            }

                            val cloudVal = weatherObj?.optDouble("clouds", Double.NaN) ?: f2.optJSONObject("weather").optDouble("clouds", Double.NaN)

                            val isDayVal = when {
                                weatherObj?.has("is_day") == true -> weatherObj.optBoolean("is_day")
                                f2.has("is_day") -> f2.optBoolean("is_day", true)
                                else -> null
                            }

                            val overall = f2.optJSONObject("activity")?.optInt("overall", Int.MIN_VALUE) ?: Int.MIN_VALUE
                            val confD = f2.optJSONObject("activity")?.optDouble("confidence", Double.NaN) ?: Double.NaN
                            val confPct = if (confD.isFinite()) (confD * 100).toInt() else null

                            val tempVal = if (temp.isFinite()) temp else null
                            val precipVal = if (precip.isFinite()) precip else null
                            val windVal = if (wind.isFinite()) wind else null
                            val windDirVal = if (windDirRaw.isFinite()) windDirRaw else null
                            val scoreVal = if (overall != Int.MIN_VALUE) overall else null

                            val condRaw = weatherObj?.optString("condition")?.takeIf { it.isNotBlank() } ?: f2.optString("condition", "").takeIf { it.isNotBlank() }
                            val condition = condRaw ?: run {
                                val isPrecip = (popVal.isFinite() && popVal >= 0.25)
                                when {
                                    isPrecip -> "rain"
                                    (cloudVal.isFinite() && cloudVal >= 60.0) -> "cloudy"
                                    isDayVal == false -> "night"
                                    else -> "clear"
                                }
                            }

                            dailyHourlyEntries.add(
                                HourlyEntry(
                                    time = time,
                                    temperature = tempVal,
                                    precipitation = precipVal,
                                    windSpeed = windVal,
                                    windDirectionDeg = windDirVal,
                                    score = scoreVal,
                                    confidencePct = confPct,
                                    precipitationProbability = if (popVal.isFinite()) popVal else null,
                                    cloudCover = if (cloudVal.isFinite()) cloudVal else null,
                                    isDay = isDayVal,
                                    condition = condition
                                )
                            )
                        } catch (_: Exception) {}
                    }
                    // ordenar y limitar (mostrar hasta 24 horas)
                    try {
                        dailyHourlyEntries.sortBy { e -> try { parseIso(e.time).time } catch (_: Exception) { Long.MAX_VALUE } }
                        if (dailyHourlyEntries.size > 24) {
                            val keep = dailyHourlyEntries.take(24)
                            dailyHourlyEntries.clear()
                            dailyHourlyEntries.addAll(keep)
                        }
                    } catch (_: Exception) {}

                    // Calcular bestWindows para este día a partir de dailyHourlyEntries
                     val itemsForDay = dailyHourlyEntries.mapNotNull { e -> e.score?.let { Pair(e.time, it.toDouble()) } }
                     val bestWindowsForDay: List<Pair<String, Int>> = try {
                         val bw = ScoringTsPort.findBestWindows(itemsForDay)
                         bw.map { w ->
                             val startText = isoToHHmm(w.start)
                             val endText = isoToHHmm(w.end)
                             ("$startText - $endText") to w.score
                         }
                     } catch (_: Exception) { emptyList() }

                    // Mejor y peor hora del día (usar scores presentes en dailyHourlyEntries)
                    val scoredHours = dailyHourlyEntries.filter { it.score != null }
                    val bestHourlyEntry = scoredHours.maxByOrNull { it.score ?: Int.MIN_VALUE }
                    val worstHourlyEntry = scoredHours.minByOrNull { it.score ?: Int.MAX_VALUE }
                    val bestHourTimeForDay = bestHourlyEntry?.time?.let { isoToHHmm(it) } ?: "—"
                    val bestHourScoreForDay = bestHourlyEntry?.score ?: dayAvgScore
                    val worstHourTimeForDay = worstHourlyEntry?.time?.let { isoToHHmm(it) } ?: "—"
                    val worstHourScoreForDay = worstHourlyEntry?.score ?: dayAvgScore

                    // Confidence media del día (si hay confidencias por hora), fallback al global
                    val confList = dailyHourlyEntries.mapNotNull { it.confidencePct }
                    val dayAvgConfidencePct = if (confList.isNotEmpty()) confList.average().toInt() else avgConfidencePct

                    val compact = SimpleForecast(
                        generatedAt = displayGenerated,
                        locationName = locationName,
                        dayAvg = dayAvgScore,
                        bestHourTime = bestHourTimeForDay,
                        bestHourScore = bestHourScoreForDay,
                        worstHourTime = worstHourTimeForDay,
                        worstHourScore = worstHourScoreForDay,
                        avgConfidencePct = dayAvgConfidencePct,
                        precipSum = dayPrecip,
                        cloudAvg = dayCloudAvg,
                        humidityAvg = humidityAvg,
                        tempMin = dayTempMin,
                        tempMax = dayTempMax,
                        windAvgMin = windAvgMin,
                        windAvgMax = windAvgMax,
                        windMax = windMax,
                        pressureMin = pressureMin,
                        pressureMax = pressureMax,
                        bestWindows = bestWindowsForDay,
                        sunrise = astroInfo.sunrise,
                        sunset = astroInfo.sunset,
                        moonInfo = moonInfo,
                        hydroText = hydroText,
                        hourly = dailyHourlyEntries,
                        otherDays = emptyList()
                    )
                    otherDaysList.add(compact)
                }
                // ordenar por fecha ascendente
                //otherDaysList.sortBy { it.generatedAt }
            }
        } catch (_: Exception) { /* ignore */ }

         return SimpleForecast(
             locationName = locationName,
             generatedAt = generatedAt,
             dayAvg = score.overall.coerceIn(0, 100),
             bestHourTime = bestHourTime,
             bestHourScore = bestHourScore.coerceIn(0, 100),
             worstHourTime = worstHourTime,
             worstHourScore = worstHourScore.coerceIn(0, 100),
             avgConfidencePct = avgConfidencePct,
             tempMin = tempMin,
             tempMax = tempMax,
             precipSum = precipSum,
             humidityAvg = humidityAvg,
             windAvgMin = windAvgMin,
             windAvgMax = windAvgMax,
             windMax = windMax,
             pressureMin = pressureMin,
             pressureMax = pressureMax,
             // Priorizar ventanas del día actual si existen
             bestWindows = if (bestWindowsTodayList.isNotEmpty()) bestWindowsTodayList else bestWindowsList,
             sunrise = astroInfo.sunrise,
             sunset = astroInfo.sunset,
             moonInfo = moonInfo,
             hydroText = hydroText,
             hourly = hourlyEntries,
             cloudAvg = cloudAvg,
             otherDays = otherDaysList
         )
     }

    private fun parseIso(s: String): Date {
        if (s.isBlank()) return Date()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Intentar varios parseos robustos: Instant, OffsetDateTime, LocalDateTime con/ sin segundos, LocalDate
                try {
                    val inst = Instant.parse(s)
                    return Date.from(inst)
                } catch (_: Exception) {
                }
                try {
                    val odt = java.time.OffsetDateTime.parse(s)
                    return Date.from(odt.toInstant())
                } catch (_: Exception) {
                }
                try {
                    val ldt = LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    val z = ldt.atZone(ZoneId.systemDefault()).toInstant()
                    return Date.from(z)
                } catch (_: Exception) {
                }
                try {
                    // aceptar formato sin segundos: yyyy-MM-dd'T'HH:mm
                    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
                    val ldt2 = LocalDateTime.parse(s, fmt)
                    val z2 = ldt2.atZone(ZoneId.systemDefault()).toInstant()
                    return Date.from(z2)
                } catch (_: Exception) {
                }
                try {
                    // aceptar solo fecha yyyy-MM-dd
                    val ld = LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE)
                    val z3 = ld.atStartOfDay(ZoneId.systemDefault()).toInstant()
                    return Date.from(z3)
                } catch (_: Exception) {
                }
                // Fallback: intentar patrones antiguos con UTC
                val patterns = arrayOf(
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    "yyyy-MM-dd'T'HH:mm:ssXXX",
                    "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                    "yyyy-MM-dd'T'HH:mm"
                )
                for (p in patterns) {
                    try {
                        val sdf = SimpleDateFormat(p, Locale.US)
                        sdf.timeZone = TimeZone.getTimeZone("UTC")
                        val parsed = sdf.parse(s)
                        if (parsed != null) return parsed
                    } catch (_: Exception) { /* try next */ }
                }
            } else {
                val patterns = arrayOf(
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    "yyyy-MM-dd'T'HH:mm:ssXXX",
                    "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                    "yyyy-MM-dd'T'HH:mm"
                )
                for (p in patterns) {
                    try {
                        val sdf = SimpleDateFormat(p, Locale.US)
                        sdf.timeZone = TimeZone.getTimeZone("UTC")
                        val parsed = sdf.parse(s)
                        if (parsed != null) return parsed
                    } catch (_: Exception) { /* try next */ }
                }
            }
        } catch (_: Exception) {
        }
        return Date()
    }
 }
