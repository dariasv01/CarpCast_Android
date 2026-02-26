package com.david.carpcast.scoring

import android.os.Build
import androidx.annotation.RequiresApi
import com.david.carpcast.util.TimeUtils
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Top-level types (epoch-ms variant) usados por ActivityScoring
data class ScoringContextMs(
    val nowMs: Long,
    val anchorNowMs: Long? = null,
    val lat: Double,
    val derived: DerivedWeatherFeatures? = null,
    val waterTemp: Double? = null
)

data class ActivityCalculateArgs(
    val weather: WeatherData,
    val astro: AstroData,
    val hydro: HydroData? = null,
    val marine: MarineData? = null,
    val mode: FishingMode = FishingMode.carpfishing,
    val species: FishSpecies = FishSpecies.general,
    val context: ScoringContextMs? = null
)

private data class MeteoContextMs(
    val nowMs: Long,
    val anchorNowMs: Long? = null,
    val astro: AstroData,
    val waterTemp: Double? = null
)

// ------------------------------------------------------------
// ActivityScoring (equivalente a tu clase TS)
// ------------------------------------------------------------

object ActivityScoring {

    // ---- Species model (W/C) ----
    private val lastMeteoModel: MutableMap<FishSpecies, MeteoModelInfo> = mutableMapOf()

    private fun getLastMeteoModelInfo(species: FishSpecies): MeteoModelInfo? = lastMeteoModel[species]

    @RequiresApi(Build.VERSION_CODES.O)
    fun calculate(args: ActivityCalculateArgs): ActivityScore {
        val weather = args.weather
        val astro = args.astro
        val hydro = args.hydro
        val marine = args.marine
        val mode = args.mode
        val normalizedSpecies = normalizeSpeciesForMode(args.species, mode)

        val reasons = mutableListOf<String>()

        val nowMs = args.context?.nowMs ?: parseTimeMs(weather.time)
        val lat = args.context?.lat ?: 0.0
        val derived = args.context?.derived

        // 1) Subscores
        val meteo = calculateMeteoScoreSpecies(
            weather = weather,
            derived = derived,
            ctx = MeteoContextMs(
                nowMs = nowMs,
                anchorNowMs = args.context?.anchorNowMs,
                astro = astro,
                waterTemp = args.context?.waterTemp
            ),
            species = normalizedSpecies,
            reasons = reasons
        )

        val astroScore = calculateAstroScoreSpecies(astro, nowMs, normalizedSpecies, reasons)
        val hydroScore = hydro?.let { calculateHydroScore(it, reasons) }
        val marineScore = marine?.let { calculateMarineScore(it, reasons) }

        // 2) Combine with renormalized weights (40/30/20/10)
        val baseMeteo = 0.4
        val baseAstro = 0.3
        val baseHydro = if (hydroScore != null) 0.2 else 0.0
        val baseMarine = if (marineScore != null) 0.1 else 0.0
        val sum = baseMeteo + baseAstro + baseHydro + baseMarine

        val weightsUsed = ScoreBreakdown.WeightsUsed(
            meteo = if (sum > 0) baseMeteo / sum else 0.0,
            astro = if (sum > 0) baseAstro / sum else 0.0,
            hydro = if (sum > 0) baseHydro / sum else 0.0,
            marine = if (sum > 0) baseMarine / sum else 0.0
        )

        var combined = 0.0
        combined += weightsUsed.meteo * meteo
        combined += weightsUsed.astro * astroScore
        if (hydroScore != null) combined += weightsUsed.hydro * hydroScore
        if (marineScore != null) combined += weightsUsed.marine * marineScore

        // 3) Biological multipliers (season/spawn/night)
        val waterTemp = args.context?.waterTemp
        val tempProxy = if (waterTemp != null && waterTemp.isFinite()) waterTemp else weather.temperature

        val seasonFactor = seasonFactor(nowMs, lat, normalizedSpecies)
        val spawnPenalty = spawnPenalty(nowMs, lat, tempProxy, normalizedSpecies)
        val nightFactor = nightFactor(nowMs, astro, normalizedSpecies)
        val moonFactor = moonFactor(astro.moonPhase)

        var totalScore = combined * seasonFactor * spawnPenalty * nightFactor * moonFactor
        totalScore = clamp(totalScore, 0.0, 100.0)

        val confidence = calculateConfidenceV2(
            hydro = hydro,
            marine = marine,
            derived = derived,
            hasWaterTemp = (waterTemp != null && waterTemp.isFinite())
        )

        val breakdown = ScoreBreakdown(
            subscores = ScoreBreakdown.Subscores(
                meteo = meteo.roundToInt(),
                astro = astroScore.roundToInt(),
                hydro = hydroScore?.roundToInt(),
                marine = marineScore?.roundToInt()
            ),
            meteoModel = getLastMeteoModelInfo(normalizedSpecies),
            weightsUsed = weightsUsed,
            bioMultipliers = ScoreBreakdown.BioMultipliers(
                seasonFactor = seasonFactor,
                spawnPenalty = spawnPenalty,
                nightFactor = nightFactor,
                moonFactor = moonFactor
            )
        )

        val uniqueReasons = reasons.distinct().take(8)

        // Log detallado para debugging
        try {
            android.util.Log.d("DEBUG_SCORING_SUMMARY", JSONObject().apply {
                put("time", nowMs)
                put("species", normalizedSpecies.name)
                put("meteo", meteo)
                put("astro", astroScore)
                put("hydro", hydroScore ?: JSONObject.NULL)
                put("combined_before_bio", combined)
                put("seasonFactor", seasonFactor)
                put("spawnPenalty", spawnPenalty)
                put("nightFactor", nightFactor)
                put("moonFactor", moonFactor)
                put("totalScore", totalScore)
                put("confidence", confidence)
            }.toString())
        } catch (_: Exception) {
        }

        return ActivityScore(
            overall = totalScore.roundToInt(),
            factors = ActivityScore.Factors(
                weather = meteo.roundToInt(),
                astronomy = astroScore.roundToInt(),
                pressure = weather.pressure,
                wind = weather.windSpeed,
                moon = astro.moonIllumination ?: 50.0,
                hydro = hydroScore?.roundToInt() ?: 0
            ),
            reasons = uniqueReasons,
            bestWindows = emptyList(),
            recommendation = getRecommendation(totalScore),
            confidence = confidence,
            breakdown = breakdown
        )
    }

