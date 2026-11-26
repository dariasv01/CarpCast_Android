package com.david.carpcast.journal

import java.util.UUID

enum class Waterbody { UNKNOWN, RIVER, RESERVOIR, POND }

data class CatchRecord(
    val id: String = UUID.randomUUID().toString(),
    val atMs: Long = System.currentTimeMillis(),
    val species: String = "carp",
    val weightKg: Double? = null,
    val notes: String = ""
)

data class FishingSession(
    val id: String = UUID.randomUUID().toString(),
    val startedAtMs: Long = System.currentTimeMillis(),
    val endedAtMs: Long? = null,
    val dateOnly: Boolean = false,
    val locationName: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val targetSpecies: String = "carp",
    val rods: Int = 1,
    val waterbody: Waterbody = Waterbody.UNKNOWN,
    val depthM: Double? = null,
    val measuredWaterC: Double? = null,
    val notes: String = "",
    // An immutable copy of what was predicted BEFORE the result was known.
    val forecastJson: String? = null,
    val historicalForecastJson: String? = null,
    val catches: List<CatchRecord> = emptyList()
) {
    fun rodHours(): Double? = endedAtMs?.takeIf { !dateOnly && it > startedAtMs }
        ?.let { (it - startedAtMs) / 3_600_000.0 * rods }

    fun catchesPerRodHour(): Double? = rodHours()?.takeIf { it > 0.0 }?.let { catches.size / it }

    fun validate() {
        require(id.isNotBlank() && locationName.isNotBlank())
        require(startedAtMs > 0 && startedAtMs <= System.currentTimeMillis())
        require(endedAtMs == null || endedAtMs > startedAtMs && endedAtMs <= System.currentTimeMillis())
        require((latitude == null) == (longitude == null))
        require(latitude == null || latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude == null || longitude.isFinite() && longitude in -180.0..180.0)
        require(rods in 1..12)
        require(waterbody in Waterbody.entries)
        require(depthM == null || depthM.isFinite() && depthM in 0.0..200.0)
        require(measuredWaterC == null || measuredWaterC.isFinite() && measuredWaterC in 0.0..50.0)
        require(catches.map { it.id }.distinct().size == catches.size)
        catches.forEach {
            require(it.atMs <= System.currentTimeMillis())
            require(it.atMs >= startedAtMs && (endedAtMs == null || it.atMs <= endedAtMs))
            require(it.weightKg == null || it.weightKg.isFinite() && it.weightKg > 0.0)
        }
    }
}

data class JournalArchive(val version: Int = 1, val sessions: List<FishingSession> = emptyList())
data class CachedForecast(val json: String = "", val savedAtMs: Long = 0L)


fun FishingSession.needsHistoricalForecast(): Boolean =
    forecastJson == null && historicalForecastJson == null && latitude != null && longitude != null &&
        startedAtMs < java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
