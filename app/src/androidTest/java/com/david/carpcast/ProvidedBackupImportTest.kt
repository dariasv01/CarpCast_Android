package com.david.carpcast
import android.content.Context

import androidx.test.platform.app.InstrumentationRegistry
import com.david.carpcast.journal.FieldStore
import com.david.carpcast.journal.needsHistoricalForecast
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import java.io.File
import org.json.JSONObject

/** Opt-in device maintenance operation. Never imports user data during normal test runs. */
class ProvidedBackupImportTest {
    @Test fun removeImportedFavorites() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("removeImportedFavorites") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = FieldStore(context)
        val backup = File(context.filesDir, "before-favorites-cleanup-${System.currentTimeMillis()}.json")
        backup.writeText(store.exportBackup(), Charsets.UTF_8)
        val source = JSONObject(File(context.filesDir, "import-adaptado.json").readText(Charsets.UTF_8)).getJSONArray("locations")
        val keys = (0 until source.length()).map {
            val place = source.getJSONObject(it)
            Triple(place.getString("name"), place.getDouble("lat"), place.getDouble("lon"))
        }.toSet()
        val prefs = context.getSharedPreferences("carpcast_prefs", Context.MODE_PRIVATE)
        val current = org.json.JSONArray(prefs.getString("favorites", "[]"))
        val retained = org.json.JSONArray()
        var removed = 0
        for (i in 0 until current.length()) {
            val place = current.getJSONObject(i)
            val key = Triple(place.getString("name"), place.getDouble("lat"), place.getDouble("lon"))
            if (key in keys) removed++ else retained.put(place)
        }
        assertTrue(prefs.edit().putString("favorites", retained.toString()).commit())
        File(context.filesDir, "favorites-cleanup-result.json").writeText(JSONObject()
            .put("before", current.length()).put("removed", removed).put("after", retained.length())
            .put("backup", backup.name).toString(2), Charsets.UTF_8)
    }

    @Test fun addHistoricalSpeciesScores() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("addSpeciesScores") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = FieldStore(context)
        File(context.filesDir, "before-species-scores-${System.currentTimeMillis()}.json").writeText(store.exportBackup(), Charsets.UTF_8)
        var count = 0
        kotlinx.coroutines.runBlocking {
            val repository = com.david.carpcast.repository.HistoricalForecastRepository()
            for (session in store.sessions().filter { it.historicalForecastJson != null }) {
                val original = session.historicalForecastJson!!
                if (JSONObject(original).getJSONArray("forecasts").getJSONObject(0).has("activityBySpecies")) continue
                val calculated = repository.reconstruct(session.copy(historicalForecastJson = null))
                val enriched = com.david.carpcast.journal.mergeHistoricalSpeciesScores(original, calculated)
                assertTrue(com.david.carpcast.journal.onlyAddsSpeciesScores(original, enriched))
                store.saveSession(session.copy(historicalForecastJson = enriched))
                count++
            }
        }
        File(context.filesDir, "after-species-scores.json").writeText(store.exportBackup(), Charsets.UTF_8)
        println("HISTORIES_UPDATED=$count")
    }

    @Test fun reconstructMissingHistory() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("reconstructHistory") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = FieldStore(context)
        File(context.filesDir, "before-history-${System.currentTimeMillis()}.json").writeText(store.exportBackup(), Charsets.UTF_8)
        val failures = mutableListOf<String>()
        var added = 0
        kotlinx.coroutines.runBlocking {
            val repository = com.david.carpcast.repository.HistoricalForecastRepository()
            for (session in store.sessions().filter { it.needsHistoricalForecast() }) {
                try {
                    val json = repository.reconstruct(session)
                    store.saveSession(session.copy(historicalForecastJson = json))
                    added++
                    println("HISTORY_SAVED=${session.id}")
                } catch (e: Exception) { failures.add("${session.id}: ${e.message}") }
            }
        }
        val report = JSONObject().put("added", added).put("failures", org.json.JSONArray(failures))
        File(context.filesDir, "history-result.json").writeText(report.toString(2), Charsets.UTF_8)
        assertTrue(failures.toString(), failures.isEmpty())
        val after = store.sessions()
        for (session in after.filter { it.historicalForecastJson != null }) {
            for (record in session.catches) assertNotNull(com.david.carpcast.journal.forecastAtCatch(session.historicalForecastJson, record.atMs))
        }
        File(context.filesDir, "after-history-backup.json").writeText(store.exportBackup(), Charsets.UTF_8)
    }

    @Test fun classifyUnidentifiedCatches() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("classifyUnidentified") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = FieldStore(context)
        File(context.filesDir, "before-species-update-${System.currentTimeMillis()}.json").writeText(store.exportBackup(), Charsets.UTF_8)
        var changed = 0
        for (session in store.sessions()) {
            val updated = session.catches.map {
                if (it.species.isBlank() || it.species == "unknown") { changed++; it.copy(species = "other") } else it
            }
            if (updated != session.catches) store.saveSession(session.copy(catches = updated))
        }
        assertTrue(store.sessions().flatMap { it.catches }.none { it.species.isBlank() || it.species == "unknown" })
        println("CAPTURES_UPDATED=$changed")
    }

    @Test fun importProvidedBackup() {
        val filename = InstrumentationRegistry.getArguments().getString("backupFile")
        assumeTrue("Requires an explicitly supplied backupFile", filename != null)
        require(filename == "import-adaptado.json")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = FieldStore(context)
        val before = store.sessions()
        val safetyCopy = File(context.filesDir, "before-import-${System.currentTimeMillis()}.json")
        safetyCopy.writeText(store.exportBackup(), Charsets.UTF_8)
        val input = File(context.filesDir, filename!!)
        val expected = JSONObject(input.readText(Charsets.UTF_8)).getJSONArray("sessions")
        val result = input.inputStream().use { store.importBackup(it) }
        val after = FieldStore(context).sessions()
        assertTrue(after.map { it.id }.containsAll(before.map { it.id }))
        for (i in 0 until expected.length()) {
            val source = expected.getJSONObject(i)
            val imported = after.single { it.id == source.getString("id") }
            assertEquals(source.getJSONArray("catches").length(), imported.catches.size)
        }
        val report = JSONObject().put("sessionsAdded", result.sessionsAdded).put("sessionsSkipped", result.sessionsSkipped)
            .put("locationsAdded", result.locationsAdded).put("beforeSessions", before.size).put("afterSessions", after.size)
            .put("safetyCopy", safetyCopy.name)
        File(context.filesDir, "import-result.json").writeText(report.toString(2), Charsets.UTF_8)
        println(report.toString())
    }
}
