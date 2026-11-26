package com.david.carpcast.journal

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import org.json.JSONObject
import java.io.File

/** Private on-device storage. Failed reads propagate, so corrupt data is never overwritten as empty. */
class FieldStore(private val context: Context) {
    private val folder = File(context.filesDir, "fieldbook").apply { mkdirs() }
    private val gson = Gson()
    companion object { private val lock = Any() }

    private fun read(name: String): String? {
        val file = AtomicFile(File(folder, name))
        return try { file.openRead().bufferedReader().use { it.readText() } }
        catch (e: java.io.FileNotFoundException) {
            if (file.baseFile.exists() || File(folder, "$name.bak").exists()) throw e
            null
        }
    }

    private fun write(name: String, value: String) {
        val file = AtomicFile(File(folder, name))
        val stream = file.startWrite()
        try {
            stream.write(value.toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
            check(file.readFully().contentEquals(value.toByteArray(Charsets.UTF_8))) { "Stored data verification failed" }
        } catch (e: Exception) {
            runCatching { file.failWrite(stream) }
            throw e
        }
    }

    fun sessions(): List<FishingSession> = synchronized(lock) {
        val text = read("journal.json") ?: return@synchronized emptyList()
        val archive = requireNotNull(gson.fromJson(text, JournalArchive::class.java))
        require(archive.version == 1) { "Unsupported journal version" }
        archive.sessions.forEach { it.validate() }
        archive.sessions.sortedByDescending { it.startedAtMs }
    }

    fun saveSession(session: FishingSession) = synchronized(lock) {
        session.validate()
        val existing = sessions()
        val previous = existing.firstOrNull { it.id == session.id }
        if (previous != null) require(previous.forecastJson == session.forecastJson) { "Forecast snapshot cannot change" }
        if (previous?.historicalForecastJson != null) require(previous.historicalForecastJson == session.historicalForecastJson ||
            session.historicalForecastJson?.let { onlyAddsSpeciesScores(previous.historicalForecastJson, it) } == true) { "Historical reconstruction cannot change" }
        write("journal.json", gson.toJson(JournalArchive(sessions = existing.filterNot { it.id == session.id } + session)))
    }

    fun deleteSession(id: String) = synchronized(lock) {
        write("journal.json", gson.toJson(JournalArchive(sessions = sessions().filterNot { it.id == id })))
    }

    fun cachedForecast(): CachedForecast? = synchronized(lock) {
        read("forecast.json")?.let { requireNotNull(gson.fromJson(it, CachedForecast::class.java)) }
    }

    fun saveForecast(json: String) = synchronized(lock) {
        val root = JSONObject(json)
        require(root.getJSONArray("forecasts").length() > 0)
        write("forecast.json", gson.toJson(CachedForecast(json, System.currentTimeMillis())))
    }

    fun prepareSessionSnapshot(json: String): String = synchronized(lock) {
        require(JSONObject(json).getJSONArray("forecasts").length() > 0)
        val id = java.util.UUID.randomUUID().toString()
        write("snapshot_$id.json", json)
        id
    }

    fun sessionSnapshot(id: String): String = synchronized(lock) {
        require(java.util.UUID.fromString(id).toString() == id)
        requireNotNull(read("snapshot_$id.json"))
    }

    fun exportJournal(): String = synchronized(lock) { gson.toJson(JournalArchive(sessions = sessions())) }

    /** Version 2 includes favorites; version 1 journal-only exports remain importable. */
    fun exportBackup(): String = synchronized(lock) {
        val root = JSONObject()
            .put("format", "CarpCast")
            .put("version", 2)
            .put("exportedAt", java.time.Instant.now().toString())
            .put("sessions", org.json.JSONArray(gson.toJson(sessions())))
        val places = org.json.JSONArray()
        com.david.carpcast.location.SavedFishingLocations.read(context).forEach {
            places.put(JSONObject().put("name", it.name).put("lat", it.latitude).put("lon", it.longitude))
        }
        root.put("locations", places).toString(2)
    }

    fun importBackup(input: java.io.InputStream, includeFavorites: Boolean = false): ImportResult {
        val text = input.bufferedReader(Charsets.UTF_8).use { reader ->
            val result = StringBuilder()
            val buffer = CharArray(8192)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                require(result.length + count <= 32 * 1024 * 1024) { "Backup is too large" }
                result.append(buffer, 0, count)
            }
            result.toString()
        }
        return importBackup(text, includeFavorites)
    }

