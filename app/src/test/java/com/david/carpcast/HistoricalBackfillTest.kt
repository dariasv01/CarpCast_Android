package com.david.carpcast

import com.david.carpcast.journal.FishingSession
import com.david.carpcast.journal.forecastAtCatch
import com.david.carpcast.repository.HistoricalForecastRepository
import com.google.gson.Gson
import org.json.JSONObject
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Optional real-API backfill, invoked only with explicit input/output environment variables. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoricalBackfillTest {
    @Test fun reconstructProvidedHistoricalJournals() {
        val source = System.getenv("CARPCAST_HISTORY_INPUT")
        val destination = System.getenv("CARPCAST_HISTORY_OUTPUT")
        assumeTrue(source != null && destination != null)
        val root = JSONObject(File(source!!).readText(Charsets.UTF_8))
        val array = root.getJSONArray("sessions")
        val gson = Gson()
        var completed = 0
        val failures = mutableListOf<String>()
        kotlinx.coroutines.runBlocking {
            val repository = HistoricalForecastRepository()
            for (i in 0 until array.length()) {
                val session = gson.fromJson(array.getJSONObject(i).toString(), FishingSession::class.java)
                if (session.forecastJson != null) continue
                try {
                    val calculated = repository.reconstruct(session.copy(historicalForecastJson = null))
                    val raw = session.historicalForecastJson?.let { com.david.carpcast.journal.mergeHistoricalSpeciesScores(it, calculated) } ?: calculated
                    for (record in session.catches) assertNotNull(forecastAtCatch(raw, record.atMs))
                    array.put(i, JSONObject(gson.toJson(session.copy(historicalForecastJson = raw))))
                    completed++
                } catch (e: Exception) { failures.add("${session.id}: ${e.message}") }
                File(destination!!).writeText(root.toString(2), Charsets.UTF_8)
                File(destination + ".progress.json").writeText(JSONObject().put("completed", completed).put("total", array.length()).put("failures", org.json.JSONArray(failures)).toString(2), Charsets.UTF_8)
            }
        }
        assertTrue(failures.toString(), failures.isEmpty())
        assertEquals(array.length(), completed)
    }
}