    // ------------------------------------------------------------
    // Species normalization
    // ------------------------------------------------------------

    private fun normalizeSpeciesForMode(species: FishSpecies, mode: FishingMode): FishSpecies {
        if (species == FishSpecies.general) return FishSpecies.general
        // Si viene una especie "incompatible" con el modo, degradamos a general
        if (mode == FishingMode.carpfishing && (species == FishSpecies.bass || species == FishSpecies.pike || species == FishSpecies.catfish)) {
            return FishSpecies.general
        }
        if (mode == FishingMode.predator && (species == FishSpecies.carp || species == FishSpecies.barbel)) {
            return FishSpecies.general
        }
        return species
    }

    // ------------------------------------------------------------
    // Meteo species model (W/C)
    // ------------------------------------------------------------

    @RequiresApi(Build.VERSION_CODES.O)
    private fun calculateMeteoScoreSpecies(
        weather: WeatherData,
        derived: DerivedWeatherFeatures?,
        ctx: MeteoContextMs,
        species: FishSpecies,
        reasons: MutableList<String>
    ): Double {
        if (species == FishSpecies.general) {
            // Fallback a legacy si no hay especie concreta
            return calculateWeatherScore(weather, reasons)
        }

        val cfg = getSpeciesConfig(species)

        val t = ctx.waterTemp?.takeIf { it.isFinite() } ?: weather.temperature
        val dewPoint = weather.dewPoint?.takeIf { it.isFinite() } ?: estimateDewPointC(weather.temperature, weather.humidity)

        val tdSpread = if (dewPoint != null && t.isFinite()) (t - dewPoint) else null

        val gustRatio = if ((weather.gustSpeed != null && weather.gustSpeed.isFinite()) && weather.windSpeed.isFinite()) {
            weather.gustSpeed / max(5.0, weather.windSpeed)
        } else null

        val cloudLayersNorm = cloudLayersNorm(weather.cloudCoverLow, weather.cloudCoverMid, weather.cloudCoverHigh)
        val dawnNorm = sunWindowNorm(ctx.nowMs, ctx.astro, 90)
        val radiationNorm = radiationNorm(weather.shortwaveRadiation, weather.uvIndex, weather.isDay)

        val dPressure3h = when {
            derived?.deltaPressure3hAvg != null -> derived.deltaPressure3hAvg * 3.0
            else -> derived?.deltaPressure1h
        }

        val wFeatures: Map<String, Double?> = mapOf(
            "wind" to weather.windSpeed,
            "gustRatio" to gustRatio,
            "cloud" to weather.cloudCover,
            "layers" to cloudLayersNorm,
            "precip" to weather.precipitation,
            "pop" to weather.precipitationProbability,
            "pressure" to weather.pressure,
            "ptrend3h" to dPressure3h,
            "temp" to t,
            "rh" to weather.humidity,
            "tdSpread" to tdSpread,
            "radiation" to radiationNorm,
            "dawn" to dawnNorm
        )

        val cFeatures: Map<String, Double?> = mapOf(
            "dP" to derived?.deltaPressure1h,
            "dPmean" to derived?.deltaPressure3hAvg,
            "dT" to derived?.deltaTemp1h,
            "rain" to derived?.rainPrev6h,
            "rainDays" to derived?.rainSum24h,
            "wstab" to derived?.windStability3h
        )

        val wNorm = weightedNorm01(cfg.W, wFeatures, cfg.f)
        val cNorm = weightedNorm01(cfg.C, cFeatures, cfg.f)

        val horizonHours = horizonHoursAhead(ctx)
        val mixUsed = meteoMixForHorizon(species, cfg.meteoMix, horizonHours)

        val meteoNorm = clamp01(mixUsed.w * wNorm + mixUsed.c * cNorm)
        val meteoScore = meteoNorm * 100.0

        lastMeteoModel[species] = MeteoModelInfo(
            wNorm = wNorm,
            cNorm = cNorm,
            mix = Mix(mixUsed.w, mixUsed.c)
        )

        // Razones (top-level)
        reasons += "Especie: ${speciesLabel(species)}"
        if (wNorm >= 0.65) reasons += "Estado actual \"W\" favorable"
        if (cNorm >= 0.65) reasons += "Cambios/Acumulados (C) favorables"
        if (horizonHours > 6.0 && mixUsed.c > cfg.meteoMix.c) reasons += "Pronóstico a futuro: más peso a acumulados (C)"

        return meteoScore
    }