    fun importBackup(text: String, includeFavorites: Boolean = false): ImportResult = synchronized(lock) {
        require(text.length <= 32 * 1024 * 1024) { "Backup is too large" }
        val root = JSONObject(text.removePrefix("\uFEFF"))
        val version = root.getInt("version")
        require(version == 1 || version == 2) { "Unsupported backup version" }
        if (version == 2) require(root.getString("format") == "CarpCast")
        val incoming = root.getJSONArray("sessions")
        val imported = (0 until incoming.length()).map { index ->
            val value = incoming.getJSONObject(index)
            // Do not accept missing IDs/timestamps that Gson might replace with new defaults.
            require(value.getString("id").isNotBlank())
            require(value.getString("locationName").isNotBlank())
            value.getLong("startedAtMs")
            value.getString("notes")
            value.getString("targetSpecies")
            val catches = value.getJSONArray("catches")
            for (i in 0 until catches.length()) {
                val record = catches.getJSONObject(i)
                require(record.getString("id").isNotBlank())
                record.getString("species")
                record.getString("notes")
                record.getLong("atMs")
            }
            requireNotNull(gson.fromJson(value.toString(), FishingSession::class.java)).also {
                it.validate()
                it.historicalForecastJson?.let { history ->
                    val snapshot = JSONObject(history)
                    require(snapshot.getString("source") == "historical_reanalysis")
                    require(snapshot.getJSONArray("forecasts").length() > 0)
                }
            }
        }
        require(imported.map { it.id }.distinct().size == imported.size) { "Duplicate IDs in backup" }
        val places = if (version == 2 && includeFavorites) root.getJSONArray("locations") else org.json.JSONArray()
        val importedPlaces = (0 until places.length()).map { index ->
            val value = places.getJSONObject(index)
            val name = value.getString("name")
            val lat = value.getDouble("lat")
            val lon = value.getDouble("lon")
            require(name.isNotBlank() && lat.isFinite() && lat in -90.0..90.0 && lon.isFinite() && lon in -180.0..180.0)
            com.david.carpcast.model.LocationModel(name, lat, lon)
        }
        // Validate everything before writing. Local entries win when a backup contains the same ID.
        val existing = sessions()
        val existingIds = existing.map { it.id }.toSet()
        val additions = imported.filterNot { it.id in existingIds }
        val incomingById = imported.associateBy { it.id }
        var historiesAdded = 0
        var capturesAdded = additions.sumOf { it.catches.size }
        var capturesSkipped = 0
        val enriched = existing.map { local ->
            val incomingSession = incomingById[local.id]
            val history = incomingSession?.historicalForecastJson
            if (local.forecastJson == null && local.historicalForecastJson == null && incomingSession != null && history != null &&
                local.latitude == incomingSession.latitude && local.longitude == incomingSession.longitude &&
                local.startedAtMs == incomingSession.startedAtMs && local.targetSpecies == incomingSession.targetSpecies &&
                JSONObject(history).optString("source") == "historical_reanalysis" &&
                local.catches.all { forecastAtCatch(history, it.atMs) != null }) {
                historiesAdded++
                local.copy(historicalForecastJson = history)
            } else if (local.historicalForecastJson != null && history != null && incomingSession != null &&
                local.latitude == incomingSession.latitude && local.longitude == incomingSession.longitude &&
                local.startedAtMs == incomingSession.startedAtMs && local.targetSpecies == incomingSession.targetSpecies) {
                val enrichedHistory = runCatching { mergeHistoricalSpeciesScores(local.historicalForecastJson, history) }.getOrNull()
                if (enrichedHistory != null && enrichedHistory != local.historicalForecastJson && onlyAddsSpeciesScores(local.historicalForecastJson, enrichedHistory)) {
                    historiesAdded++
                    local.copy(historicalForecastJson = enrichedHistory)
                } else local
            } else local
        }.map { local ->
            val existingCatchIds = local.catches.map { it.id }.toSet()
            var restored = local
            for (record in incomingById[local.id]?.catches.orEmpty().filterNot { it.id in existingCatchIds }) {
                val candidate = restored.copy(catches = restored.catches + record)
                if (runCatching { candidate.validate() }.isSuccess) { restored = candidate; capturesAdded++ }
                else capturesSkipped++
            }
            restored
        }
        val journalChanged = additions.isNotEmpty() || historiesAdded > 0 || capturesAdded > 0
        val favorites = com.david.carpcast.location.SavedFishingLocations.read(context)
        val coordinates = favorites.map { it.latitude to it.longitude }.toSet()
        val newPlaces = importedPlaces.distinctBy { it.latitude to it.longitude }
            .filterNot { (it.latitude to it.longitude) in coordinates }
        val prefs = context.getSharedPreferences("carpcast_prefs", Context.MODE_PRIVATE)
        val oldPlaces = prefs.getString("favorites", null)
        val mergedPlaces = org.json.JSONArray()
        (favorites + newPlaces).forEach { mergedPlaces.put(JSONObject().put("name", it.name).put("lat", it.latitude).put("lon", it.longitude)) }
        val oldJournal = read("journal.json")
        try {
            if (journalChanged) write("journal.json", gson.toJson(JournalArchive(sessions = enriched + additions)))
            if (newPlaces.isNotEmpty()) check(prefs.edit().putString("favorites", mergedPlaces.toString()).commit()) { "Unable to save locations" }
        } catch (error: Exception) {
            // Restore the previous journal if updating favorites fails.
            if (journalChanged) runCatching { write("journal.json", oldJournal ?: gson.toJson(JournalArchive())) }.onFailure { error.addSuppressed(it) }
            if (newPlaces.isNotEmpty()) prefs.edit().apply { if (oldPlaces == null) remove("favorites") else putString("favorites", oldPlaces) }.commit()
            throw error
        }
        ImportResult(additions.size, imported.size - additions.size, newPlaces.size, historiesAdded, capturesAdded, capturesSkipped)
    }

}

data class ImportResult(val sessionsAdded: Int, val sessionsSkipped: Int, val locationsAdded: Int, val historiesAdded: Int = 0, val capturesAdded: Int = 0, val capturesSkipped: Int = 0)
