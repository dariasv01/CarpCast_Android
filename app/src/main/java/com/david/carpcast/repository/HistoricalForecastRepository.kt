package com.david.carpcast.repository

import com.david.carpcast.journal.FishingSession
import com.david.carpcast.network.NetworkModule
import com.david.carpcast.network.OpenMeteoApi
import org.json.JSONObject
import org.json.JSONArray
import java.time.Instant
import java.time.ZoneId
import java.io.IOException

class HistoricalForecastRepository {
    suspend fun reconstruct(session: FishingSession): String {
        require(session.forecastJson == null && session.historicalForecastJson == null)
        val lat = requireNotNull(session.latitude)
        val lon = requireNotNull(session.longitude)
        val day = Instant.ofEpochMilli(session.startedAtMs).atZone(ZoneId.systemDefault()).toLocalDate()
        require(day < java.time.LocalDate.now()) { "Only past dates can be reconstructed" }
        val api = NetworkModule.create(NetworkModule.openMeteo, OpenMeteoApi::class.java)
        val response = api.getHistoricalWeather(lat, lon, day.minusDays(7).toString(), day.toString())
        if (!response.isSuccessful) throw IOException("Historical weather: HTTP ${response.code()}")
        val raw = JSONObject(response.body()?.string() ?: throw IOException("Empty historical weather"))
        val root = ForecastRepository().buildForecastJsonReal(lat, lon, session.targetSpecies, raw, day)
        val points = root.getJSONArray("forecasts")
        val selected = JSONArray()
        for (i in 0 until points.length()) {
            val point = points.getJSONObject(i)
            if (point.getString("time").startsWith(day.toString())) selected.put(point)
        }
        require(selected.length() > 0) { "No historical hours for session" }
        for (record in session.catches) {
            require((0 until selected.length()).any { i ->
                val start = com.david.carpcast.util.TimeUtils.parseTimeMs(selected.getJSONObject(i).getString("time")) ?: return@any false
                record.atMs >= start && record.atMs < start + 3_600_000L
            }) { "Historical data does not cover a catch" }
        }
        root.put("forecasts", selected)
        root.put("source", "historical_reanalysis")
        root.put("historicalModel", "ERA5")
        root.put("scoringVersion", "2.0.0")
        root.put("reconstructedAt", Instant.now().toString())
        root.put("name", session.locationName)
        root.put("missingHistoricalInputs", JSONArray(listOf("precipitation_probability", "hydrology", "marine", "visibility", "uv_index")))
        root.remove("raw_api")
        return root.toString()
    }
}
