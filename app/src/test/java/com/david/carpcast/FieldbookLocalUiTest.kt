package com.david.carpcast

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.junit.runner.RunWith
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.david.carpcast.journal.*
import com.david.carpcast.ui.theme.CarpCastTheme
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-rES-w411dp-h891dp-xhdpi", shadows = [WindowsAtomicFileShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FieldbookLocalUiTest {
    @get:Rule val compose = createComposeRule()
    private val app = RuntimeEnvironment.getApplication()
    private lateinit var folder: File
    private lateinit var context: Context
    private lateinit var store: FieldStore
    @Before fun setup() {
        folder = File(app.cacheDir, "fieldbook-test-${UUID.randomUUID()}").apply { mkdirs() }
        context = object : ContextWrapper(app) { override fun getFilesDir() = folder }
        store = FieldStore(context)
    }
    @After fun cleanup() { folder.deleteRecursively() }

    @Test fun largeForecastTravelsByIdAndSurvivesStoreRecreation() {
        val raw = org.json.JSONObject().put("forecasts", org.json.JSONArray().put(org.json.JSONObject()))
            .put("largePayload", "x".repeat(700_000)).toString()
        val id = store.prepareSessionSnapshot(raw)
        val intent = ForecastActivity.snapshotIntent(context, id)
        val parcel = android.os.Parcel.obtain()
        try {
            intent.writeToParcel(parcel, 0)
            assertTrue("Binder payload must stay small", parcel.dataSize() < 4096)
            assertFalse(intent.hasExtra(ForecastActivity.EXTRA_FORECAST_JSON))
            assertEquals(raw, FieldStore(context).sessionSnapshot(intent.getStringExtra(ForecastActivity.EXTRA_SNAPSHOT_ID)!!))
        } finally { parcel.recycle() }
    }

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

    @Test fun backupRoundTripIncludesCatchesForecastAndFavoritesWithoutDuplicates() {
        val prefs = app.getSharedPreferences("carpcast_prefs", Context.MODE_PRIVATE)
        val previous = prefs.getString("favorites", null)
        val now = System.currentTimeMillis()
        val raw = """{"forecasts":[{}],"species":"carp"}"""
        val session = FishingSession(locationName = "Copia", startedAtMs = now - 3_600_000, endedAtMs = now,
            forecastJson = raw, catches = listOf(CatchRecord(atMs = now - 1000, weightKg = 2.5)))
        try {
            prefs.edit().putString("favorites", """[{"name":"Lago","lat":40.5,"lon":-3.7}]""").commit()
            store.saveSession(session)
            val backup = store.exportBackup()
            store.deleteSession(session.id)
            prefs.edit().remove("favorites").commit()
            val result = store.importBackup(backup.byteInputStream(), includeFavorites = true)
            assertEquals(1, result.sessionsAdded)
            assertEquals(1, result.locationsAdded)
            assertEquals(session, FieldStore(context).sessions().single())
            assertEquals("Lago", com.david.carpcast.location.SavedFishingLocations.read(context).single().name)
            val repeat = store.importBackup(backup)
            assertEquals(0, repeat.sessionsAdded)
            assertEquals(1, repeat.sessionsSkipped)
            assertEquals(0, repeat.locationsAdded)
        } finally { prefs.edit().apply { if (previous == null) remove("favorites") else putString("favorites", previous) }.commit() }
    }

    @Test fun oldJournalExportImportsAndLocalEditsWin() {
        val session = FishingSession(locationName = "Original", startedAtMs = System.currentTimeMillis() - 1000)
        store.saveSession(session)
        val oldExport = store.exportJournal()
        store.deleteSession(session.id)
        assertEquals(1, store.importBackup(oldExport).sessionsAdded)
        store.saveSession(session.copy(notes = "Local edit"))
        assertEquals(1, store.importBackup(oldExport).sessionsSkipped)
        assertEquals("Local edit", store.sessions().single().notes)
    }

    @Test fun invalidBackupDoesNotPartiallyImportOrOverwrite() {
        val current = FishingSession(locationName = "Conservar", startedAtMs = System.currentTimeMillis() - 1000)
        store.saveSession(current)
        val root = org.json.JSONObject(store.exportBackup())
        val valid = org.json.JSONObject(com.google.gson.Gson().toJson(current.copy(id = UUID.randomUUID().toString())))
        root.put("sessions", org.json.JSONArray().put(valid).put(org.json.JSONObject().put("id", "bad")))
        assertTrue(runCatching { store.importBackup(root.toString()) }.isFailure)
        assertEquals(listOf(current), store.sessions())
        root.put("version", 999)
        assertTrue(runCatching { store.importBackup(root.toString()) }.isFailure)
        assertEquals(listOf(current), store.sessions())
    }

    @Test fun importingSessionsDoesNotAddFavoritesUnlessExplicitlyEnabled() {
        val prefs = app.getSharedPreferences("carpcast_prefs", Context.MODE_PRIVATE)
        val previous = prefs.getString("favorites", null)
        val existing = """[{"name":"Mi favorito","lat":41.0,"lon":-4.0}]"""
        try {
            prefs.edit().putString("favorites", existing).commit()
            val session = FishingSession(locationName = "Lugar importado", latitude = 40.5, longitude = -3.7)
            val raw = org.json.JSONObject().put("format", "CarpCast").put("version", 2)
                .put("sessions", org.json.JSONArray().put(org.json.JSONObject(com.google.gson.Gson().toJson(session))))
                .put("locations", org.json.JSONArray("""[{"name":"Lugar importado","lat":40.5,"lon":-3.7}]""")).toString()
            assertEquals(0, store.importBackup(raw).locationsAdded)
            assertEquals(existing, prefs.getString("favorites", null))
            assertEquals(40.5, store.sessions().single().latitude!!, 0.0)
            assertEquals(-3.7, store.sessions().single().longitude!!, 0.0)
            assertEquals(1, store.importBackup(raw, includeFavorites = true).locationsAdded)
            assertEquals(2, com.david.carpcast.location.SavedFishingLocations.read(context).size)
        } finally { prefs.edit().apply { if (previous == null) remove("favorites") else putString("favorites", previous) }.commit() }
    }

    @Test fun reimportRestoresDeletedCatchesWithoutDuplicatingOrOverwritingEdits() {
        val now = System.currentTimeMillis()
        val capture = CatchRecord(atMs = now - 1000, weightKg = 2.5)
        val session = FishingSession(locationName = "Restore", startedAtMs = now - 60_000, catches = listOf(capture))
        store.saveSession(session)
        val backup = store.exportBackup()
        store.saveSession(session.copy(notes = "Keep local notes", catches = emptyList()))
        assertEquals(1, store.importBackup(backup).capturesAdded)
        assertEquals(listOf(capture), store.sessions().single().catches)
        assertEquals("Keep local notes", store.sessions().single().notes)
        assertEquals(0, store.importBackup(backup).capturesAdded)
        assertEquals(1, store.sessions().single().catches.size)
    }

    @Test fun deletingCatchAndSessionRequiresConfirmationAndCancelKeepsData() {
        val now = System.currentTimeMillis()
        val session = FishingSession(locationName = "Confirmar borrado", startedAtMs = now - 60_000, catches = listOf(CatchRecord(atMs = now - 1000)))
        store.saveSession(session)
        compose.setContent { CarpCastTheme { PreviewJournalHost(store, null, {}, ioDispatcher = kotlinx.coroutines.Dispatchers.Main.immediate) } }
        compose.onNodeWithText("Confirmar borrado").performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.field_remove_catch)).performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.field_delete_catch_title)).assertIsDisplayed()
        assertEquals(1, store.sessions().single().catches.size)
        compose.onNodeWithText(app.getString(R.string.field_cancel)).performClick()
        assertEquals(1, store.sessions().single().catches.size)
        compose.onNodeWithText(app.getString(R.string.field_remove_catch)).performScrollTo().performClick()
        compose.onNode(hasText(app.getString(R.string.field_delete)) and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(10_000) { store.sessions().single().catches.isEmpty() }
        compose.onNodeWithText(app.getString(R.string.field_delete)).performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.field_cancel)).performClick()
        assertEquals(1, store.sessions().size)
        compose.onNodeWithText(app.getString(R.string.field_delete)).performScrollTo().performClick()
        compose.onNode(hasText(app.getString(R.string.field_delete)) and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(10_000) { store.sessions().isEmpty() }
    }

    @Test fun catchUsesItsOwnSpeciesAndNeverFallsBackToCarp() {
        val raw = """{"species":"carp","forecasts":[{"time":"2025-01-01T10:00:00Z","activity":{"overall":70},"activityBySpecies":{"carp":{"overall":70},"catfish":{"overall":35}},"weather":{"temperature":15}}]}"""
        val at = java.time.Instant.parse("2025-01-01T10:35:00Z").toEpochMilli()
        assertEquals(70, forecastAtCatch(raw, at, "carpa")!!.hour.score)
        assertEquals(35, forecastAtCatch(raw, at, "siluro")!!.hour.score)
        assertEquals("catfish", forecastAtCatch(raw, at, "siluro")!!.species)
        assertNull(forecastAtCatch(raw, at, "pike")!!.hour.score)
        for (unsupported in listOf("other", "Carpín", "Tortuga")) {
            val result = forecastAtCatch(raw, at, unsupported)!!
            assertNull(result.hour.score)
            assertEquals(15.0, result.hour.temperature!!, 0.0)
        }
    }

    @Test fun historicalSpeciesEnrichmentCannotChangeRecordedWeather() {
        val old = """{"source":"historical_reanalysis","lat":40,"lon":-3,"forecasts":[{"time":"2025-01-01T10:00:00Z","activity":{"overall":70},"weather":{"temperature":15}}]}"""
        val fresh = org.json.JSONObject(old)
        val scores = org.json.JSONObject()
        for (id in listOf("carp","barbel","bass","pike","catfish")) scores.put(id, org.json.JSONObject().put("overall", 50))
        fresh.getJSONArray("forecasts").getJSONObject(0).put("activityBySpecies", scores)
        val merged = mergeHistoricalSpeciesScores(old, fresh.toString())
        assertTrue(onlyAddsSpeciesScores(old, merged))
        assertFalse(onlyAddsSpeciesScores(old, merged.replace("\"temperature\":15", "\"temperature\":30")))
        val session = FishingSession(locationName = "Keep location", notes = "Keep notes", historicalForecastJson = old)
        store.saveSession(session)
        val backup = org.json.JSONObject(store.exportBackup())
        backup.getJSONArray("sessions").getJSONObject(0).put("historicalForecastJson", merged).put("notes", "Stale notes")
        assertEquals(1, store.importBackup(backup.toString()).historiesAdded)
        assertEquals("Keep notes", store.sessions().single().notes)
        assertTrue(onlyAddsSpeciesScores(old, store.sessions().single().historicalForecastJson!!))
    }

    @Test fun importAddsMissingHistoryWithoutReplacingLocalNotesOrDuplicatingSession() {
        val history = """{"source":"historical_reanalysis","forecasts":[{"time":"2025-01-01T10:00:00Z","activity":{"overall":60}}]}"""
        val session = FishingSession(locationName = "Local", startedAtMs = java.time.Instant.parse("2025-01-01T00:00:00Z").toEpochMilli(),
            latitude = 40.5, longitude = -3.7, notes = "Keep my notes")
        store.saveSession(session)
        val root = org.json.JSONObject(store.exportBackup())
        root.getJSONArray("sessions").getJSONObject(0).put("historicalForecastJson", history).put("notes", "Stale notes")
        val result = store.importBackup(root.toString())
        assertEquals(0, result.sessionsAdded)
        assertEquals(1, result.historiesAdded)
        val saved = store.sessions().single()
        assertEquals("Keep my notes", saved.notes)
        assertEquals(history, saved.historicalForecastJson)
        assertNull(saved.forecastJson)
        assertEquals(0, store.importBackup(root.toString()).historiesAdded)
    }

    @Test fun historyIsSeparateImmutableAndIncludedInBackup() {
        val raw = """{"source":"historical_reanalysis","generatedAt":"2026-01-02T12:00:00Z","species":"carp","forecasts":[{"time":"2026-01-01T10:00:00Z","activity":{"overall":65},"weather":{"temperature":12}}]}"""
        val session = FishingSession(locationName = "Historia", startedAtMs = java.time.Instant.parse("2026-01-01T00:00:00Z").toEpochMilli())
        store.saveSession(session)
        store.saveSession(session.copy(historicalForecastJson = raw))
        val saved = store.sessions().single()
        assertNull(saved.forecastJson)
        assertEquals(raw, saved.historicalForecastJson)
        val point = forecastAtCatch(raw, java.time.Instant.parse("2026-01-01T10:35:00Z").toEpochMilli())!!
        assertTrue(point.historical)
        assertNull(point.hour.precipitationProbability)
        assertTrue(runCatching { store.saveSession(saved.copy(historicalForecastJson = "changed")) }.isFailure)
        val export = store.exportBackup()
        store.deleteSession(saved.id)
        store.importBackup(export)
        assertEquals(raw, store.sessions().single().historicalForecastJson)
    }

    @Test fun catchForecastMatchesHourByInstantAndNeverAnotherDay() {
        val snapshot = """{"generatedAt":"2025-01-01T08:00:00Z","species":"carp","forecasts":[{"time":"2025-01-01T10:00:00+01:00","activity":{"overall":72},"weather":{"temperature":15,"pop":60,"weather_code":61}}]}"""
        val start = java.time.Instant.parse("2025-01-01T09:00:00Z").toEpochMilli()
        val match = forecastAtCatch(snapshot, start + 35 * 60_000)!!
        assertEquals(72, match.hour.score)
        assertEquals(0.6, match.hour.precipitationProbability!!, 0.0001)
        assertFalse(match.savedAfterCatch)
        assertNull(forecastAtCatch(snapshot, start - 1))
        assertNull(forecastAtCatch(snapshot, start + 3_600_000))
        assertNull(forecastAtCatch(snapshot, start + 86_400_000))
        assertNull(forecastAtCatch(null, start))
        assertNull(forecastAtCatch("invalid", start))
        assertTrue(forecastAtCatch(snapshot.replace("08:00:00Z", "11:00:00Z"), start)!!.savedAfterCatch)
    }

    @Test fun journalDetailShowsMapAndForecastForCatch() {
        val start = java.time.Instant.parse("2025-01-01T09:00:00Z").toEpochMilli()
        val raw = """{"generatedAt":"2025-01-01T08:00:00Z","species":"carp","forecasts":[{"time":"2025-01-01T10:00:00+01:00","activity":{"overall":72},"weather":{"temperature":15,"pop":60,"precip":0.5,"wind_speed":10,"gust":20,"pressure":1010,"weather_code":61}}]}"""
        store.saveSession(FishingSession(locationName = "Detalle con mapa", latitude = 40.5, longitude = -3.7,
            startedAtMs = start, endedAtMs = start + 3_600_000, forecastJson = raw, catches = listOf(CatchRecord(atMs = start + 35 * 60_000))))
        compose.setContent { CarpCastTheme { PreviewJournalHost(store, null, {}, ioDispatcher = kotlinx.coroutines.Dispatchers.Main.immediate) } }
        compose.onNodeWithText("Detalle con mapa").performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.field_session_map)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.field_catch_forecast)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.field_catch_forecast_index, app.getString(R.string.species_carp_label), 72)).assertExists()
        capture("journal-catch-forecast.png")
    }

    @Test fun captureCanSelectOtherAndKeepItInExport() {
        val now = System.currentTimeMillis()
        store.saveSession(FishingSession(locationName = "Otros", startedAtMs = now - 60_000))
        compose.setContent { CarpCastTheme { PreviewJournalHost(store, null, {}, ioDispatcher = kotlinx.coroutines.Dispatchers.Main.immediate) } }
        compose.onNodeWithText("Otros").performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.field_add_catch)).performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.field_species_other)).performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.field_catch_save)).performScrollTo().performClick()
        compose.waitUntil(10_000) { store.sessions().single().catches.size == 1 }
        assertEquals("other", FieldStore(context).sessions().single().catches.single().species)
        val backup = org.json.JSONObject(store.exportBackup())
        assertEquals("other", backup.getJSONArray("sessions").getJSONObject(0).getJSONArray("catches").getJSONObject(0).getString("species"))
    }

    @Test fun captureUsesSessionDayWithClockPickerAndManualTime() {
        val zone = java.time.ZoneId.systemDefault()
        val day = java.time.LocalDate.now(zone).minusDays(3)
        val session = FishingSession(locationName = "Horas de captura", dateOnly = true,
            startedAtMs = day.atStartOfDay(zone).toInstant().toEpochMilli(),
            endedAtMs = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1)
        store.saveSession(session)
        compose.setContent { CarpCastTheme { PreviewJournalHost(store, null, {}, ioDispatcher = kotlinx.coroutines.Dispatchers.Main.immediate) } }
        compose.onNodeWithText("Horas de captura").performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.field_add_catch)).performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.field_open_clock)).performScrollTo().performClick()
        compose.runOnIdle {
            val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog() as android.app.TimePickerDialog
            dialog.updateTime(10, 35)
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        }
        compose.onNodeWithText(app.getString(R.string.field_catch_save)).performScrollTo().performClick()
        compose.waitUntil(10_000) { store.sessions().single().catches.size == 1 }
        val first = java.time.Instant.ofEpochMilli(store.sessions().single().catches.single().atMs).atZone(zone)
        assertEquals(day, first.toLocalDate())
        assertEquals(java.time.LocalTime.of(10, 35), first.toLocalTime())
        compose.onNodeWithText(app.getString(R.string.field_add_catch)).performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.field_catch_time)).performScrollTo().performTextReplacement("17:45")
        compose.onNodeWithText(app.getString(R.string.field_catch_save)).performScrollTo().performClick()
        compose.waitUntil(10_000) { store.sessions().single().catches.size == 2 }
        val second = java.time.Instant.ofEpochMilli(store.sessions().single().catches.last().atMs).atZone(zone)
        assertEquals(day, second.toLocalDate())
        assertEquals(java.time.LocalTime.of(17, 45), second.toLocalTime())
    }

    @Test fun calendarUpdatesSessionDate() {
        val chosen = java.time.LocalDate.now().minusDays(3)
        compose.setContent { CarpCastTheme { PreviewJournalHost(store, null, {}, ioDispatcher = kotlinx.coroutines.Dispatchers.Main.immediate) } }
        compose.onNodeWithText(app.getString(R.string.field_new_session)).performClick()
        compose.onNodeWithText(app.getString(R.string.field_location)).performTextInput("Calendario")
        compose.onNodeWithText(app.getString(R.string.field_open_calendar)).performScrollTo().performClick()
        compose.runOnIdle {
            val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog() as android.app.DatePickerDialog
            dialog.datePicker.updateDate(chosen.year, chosen.monthValue - 1, chosen.dayOfMonth)
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        }
        compose.onNodeWithText(app.getString(R.string.field_save)).performScrollTo().performClick()
        compose.waitUntil(10_000) { store.sessions().size == 1 }
        val saved = store.sessions().single()
        assertEquals(chosen, java.time.Instant.ofEpochMilli(saved.startedAtMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate())
    }

    @Test fun previousSessionNeedsOnlyDateAndKeepsCatchTimes() {
        val day = java.time.LocalDate.now().minusDays(2).toString()
        compose.setContent { CarpCastTheme { PreviewJournalHost(store, null, {}, ioDispatcher = kotlinx.coroutines.Dispatchers.Main.immediate) } }
        compose.onNodeWithText(app.getString(R.string.field_new_session)).performClick()
        compose.onNodeWithText(app.getString(R.string.field_location)).performTextInput("Salida anterior")
        compose.onNodeWithText(app.getString(R.string.field_date_manual)).performScrollTo().performTextReplacement(day)
        compose.onNodeWithText(app.getString(R.string.field_save)).performScrollTo().performClick()
        compose.waitUntil(10_000) { store.sessions().size == 1 }
        val saved = FieldStore(context).sessions().single()
        assertTrue(saved.dateOnly)
        assertNull(saved.rodHours())
        assertNull(saved.catchesPerRodHour())
        val caughtAt = java.time.LocalDate.parse(day).atTime(10, 35).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        store.saveSession(saved.copy(catches = listOf(CatchRecord(atMs = caughtAt))))
        assertEquals(caughtAt, store.sessions().single().catches.single().atMs)
        assertEquals(day, java.time.Instant.ofEpochMilli(saved.startedAtMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString())
        assertNotNull(saved.endedAtMs)
    }

    @Test fun journalUsesHomeFavoritesAndPersistsCoordinates() {
        val prefs = app.getSharedPreferences("carpcast_prefs", Context.MODE_PRIVATE)
        val previous = prefs.getString("favorites", null)
        prefs.edit().putString("favorites", """[{"name":"Embalse guardado","lat":40.51,"lon":-3.72}]""").commit()
        try {
            compose.setContent { CarpCastTheme { PreviewJournalHost(store, null, {}, ioDispatcher = kotlinx.coroutines.Dispatchers.Main.immediate) } }
            compose.onNodeWithText(app.getString(R.string.field_new_session)).performClick()
            compose.onNodeWithText("Embalse guardado").performScrollTo().performClick()
            compose.onNodeWithText(app.getString(R.string.field_save)).performScrollTo().performClick()
            compose.waitUntil(10_000) { store.sessions().size == 1 }
            val saved = FieldStore(context).sessions().single()
            assertEquals("Embalse guardado", saved.locationName)
            assertEquals(40.51, saved.latitude!!, 0.00001)
            assertEquals(-3.72, saved.longitude!!, 0.00001)
        } finally {
            prefs.edit().apply { if (previous == null) remove("favorites") else putString("favorites", previous) }.commit()
        }
    }

    @Test fun journalCanStartAddCatchAndFinish() {
        compose.setContent { CarpCastTheme { PreviewJournalHost(store, null, {}, ioDispatcher = kotlinx.coroutines.Dispatchers.Main.immediate) } }
        compose.onNodeWithText(app.getString(R.string.field_new_session)).performClick()
        compose.onNodeWithText(app.getString(R.string.field_location)).performTextInput("Embalse de prueba")
        compose.onNodeWithText(app.getString(R.string.field_save)).performScrollTo().performClick()
        compose.waitUntil(10_000) { store.sessions().size == 1 }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(app.getString(R.string.field_add_catch)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(app.getString(R.string.field_add_catch)).performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.field_catch_save)).performScrollTo().performClick()
        compose.waitUntil(10_000) { store.sessions().single().catches.size == 1 }
        compose.onNodeWithText(app.getString(R.string.field_finish_session)).performScrollTo().performClick()
        compose.waitUntil(10_000) { store.sessions().single().endedAtMs != null }
        capture("journal-session.png")
    }

    @Test fun renderForecastAtLargeFont() {
        val day = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Madrid"))
        val hours = (0..23).map { hour -> com.david.carpcast.ui.HourlyEntry(
            day.atTime(hour, 0).atZone(java.time.ZoneId.of("Europe/Madrid")).toOffsetDateTime().toString(),
            temperature = 18.0, windSpeed = 10.0, windGust = 22.0, precipitationProbability = 0.65, precipitation = 0.3, weatherCode = 61, isDay = hour in 8..18, pressure = 1008.0, score = 62 + hour % 10,
            confidencePct = 90, waterTempSource = "estimated", influences = listOf(
                com.david.carpcast.scoring.FactorInfluence("wind", 3.0),
                com.david.carpcast.scoring.FactorInfluence("pressure", 2.0),
                com.david.carpcast.scoring.FactorInfluence("temp", -1.0))) }
        val f = com.david.carpcast.ui.SimpleForecast(generatedAt = day.toString(), locationName = "Embalse de prueba",
            dayAvg = 68, bestHourTime = "18:00", bestHourScore = 72, worstHourTime = "01:00", worstHourScore = 60,
            avgConfidencePct = 90, precipSum = 0.5, cloudAvg = 60.0, humidityAvg = 70.0, tempMin = 12.0, tempMax = 20.0,
            windMin = 5.0, windAvgMin = 5.0, windAvgMax = 15.0, windMax = 20.0, pressureMin = 1006.0, pressureMax = 1010.0,
            bestWindows = emptyList(), sunrise = "07:00", sunset = "18:00", moonInfo = "—", hydroText = null, hourly = hours)
        compose.setContent { CarpCastTheme {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(2f, 1.3f)) {
                com.david.carpcast.ui.ModernForecastScreen(f, generatedAt = java.time.Instant.now().toString(), speciesId = "carp")
            }
        } }
        compose.onNodeWithTag("forecast-list").performScrollToNode(hasText(app.getString(R.string.wx_title)))
        compose.onNodeWithText(app.getString(R.string.wx_title)).assertIsDisplayed()
        capture("forecast-weather.png")
        compose.onNodeWithTag("forecast-list").performScrollToNode(hasText(app.getString(R.string.field_start_here)))
        compose.onNodeWithText(app.getString(R.string.field_start_here)).assertExists()
        capture("forecast-large-font.png")
        compose.onNodeWithTag("forecast-list").performScrollToNode(hasText(app.getString(R.string.field_data_sources)))
        compose.onNodeWithText(app.getString(R.string.field_data_sources)).assertIsDisplayed()
        capture("forecast-data.png")
    }

    @Test fun renderHome() {
        compose.setContent { CarpCastTheme {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {
                com.david.carpcast.ui.home.HomeScreenContent(searchQuery = "", searchResults = emptyList(),
                    selectedLocation = com.david.carpcast.model.LocationModel("Embalse de prueba", 40.4, -3.7), isLoading = false)
            }
        } }
        compose.onNodeWithText(app.getString(R.string.field_journal_action)).assertExists()
        capture("home.png")
    }

    private fun capture(name: String) {
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            val activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).first()
            val view = activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
        }
        val file = File("build/qa/$name").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

@androidx.compose.runtime.Composable
private fun PreviewJournalHost(store: FieldStore, snapshotId: String?, onBack: () -> Unit, ioDispatcher: kotlinx.coroutines.CoroutineDispatcher) {
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {
        JournalHost(store, snapshotId, onBack, ioDispatcher)
    }
}