    private fun horizonHoursAhead(ctx: MeteoContextMs): Double {
        val anchor = ctx.anchorNowMs ?: return 0.0
        val diffMs = ctx.nowMs - anchor
        if (!diffMs.isFiniteLong()) return 0.0
        // Solo futuro
        return max(0.0, diffMs / (60.0 * 60.0 * 1000.0))
    }

    // ------------------------------------------------------------
    // Helpers portados (estimateWaterTempSeries, best windows, etc.)
    // ------------------------------------------------------------

    fun estimateWaterTempSeries(weatherHours: List<WeatherData>, seedTempC: Double? = null): List<Double> {
        if (weatherHours.isEmpty()) return emptyList()

        val out = DoubleArray(weatherHours.size)
        var prev = seedTempC?.takeIf { it.isFinite() }
            ?: (weatherHours.firstOrNull()?.temperature?.takeIf { it.isFinite() } ?: 15.0)

        val k = 0.08
        val solarCoeff = 1.5
        val windCoeff = 0.05

        for (i in weatherHours.indices) {
            val h = weatherHours[i]
            val air = if (h.temperature.isFinite()) h.temperature else prev
            val sw = h.shortwaveRadiation?.takeIf { it.isFinite() } ?: 0.0
            val wind = if (h.windSpeed.isFinite()) h.windSpeed else 0.0
            val isDay = h.isDay ?: true

            val dTAir = k * (air - prev)
            val dTSolar = solarCoeff * (sw / 1000.0)
            val dTWind = -windCoeff * wind
            val diurnal = if (isDay) 0.08 else -0.04

            val next = prev + dTAir + dTSolar + dTWind + diurnal
            val delta = clamp(next - prev, -1.5, 1.5)

            prev = clamp(prev + delta, 2.0, 34.0)
            out[i] = (prev * 100.0).roundToInt() / 100.0
        }

        return out.toList()
    }

    // Nueva data class para representar items { time: string; score: number }
    data class TimeScore(val time: String, val score: Double?)

    // Versión que acepta la estructura explícita (como en TS) -> comportamiento igual al snippet TS
    fun findBestWindowsDetails(
        items: List<TimeScore>?,
        windowHours: Int = 3,
        topN: Int = 3
    ): List<ActivityScore.BestWindow> {
        if (items == null || items.isEmpty()) return emptyList()

        data class Tmp(val start: String, val end: String, val score: Double, val startIdx: Int, val endIdx: Int)
        val out = mutableListOf<Tmp>()

        val lastStart = items.size - windowHours
        if (lastStart < 0) return emptyList()
        for (i in 0..lastStart) {
            val slice = items.subList(i, i + windowHours)
            val valid = slice.map { it.score }.filterNotNull().filter { it.isFinite() }
            if (valid.isEmpty()) continue
            val avg = valid.sum() / valid.size
            out += Tmp(slice.first().time, slice.last().time, avg, i, i + windowHours - 1)
        }

        val selected = mutableListOf<Tmp>()
        val candidates = out.sortedWith(compareByDescending<Tmp> { it.score }.thenBy { it.start })

        for (cand in candidates) {
            if (selected.size >= topN) break
            var overlaps = false
            for (sel in selected) {
                if (!(cand.endIdx < sel.startIdx || cand.startIdx > sel.endIdx)) {
                    overlaps = true
                    break
                }
            }
            if (!overlaps) selected += cand
        }

        return selected.map {
            ActivityScore.BestWindow(
                start = it.start,
                end = it.end,
                score = it.score.roundToInt(),
                reason = "Ventana ${windowHours}h"
            )
        }
    }

