package com.david.carpcast.astronomy

import com.david.carpcast.network.FarmSenseApi
import com.david.carpcast.network.SunriseSunsetApi
import com.david.carpcast.network.USNOApi
import kotlinx.coroutines.withTimeout
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class AstronomyClient(
    private val usnoApi: USNOApi,
    private val sunriseApi: SunriseSunsetApi,
    private val farmSenseApi: FarmSenseApi,
    private val globalTimeoutMs: Long = 8000L
) {

    suspend fun getAstronomy(dateIso: String, lat: Double, lon: Double): AstronomyResult {
        // Normalize date (expecting yyyy-MM-dd or ISO starting with date)
        val dateStr = try {
            if (dateIso.length >= 10) dateIso.substring(0, 10) else dateIso
        } catch (e: Exception) {
            return AstronomyResult.Failure(AstronomyError.Parse("Invalid date format: ${e.message}"))
        }

        // Validate date string
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdfDate.timeZone = TimeZone.getTimeZone("UTC")
        val dateObj: Date = try {
            sdfDate.parse(dateStr) ?: return AstronomyResult.Failure(AstronomyError.Parse("Unable to parse date"))
        } catch (e: ParseException) {
            return AstronomyResult.Failure(AstronomyError.Parse("Invalid date format: ${e.message}"))
        }

        return try {
            withTimeout(globalTimeoutMs) {
                val reasons = mutableListOf<AstronomyError>()

                // Prepare holders for data from each provider
                var usnoSunriseIso: String? = null
                var usnoSunsetIso: String? = null

                var ssSunriseIso: String? = null
                var ssSunsetIso: String? = null

                var moonPhase: String? = null
                var moonIllum: Double? = null

                var usnoSucceeded = false
                var ssSucceeded = false
                var farmSucceeded = false

                // 1) Try USNO (but do NOT return early; collect data). USNO response can vary so we parse JsonElement flexibly.
                try {
                    // Use correct date pattern (lowercase y) to format date for USNO
                    val usnoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(dateObj)
                    val coords = "${lat},${lon}"
                    val json = usnoApi.oneDay(usnoDate, coords)

                    try {
                        // USNO provides structure like: { properties: { data: { sundata: [...], moondata: [...], tz: 0.0, curphase: "...", fracillum: "75%" } } }
                        val root = json.asJsonObject
                        val properties = if (root.has("properties") && root.get("properties").isJsonObject) root.getAsJsonObject("properties") else null
                        val dataObj = if (properties != null && properties.has("data") && properties.get("data").isJsonObject) properties.getAsJsonObject("data") else root

                        // timezone offset in hours (can be fractional), default 0.0
                        val tzOffset = try {
                            if (dataObj.has("tz") && !dataObj.get("tz").isJsonNull) dataObj.get("tz").asDouble else 0.0
                        } catch (_: Exception) { 0.0 }

                        // sun times: look into sundata array
                        if (dataObj.has("sundata") && dataObj.get("sundata").isJsonArray) {
                            val sunArr = dataObj.getAsJsonArray("sundata")
                            var sunriseRaw: String? = null
                            var sunsetRaw: String? = null
                            for (el in sunArr) {
                                if (!el.isJsonObject) continue
                                val o = el.asJsonObject
                                val phen = if (o.has("phen")) o.get("phen").asString else null
                                val time = if (o.has("time")) o.get("time").asString else null
                                if (phen != null && time != null) {
                                    if (phen.contains("Rise", true)) sunriseRaw = time
                                    if (phen.contains("Set", true)) sunsetRaw = time
                                }
                            }
                           if (!sunriseRaw.isNullOrBlank() || !sunsetRaw.isNullOrBlank()) {
                                usnoSunriseIso = sunriseRaw?.let { tryLocalToUtcIso(dateStr, it, tzOffset) }
                                usnoSunsetIso = sunsetRaw?.let { tryLocalToUtcIso(dateStr, it, tzOffset) }
                                usnoSucceeded = true
                            }
                        } else if (dataObj.has("sundata") && dataObj.get("sundata").isJsonObject) {
                            // In case sundata is an object (unexpected), try direct fields
                            val o = dataObj.getAsJsonObject("sundata")
                            val s1 = if (o.has("sunrise")) o.get("sunrise").asString else null
                            val s2 = if (o.has("sunset")) o.get("sunset").asString else null
                            if (!s1.isNullOrBlank() || !s2.isNullOrBlank()) {
                                usnoSunriseIso = s1?.let { tryLocalToUtcIso(dateStr, it, tzOffset) } ?: s1
                                usnoSunsetIso = s2?.let { tryLocalToUtcIso(dateStr, it, tzOffset) } ?: s2
                                usnoSucceeded = true
                            }
                        } else {
                            // fallback: try older keys if present at dataObj root
                            val s1 = if (dataObj.has("sunrise")) dataObj.get("sunrise").asString else null
                            val s2 = if (dataObj.has("sunset")) dataObj.get("sunset").asString else null
                            if (!s1.isNullOrBlank() || !s2.isNullOrBlank()) {
                                usnoSunriseIso = s1?.let { tryLocalToUtcIso(dateStr, it, tzOffset) } ?: s1
                                usnoSunsetIso = s2?.let { tryLocalToUtcIso(dateStr, it, tzOffset) } ?: s2
                                usnoSucceeded = true
                            }
                        }

                        // moon data: curphase, closestphase, fracillum, or moondata array
                        try {
                            if (dataObj.has("closestphase") && dataObj.get("closestphase").isJsonObject) {
                                val cp = dataObj.getAsJsonObject("closestphase")
                                if (cp.has("phase") && !cp.get("phase").isJsonNull) moonPhase = cp.get("phase").asString
                            } else if (dataObj.has("curphase") && !dataObj.get("curphase").isJsonNull) {
                                moonPhase = dataObj.get("curphase").asString
                            }

                            if (dataObj.has("fracillum") && !dataObj.get("fracillum").isJsonNull) {
                                val fracRaw = dataObj.get("fracillum").asString
                                // expected like "75%" -> parse to double 75.0
                                moonIllum = fracRaw.replace("%", "").trim().toDoubleOrNull()
                            }

                            // as extra, moondata array may contain events but not illumination; ignore for now
                        } catch (_: Exception) {
                            // ignore moon parsing errors
                        }

                    } catch (_: Exception) {
                        // fallback: ignore USNO parsing errors
                    }
                } catch (_: Exception) {
                    reasons.add(AstronomyError.Parse("USNO failed"))
                }

                // 2) Sunrise-Sunset (always call, to have alternative sun times)
                try {
                    val ss = sunriseApi.getForDate(lat, lon, dateStr, 0)
                    val res = ss.results
                    if (res != null) {
                        ssSunriseIso = res.sunrise
                        ssSunsetIso = res.sunset
                        ssSucceeded = true
                    } else {
                        reasons.add(AstronomyError.Parse("Sunrise-Sunset returned empty results"))
                    }
                } catch (_: Exception) {
                    reasons.add(AstronomyError.Parse("Sunrise-Sunset failed"))
                }

                // 3) FarmSense for moon data (always call)
                try {
                    val epoch = dateObj.time / 1000L
                    val list = farmSenseApi.getMoonPhase(epoch)
                    val first = list.firstOrNull()
                    if (first != null) {
                        // only set if absent from USNO (prefer USNO's more detailed phase/text)
                        if (moonPhase.isNullOrBlank()) moonPhase = first.phase
                        if (moonIllum == null) moonIllum = first.illumination
                        farmSucceeded = true
                    }
                } catch (_: Exception) {
                    reasons.add(AstronomyError.Parse("FarmSense failed"))
                }

                // Decide which sunrise/sunset to use: prefer USNO when available, otherwise Sunrise-Sunset
                val finalSunrise = usnoSunriseIso ?: ssSunriseIso
                val finalSunset = usnoSunsetIso ?: ssSunsetIso

                // Determine source
                val sourcesUsed = mutableSetOf<AstronomyResult.Source>()
                if (usnoSucceeded && (!usnoSunriseIso.isNullOrBlank() || !usnoSunsetIso.isNullOrBlank())) sourcesUsed.add(AstronomyResult.Source.USNO)
                if (ssSucceeded && (!ssSunriseIso.isNullOrBlank() || !ssSunsetIso.isNullOrBlank())) sourcesUsed.add(AstronomyResult.Source.SUNRISE_SUNSET)
                if (farmSucceeded) sourcesUsed.add(AstronomyResult.Source.FARMSENSE)

                val source = when (sourcesUsed.size) {
                    0 -> null
                    1 -> sourcesUsed.first()
                    else -> AstronomyResult.Source.COMBINED
                }

                // If we have no useful data at all, return Failure
                val hasAnyData = (finalSunrise != null && finalSunset != null) || moonPhase != null || moonIllum != null
                if (!hasAnyData) {
                    return@withTimeout AstronomyResult.Failure(
                        if (reasons.isEmpty()) AstronomyError.Network else AstronomyError.Aggregate(reasons)
                    )
                }

                return@withTimeout AstronomyResult.Success(finalSunrise, finalSunset, moonPhase, moonIllum, source ?: AstronomyResult.Source.COMBINED)
            }
        } catch (_: Exception) {
            // Timeout or other cancellation
            return AstronomyResult.Failure(AstronomyError.Timeout)
        }
    }

    private fun tryLocalToUtcIso(dateStr: String, timeStr: String, tzOffsetHours: Double? = null): String? {
        // If tzOffsetHours is provided, parse the local time using that offset (e.g. 0.0 for UTC, -5.0 for EST)
        val candidates = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd h:mm a", "yyyy-MM-dd h:mm:ss a")
        for (fmt in candidates) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                if (tzOffsetHours != null) {
                    // build timezone ID like GMT+02:00 or GMT-05:30
                    val tz = tzOffsetHours
                    val sign = if (tz >= 0.0) "+" else "-"
                    val absVal = kotlin.math.abs(tz)
                    val hh = absVal.toInt()
                    val mm = ((absVal - hh) * 60).toInt()
                    val tzId = String.format(Locale.US, "GMT%s%02d:%02d", sign, hh, mm)
                    sdf.timeZone = TimeZone.getTimeZone(tzId)
                } else {
                    // try device default timezone first
                    sdf.timeZone = TimeZone.getDefault()
                }

                val combined = "$dateStr $timeStr"
                val parsed: Date = try {
                    sdf.parse(combined) ?: continue
                } catch (_: Exception) {
                    continue
                }

                val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                iso.timeZone = TimeZone.getTimeZone("UTC")
                return iso.format(parsed)
            } catch (_: Exception) {
                // ignore and try next
            }
        }
        return null
    }
}

