package com.david.carpcast

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.david.carpcast.journal.*
import com.david.carpcast.ui.theme.CarpCastTheme
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class FieldbookInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var folder: File
    private lateinit var context: Context
    private lateinit var store: FieldStore
    @Before fun setup() {
        folder = File(app.cacheDir, "fieldbook-test-${UUID.randomUUID()}").apply { mkdirs() }
        context = object : ContextWrapper(app) { override fun getFilesDir() = folder }
        store = FieldStore(context)
    }
    @After fun cleanup() { folder.deleteRecursively() }

    @Test fun snapshotStaysImmutableWhenCacheIsRefreshed() {
        val raw = """{"lat":40,"lon":-3,"species":"carp","generatedAt":"2026-01-01T10:00:00Z","forecasts":[{"time":"2026-01-01T10:00:00Z"}]}"""
        store.saveForecast(raw)
        val session = FishingSession(locationName = "Test", forecastJson = raw)
        store.saveSession(session)
        store.saveForecast(raw.replace("10:00:00", "11:00:00"))
        assertTrue(store.cachedForecast()!!.json.contains("11:00:00"))
        val reopened = FieldStore(context).sessions().single()
        assertEquals(raw, reopened.forecastJson)
        assertTrue(runCatching { store.saveSession(reopened.copy(forecastJson = "changed")) }.isFailure)
        assertEquals(raw, store.sessions().single().forecastJson)
    }

    @Test fun finishedZeroCatchSessionPersistsAndExports() {
        val now = System.currentTimeMillis()
        val session = FishingSession(locationName = "Test", startedAtMs = now - 3_600_000L, endedAtMs = now, rods = 2)
        store.saveSession(session)
        assertEquals(0, FieldStore(context).sessions().single().catches.size)
        assertTrue(store.exportJournal().contains(session.id))
        assertEquals(0.0, store.sessions().single().catchesPerRodHour()!!, 0.0)
    }

    @Test fun corruptJournalIsNotOverwrittenAsEmpty() {
        val f = File(folder, "fieldbook/journal.json")
        f.parentFile!!.mkdirs(); f.writeText("corrupt")
        assertTrue(runCatching { store.saveSession(FishingSession(locationName = "Test")) }.isFailure)
        assertEquals("corrupt", f.readText())
    }

    @Test fun journalCanStartAddCatchAndFinish() {
        compose.setContent { CarpCastTheme { JournalHost(store, null, {}) } }
        compose.onNodeWithText(app.getString(R.string.field_new_session)).performClick()
        compose.onNodeWithText(app.getString(R.string.field_location)).performTextInput("Embalse de prueba")
        compose.onNodeWithText(app.getString(R.string.field_save)).performScrollTo().performClick()
        compose.waitUntil(10_000) { store.sessions().size == 1 }
        compose.onNodeWithText(app.getString(R.string.field_add_catch)).performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.field_catch_save)).performScrollTo().performClick()
        compose.waitUntil(10_000) { store.sessions().single().catches.size == 1 }
        compose.onNodeWithText(app.getString(R.string.field_finish_session)).performScrollTo().performClick()
        compose.waitUntil(10_000) { store.sessions().single().endedAtMs != null }
        capture("journal-session.png")
    }

    private fun capture(name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file = File(app.getExternalFilesDir(null), "qa/$name").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