    // Adaptador para la versión existente que usa Pair<String, Double>
    fun findBestWindows(
        items: List<Pair<String, Double>>,
        windowHours: Int = 3,
        topN: Int = 3
    ): List<ActivityScore.BestWindow> {
        if (items.isEmpty()) return emptyList()
        val conv = items.map { TimeScore(it.first, it.second) }
        return findBestWindowsDetails(conv, windowHours, topN)
    }

    private fun moonFactor(phase: Double): Double {
        if (!phase.isFinite()) return 1.0
        val p = ((phase % 1.0) + 1.0) % 1.0
        val dNew = min(abs(p - 0.0), abs(p - 1.0))
        val dFull = abs(p - 0.5)
        return if (dNew <= 0.12 || dFull <= 0.12) 1.06 else 1.0
    }

    private fun estimateDewPointC(tempC: Double, rhPct: Double): Double? {
        // Magnus formula (approx).
        if (!tempC.isFinite() || !rhPct.isFinite()) return null
        val rh = clamp(rhPct, 1.0, 100.0) / 100.0
        val a = 17.62
        val b = 243.12
        val gamma = (a * tempC) / (b + tempC) + ln(rh)
        return (b * gamma) / (a - gamma)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun sunWindowNorm(nowMs: Long, astro: AstroData, mins: Int = 90): Double {
        val sunriseMs = runCatching { parseTimeMs(astro.sunrise) }.getOrNull()
        val sunsetMs = runCatching { parseTimeMs(astro.sunset) }.getOrNull()
        val ms = mins * 60_000L

        if (sunriseMs != null && abs(nowMs - sunriseMs) <= ms) return 1.0
        if (sunsetMs != null && abs(nowMs - sunsetMs) <= ms) return 1.0
        return 0.0
    }

    private fun radiationNorm(sw: Double?, uv: Double?, isDay: Boolean?): Double {
        if (isDay == false) return 0.5

        if (uv != null && uv.isFinite()) {
            val u = uv
            return when {
                u <= 2 -> 0.9
                u <= 5 -> 0.7
                u <= 7 -> 0.5
                else -> 0.3
            }
        }

        if (sw == null || !sw.isFinite()) return 0.6
        return when {
            sw <= 200 -> 0.9
            sw <= 500 -> 0.7
            sw <= 800 -> 0.5
            else -> 0.35
        }
    }

    private fun cloudLayersNorm(low: Double?, mid: Double?, high: Double?): Double {
        fun score(v: Double?, peak: Double, left0: Double, right0: Double, fallback: Double): Double {
            if (v == null || !v.isFinite()) return fallback
            return tri(v, left0, peak, right0)
        }

        val lowS = score(low, 50.0, 10.0, 90.0, 0.5)
        val midS = score(mid, 50.0, 10.0, 90.0, 0.5)
        val highS = score(high, 40.0, 5.0, 95.0, 0.4)
        return (lowS + midS + highS) / 3.0
    }

    // ------------------------------------------------------------
    // Astro scoring (por especie)
    // ------------------------------------------------------------

    @RequiresApi(Build.VERSION_CODES.O)
    private fun calculateAstroScoreSpecies(astro: AstroData, nowMs: Long, species: FishSpecies, reasons: MutableList<String>): Double {
        // Legacy base: 50 + 30 si está dentro de ±1h de amanecer/atardecer
        val sunriseMs = parseTimeMs(astro.sunrise)
        val sunsetMs = parseTimeMs(astro.sunset)

        val oneHour = 60L * 60L * 1000L
        val isGoldenHour =
            (nowMs in (sunriseMs - oneHour)..(sunriseMs + oneHour)) ||
                    (nowMs in (sunsetMs - oneHour)..(sunsetMs + oneHour))

        var base = 50.0 + if (isGoldenHour) 30.0 else 0.0
        if (isGoldenHour) reasons += "Ventana crepuscular (amanecer/atardecer)"

        val mult = when (species) {
            FishSpecies.bass -> 1.2
            FishSpecies.carp -> 0.9
            FishSpecies.barbel -> 0.85
            FishSpecies.pike -> 0.8
            FishSpecies.catfish -> 0.7
            else -> 1.0
        }

        base = clamp(base * mult, 0.0, 100.0)
        return base
    }

    // ------------------------------------------------------------
    // Bio multipliers
    // ------------------------------------------------------------

    @RequiresApi(Build.VERSION_CODES.O)
    private fun seasonFactor(nowMs: Long, lat: Double, species: FishSpecies): Double {
        if (species == FishSpecies.general) return 1.0
        val month = monthUtc(nowMs) // 1..12
        val northern = lat >= 0
        val m = if (northern) month else ((month + 6 - 1) % 12) + 1

        val isWinter = (m == 12 || m == 1 || m == 2)
        val isSpring = (m in 3..5)
        val isSummer = (m in 6..8)
        val isAutumn = (m in 9..11)

        return when (species) {
            FishSpecies.carp -> when {
                isSummer -> 1.1
                isSpring -> 1.0
                isAutumn -> 0.95
                else -> 0.8
            }
            FishSpecies.barbel -> when {
                isSummer -> 1.05
                isSpring -> 1.0
                isAutumn -> 0.9
                else -> 0.8
            }
            FishSpecies.bass -> when {
                isSummer -> 1.1
                isSpring -> 1.0
                isAutumn -> 0.9
                else -> 0.75
            }
            FishSpecies.pike -> when {
                isAutumn -> 1.1
                isWinter -> 1.0
                isSpring -> 0.95
                else -> 0.85
            }
            FishSpecies.catfish -> when {
                isSummer -> 1.1
                isSpring -> 0.95
                isAutumn -> 0.9
                else -> 0.75
            }
            else -> 1.0
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun spawnPenalty(nowMs: Long, lat: Double, waterTemp: Double, species: FishSpecies): Double {
        if (species == FishSpecies.general) return 1.0
        val month = monthUtc(nowMs)
        val northern = lat >= 0
        val m = if (northern) month else ((month + 6 - 1) % 12) + 1

        fun inRange(t: Double, a: Double, b: Double) = t >= a && t <= b

        return when (species) {
            FishSpecies.carp -> {
                val likelyMonths = m in 5..7
                if (likelyMonths && inRange(waterTemp, 18.0, 24.0)) 0.7 else 1.0
            }
            FishSpecies.barbel -> {
                val likelyMonths = m in 5..7
                if (likelyMonths && inRange(waterTemp, 16.0, 20.0)) 0.75 else 1.0
            }
            FishSpecies.bass -> {
                val likelyMonths = m in 4..6
                if (likelyMonths && inRange(waterTemp, 16.0, 21.0)) 0.75 else 1.0
            }
            FishSpecies.pike -> {
                val likelyMonths = m in 2..4
                if (likelyMonths && inRange(waterTemp, 4.0, 10.0)) 0.8 else 1.0
            }
            FishSpecies.catfish -> {
                val likelyMonths = m in 5..7
                if (likelyMonths && inRange(waterTemp, 20.0, 26.0)) 0.7 else 1.0
            }
            else -> 1.0
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun nightFactor(nowMs: Long, astro: AstroData, species: FishSpecies): Double {
        if (species != FishSpecies.catfish) return 1.0
        val sunriseMs = parseTimeMs(astro.sunrise)
        val sunsetMs = parseTimeMs(astro.sunset)
        val isNight = nowMs < sunriseMs || nowMs > sunsetMs
        return if (isNight) 1.1 else 0.7
    }

    // ------------------------------------------------------------
    // Confidence
    // ------------------------------------------------------------

    private fun calculateConfidenceV2(
        hydro: HydroData?,
        marine: MarineData?,
        derived: DerivedWeatherFeatures?,
        hasWaterTemp: Boolean
    ): Double {
        var confidence = 0.65
        if (hydro != null) confidence += 0.1
        if (marine != null) confidence += 0.1
        if (derived != null && (derived.deltaPressure3hAvg != null || derived.rainPrev6h != null)) confidence += 0.1
        if (hasWaterTemp) confidence += 0.05
        return min(1.0, confidence)
    }

    // ------------------------------------------------------------
    // Legacy scoring pieces (general / hydro / marine)
    // ------------------------------------------------------------

    private fun calculateWeatherScore(weather: WeatherData, reasons: MutableList<String>): Double {
        var score = 0.0

        // Pressure (0-25 points)
        if (weather.pressure in 1010.0..1030.0) {
            score += 25
            reasons += "Presión atmosférica favorable (${fmt1(weather.pressure)} hPa)"
        } else {
            score += 10
            reasons += "Presión atmosférica moderada (${fmt1(weather.pressure)} hPa)"
        }

        // Wind (0-20 points)
        if (weather.windSpeed <= 15.0) {
            score += 20
            reasons += "Viento favorable (${fmt1(weather.windSpeed)} km/h)"
        } else {
            score += 5
            reasons += "Viento fuerte (${fmt1(weather.windSpeed)} km/h)"
        }

        // Precipitation (0-15 points)
        when {
            weather.precipitation == 0.0 -> {
                score += 15
                reasons += "Sin precipitación"
            }
            weather.precipitation < 2.0 -> {
                score += 10
                reasons += "Precipitación ligera"
            }
            else -> {
                score += 2
                reasons += "Precipitación intensa"
            }
        }

        // Temperature (0-15 points)
        if (weather.temperature in 15.0..25.0) {
            score += 15
            reasons += "Temperatura óptima (${fmt1(weather.temperature)}°C)"
        } else {
            score += 8
            reasons += "Temperatura moderada (${fmt1(weather.temperature)}°C)"
        }

        // Cloud cover (0-10 points)
        if (weather.cloudCover <= 50.0) {
            score += 10
            reasons += "Nubosidad favorable"
        } else {
            score += 5
            reasons += "Muy nublado"
        }

        return score
    }

    private fun calculateHydroScore(hydro: HydroData, reasons: MutableList<String>): Double {
        var score = 50.0
        if (hydro.waterLevel != null) { score += 20; reasons += "Datos de nivel de agua disponibles" }
        if (hydro.waterFlow != null) { score += 20; reasons += "Datos de caudal disponibles" }
        if (hydro.waterTemp != null) { score += 10; reasons += "Datos de temperatura del agua disponibles" }
        return min(100.0, score)
    }

    private fun calculateMarineScore(marine: MarineData, reasons: MutableList<String>): Double {
        var score = 30.0
        if (marine.waveHeight <= 1.0) {
            score += 20
            reasons += "Oleaje favorable"
        } else {
            score += 5
            reasons += "Oleaje moderado"
        }
        return min(100.0, score)
    }

    private fun getRecommendation(score: Double): String = when {
        score >= 80.0 -> "Excelente momento para pescar"
        score >= 60.0 -> "Buenas condiciones para pescar"
        score >= 40.0 -> "Condiciones regulares para pescar"
        else -> "No es el momento óptimo para pescar"
    }

    private fun speciesLabel(species: FishSpecies): String = when (species) {
        FishSpecies.carp -> "Carpa"
        FishSpecies.barbel -> "Barbo"
        FishSpecies.bass -> "BlackBass"
        FishSpecies.pike -> "Lucio"
        FishSpecies.catfish -> "Siluro"
        else -> "General"
    }

    // ------------------------------------------------------------
    // Species config (W/C weights + membership functions)
    // ------------------------------------------------------------

    private data class SpeciesConfig(
        val meteoMix: Mix,
        val W: Map<String, Int>,
        val C: Map<String, Int>,
        val f: Map<String, (Double) -> Double>
    )

    private fun getSpeciesConfig(species: FishSpecies): SpeciesConfig {
        require(species != FishSpecies.general) { "general no tiene config de especie" }

        val meteoMix = when (species) {
            FishSpecies.carp -> Mix(0.65, 0.35)
            FishSpecies.barbel -> Mix(0.55, 0.45)
            FishSpecies.bass -> Mix(0.75, 0.25)
            FishSpecies.pike -> Mix(0.6, 0.4)
            FishSpecies.catfish -> Mix(0.7, 0.3)
            else -> Mix(0.65, 0.35)
        }

        val W = when (species) {
            FishSpecies.carp -> mapOf("wind" to 11, "gustRatio" to 4, "cloud" to 8, "layers" to 1, "precip" to 10, "pop" to 2, "pressure" to 11, "ptrend3h" to 10, "temp" to 9, "rh" to 5, "tdSpread" to 4, "radiation" to 4, "dawn" to 3)
            FishSpecies.barbel -> mapOf("wind" to 11, "gustRatio" to 4, "cloud" to 8, "layers" to 1, "precip" to 12, "pop" to 2, "pressure" to 11, "ptrend3h" to 10, "temp" to 10, "rh" to 5, "tdSpread" to 4, "radiation" to 4, "dawn" to 2)
            FishSpecies.bass -> mapOf("wind" to 10, "gustRatio" to 4, "cloud" to 7, "layers" to 1, "precip" to 6, "pop" to 2, "pressure" to 9, "ptrend3h" to 10, "temp" to 12, "rh" to 5, "tdSpread" to 4, "radiation" to 7, "dawn" to 6)
            FishSpecies.pike -> mapOf("wind" to 9, "gustRatio" to 4, "cloud" to 8, "layers" to 1, "precip" to 8, "pop" to 2, "pressure" to 13, "ptrend3h" to 12, "temp" to 9, "rh" to 5, "tdSpread" to 4, "radiation" to 3, "dawn" to 4)
            FishSpecies.catfish -> mapOf("wind" to 9, "gustRatio" to 3, "cloud" to 8, "layers" to 1, "precip" to 9, "pop" to 2, "pressure" to 9, "ptrend3h" to 9, "temp" to 13, "rh" to 5, "tdSpread" to 4, "radiation" to 4, "dawn" to 2)
            else -> emptyMap()
        }

        val C = when (species) {
            FishSpecies.carp -> mapOf("dP" to 4, "dPmean" to 4, "dT" to 3, "rain" to 6, "rainDays" to 4, "wstab" to 2)
            FishSpecies.barbel -> mapOf("dP" to 4, "dPmean" to 4, "dT" to 3, "rain" to 7, "rainDays" to 5, "wstab" to 3)
            FishSpecies.bass -> mapOf("dP" to 4, "dPmean" to 4, "dT" to 4, "rain" to 5, "rainDays" to 3, "wstab" to 2)
            FishSpecies.pike -> mapOf("dP" to 5, "dPmean" to 5, "dT" to 3, "rain" to 5, "rainDays" to 4, "wstab" to 3)
            FishSpecies.catfish -> mapOf("dP" to 4, "dPmean" to 4, "dT" to 3, "rain" to 6, "rainDays" to 5, "wstab" to 2)
            else -> emptyMap()
        }

        val f: Map<String, (Double) -> Double> = mapOf(
            // W (inmediatos)
            "wind" to { w -> tri(w, 0.0, 10.0, 30.0) },
            "gustRatio" to { ratio ->
                when {
                    !ratio.isFinite() -> 0.5
                    ratio <= 1.2 -> 1.0
                    ratio <= 1.5 -> 0.7
                    ratio <= 2.0 -> 0.4
                    else -> 0.2
                }
            },
            "cloud" to { cc ->
                when {
                    !cc.isFinite() -> 0.5
                    cc in 40.0..80.0 -> 1.0
                    cc in 20.0..40.0 -> 0.7
                    cc > 80.0 && cc <= 95.0 -> 0.7
                    else -> 0.3
                }
            },
            "layers" to { norm -> clamp01(norm) },
            "precip" to { mm ->
                when {
                    !mm.isFinite() -> 0.6
                    mm == 0.0 -> 0.7
                    mm <= 1.0 -> 1.0
                    mm <= 3.0 -> 0.6
                    mm <= 6.0 -> 0.35
                    else -> 0.15
                }
            },
            "pop" to { pop ->
                when {
                    !pop.isFinite() -> 0.5
                    pop <= 20.0 -> 0.7
                    pop <= 50.0 -> 0.6
                    pop <= 70.0 -> 0.5
                    else -> 0.35
                }
            },
            "pressure" to { p ->
                when {
                    !p.isFinite() -> 0.5
                    species == FishSpecies.carp -> when {
                        p <= 995.0 -> 0.6
                        p <= 1002.0 -> 0.9
                        p <= 1010.0 -> 1.0
                        p <= 1016.0 -> 0.6
                        else -> 0.4
                    }
                    else -> tri(p, 995.0, 1008.0, 1015.0)
                }
            },
            "ptrend3h" to { d ->
                when {
                    !d.isFinite() -> 0.5
                    species == FishSpecies.carp -> when {
                        d <= -3.0 -> 1.0
                        d <= -1.5 -> 0.9
                        d <= -0.5 -> 0.8
                        d < 0.5 -> 0.65
                        d < 1.5 -> 0.4
                        else -> 0.2
                    }
                    d <= -2.0 -> 0.95
                    d <= -0.5 -> 0.8
                    d < 0.5 -> 0.6
                    d < 2.0 -> 0.4
                    else -> 0.2
                }
            },
            "temp" to { t ->
                when {
                    !t.isFinite() -> 0.5
                    species == FishSpecies.carp -> when {
                        t < 8.0 -> 0.1
                        t < 15.0 -> 0.4
                        t < 18.0 -> 0.7
                        t <= 30.0 -> 1.0
                        t <= 32.0 -> 0.7
                        t <= 34.0 -> 0.4
                        else -> 0.2
                    }
                    species == FishSpecies.barbel -> tri(t, 12.0, 22.0, 30.0)
                    species == FishSpecies.bass -> tri(t, 12.0, 22.0, 30.0)
                    species == FishSpecies.pike -> tri(t, 4.0, 14.0, 22.0)
                    species == FishSpecies.catfish -> tri(t, 14.0, 24.0, 32.0)
                    else -> 0.5
                }
            },
            "rh" to { rh ->
                when {
                    !rh.isFinite() -> 0.5
                    rh in 55.0..85.0 -> 1.0
                    (rh in 40.0..55.0) || (rh > 85.0 && rh <= 95.0) -> 0.7
                    else -> 0.4
                }
            },
            "tdSpread" to { s ->
                when {
                    !s.isFinite() -> 0.5
                    s < 0.0 -> 0.4
                    s <= 2.0 -> 0.8
                    s <= 8.0 -> 1.0
                    s <= 14.0 -> 0.6
                    else -> 0.3
                }
            },
            "radiation" to { norm -> clamp01(norm) },
            "dawn" to { norm -> clamp01(norm) },

            // C (contexto / cambios)
            "dP" to { d ->
                when {
                    !d.isFinite() -> 0.5
                    d <= -2.0 -> 0.95
                    d <= -0.5 -> 0.8
                    d < 0.5 -> 0.6
                    d < 2.0 -> 0.4
                    else -> 0.25
                }
            },
            "dPmean" to { d ->
                when {
                    !d.isFinite() -> 0.5
                    d <= -1.5 -> 0.95
                    d <= -0.5 -> 0.8
                    d < 0.5 -> 0.65
                    d < 1.5 -> 0.45
                    else -> 0.25
                }
            },
            "dT" to { d ->
                when {
                    !d.isFinite() -> 0.5
                    d <= -3.0 -> 0.9
                    d <= -1.0 -> 0.8
                    d <= 1.0 -> 0.6
                    d <= 3.0 -> 0.4
                    else -> 0.3
                }
            },
            "rain" to { mm ->
                when {
                    !mm.isFinite() -> 0.5
                    mm == 0.0 -> 0.4
                    mm <= 2.0 -> 1.0
                    mm <= 10.0 -> 0.85
                    mm <= 30.0 -> 0.6
                    else -> 0.3
                }
            },
            "rainDays" to { mm ->
                when {
                    !mm.isFinite() -> 0.5
                    mm == 0.0 -> 0.5
                    mm <= 5.0 -> 1.0
                    mm <= 20.0 -> 0.85
                    mm <= 50.0 -> 0.6
                    else -> 0.3
                }
            },
            "wstab" to { s -> clamp01(s) }
        )

        return SpeciesConfig(meteoMix = meteoMix, W = W, C = C, f = f)
    }

    // Local helper identical a TS: tramos explícitos por especie
    private fun meteoMixForHorizon(species: FishSpecies, base: Mix, horizonHours: Double): Mix {
        if (species == FishSpecies.general) return base
        val h = horizonHours
        return when (species) {
            FishSpecies.carp -> when {
                h <= 6.0 -> Mix(0.65, 0.35)
                h <= 24.0 -> Mix(0.63, 0.37)
                h <= 72.0 -> Mix(0.55, 0.45)
                else -> Mix(0.50, 0.50)
            }
            FishSpecies.barbel -> when {
                h <= 6.0 -> Mix(0.55, 0.45)
                h <= 24.0 -> Mix(0.53, 0.47)
                h <= 72.0 -> Mix(0.47, 0.53)
                else -> Mix(0.45, 0.55)
            }
            FishSpecies.bass -> when {
                h <= 6.0 -> Mix(0.75, 0.25)
                h <= 24.0 -> Mix(0.73, 0.27)
                h <= 72.0 -> Mix(0.63, 0.37)
                else -> Mix(0.55, 0.45)
            }
            FishSpecies.pike -> when {
                h <= 6.0 -> Mix(0.60, 0.40)
                h <= 24.0 -> Mix(0.58, 0.42)
                h <= 72.0 -> Mix(0.50, 0.50)
                else -> Mix(0.50, 0.50)
            }
            FishSpecies.catfish -> when {
                h <= 6.0 -> Mix(0.70, 0.30)
                h <= 24.0 -> Mix(0.68, 0.32)
                h <= 72.0 -> Mix(0.58, 0.42)
                else -> Mix(0.55, 0.45)
            }
            else -> base
        }
    }

    // ------------------------------------------------------------
    // Weighted norm (0..1) + math & time helpers (faltaban en este archivo)
    // ------------------------------------------------------------

    private fun weightedNorm01(
        weights: Map<String, Int>,
        features: Map<String, Double?>,
        fns: Map<String, (Double) -> Double>
    ): Double {
        var sumW = 0.0
        var sum = 0.0

        for ((k, w) in weights) {
            val raw = features[k] ?: continue
            if (!raw.isFinite()) continue
            val fn = fns[k] ?: continue
            val v = clamp01(fn(raw))
            sumW += w
            sum += w * v
        }

        if (sumW <= 0.0) return 0.5
        return sum / sumW
    }

    private fun tri(x: Double, a: Double, b: Double, c: Double): Double {
        // triangular membership a..b..c
        if (x <= a || x >= c) return 0.0
        if (x == b) return 1.0
        return if (x < b) (x - a) / (b - a) else (c - x) / (c - b)
    }

    private fun clamp01(x: Double): Double = clamp(x, 0.0, 1.0)
    private fun clamp(x: Double, minV: Double, maxV: Double): Double = max(minV, min(maxV, x))

    @RequiresApi(Build.VERSION_CODES.O)
    private fun parseTimeMs(iso: String): Long {
        // Delegate to TimeUtils to handle multiple formats consistently
        return TimeUtils.parseTimeMs(iso) ?: throw java.time.format.DateTimeParseException("Invalid date", iso, 0)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun monthUtc(epochMs: Long): Int {
        val instant = java.time.Instant.ofEpochMilli(epochMs)
        val zdt = java.time.ZonedDateTime.ofInstant(instant, java.time.ZoneOffset.UTC)
        return zdt.monthValue
    }

    private fun fmt1(x: Double): String = String.format(Locale.US, "%.1f", x)

    private fun Double.isFinite(): Boolean = !this.isNaN() && this != Double.POSITIVE_INFINITY && this != Double.NEGATIVE_INFINITY
    private fun Long.isFiniteLong(): Boolean = true
}
