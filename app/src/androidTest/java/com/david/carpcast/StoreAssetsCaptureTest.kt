package com.david.carpcast

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.david.carpcast.journal.*
import com.david.carpcast.model.LocationModel
import com.david.carpcast.repository.*
import com.david.carpcast.ui.*
import com.david.carpcast.ui.home.HomeScreenContent
import com.david.carpcast.ui.theme.CarpCastTheme
import org.junit.*
import org.junit.Assume.assumeTrue
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.time.*
import java.util.UUID

class StoreAssetsCaptureTest {
    @get:Rule val compose = createComposeRule()
    private val app = InstrumentationRegistry.getInstrumentation().targetContext

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun captureSpanishStoreAssets() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureStore") == "true")
        val folder = File(app.cacheDir, "store-assets-${UUID.randomUUID()}").apply { mkdirs() }
        val demo = object : ContextWrapper(app) {
            override fun getFilesDir() = folder
            override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences("store-demo-$name", mode)
        }
        val store = FieldStore(demo)
        val zone = ZoneId.of("Europe/Madrid")
        val raw = kotlinx.coroutines.runBlocking { ForecastRepository().buildForecastJsonReal(38.78, -6.25, "carp") }
        raw.put("name", "Embalse de Alange")
        val forecast = toForecast(raw, zone)
        val day = LocalDate.of(2025, 9, 21)
        val session = FishingSession(id = "store-demo-carp", dateOnly = true, locationName = "Embalse de Alange",
            latitude = 38.78, longitude = -6.25, targetSpecies = "carp", notes = "Datos de ejemplo.",
            startedAtMs = day.atStartOfDay(zone).toInstant().toEpochMilli(),
            endedAtMs = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1,
            catches = listOf(CatchRecord(atMs = day.atTime(10, 35).atZone(zone).toInstant().toEpochMilli(), weightKg = 4.2, notes = "Datos de ejemplo. Cebo: boilie."),
                CatchRecord(atMs = day.atTime(12, 20).atZone(zone).toInstant().toEpochMilli(), weightKg = 6.8, notes = "Datos de ejemplo.")))
        val history = kotlinx.coroutines.runBlocking { HistoricalForecastRepository().reconstruct(session) }
        store.saveSession(session.copy(historicalForecastJson = history))
        store.saveSession(FishingSession(id = "store-demo-bass", dateOnly = true, locationName = "Lago de ejemplo",
            targetSpecies = "bass", startedAtMs = day.minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli(),
            endedAtMs = day.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()-1, notes = "Datos de ejemplo. Jornada sin capturas."))
        var screen by mutableStateOf(0)
        compose.setContent {
            CarpCastTheme {
                val registryOwner = requireNotNull(androidx.activity.compose.LocalActivityResultRegistryOwner.current)
                val backOwner = requireNotNull(androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current)
                CompositionLocalProvider(LocalContext provides demo,
                    androidx.activity.compose.LocalActivityResultRegistryOwner provides registryOwner,
                    androidx.activity.compose.LocalOnBackPressedDispatcherOwner provides backOwner) {
                    key(screen) {
                        when (screen) {
                            0 -> Scaffold { padding -> Box(Modifier.padding(padding)) {
                                HomeScreenContent("", emptyList(), LocationModel("Embalse de Alange", 38.78, -6.25), false)
                            } }
                            1 -> ModernForecastScreen(forecast, generatedAt = raw.getString("generatedAt"), speciesId = "carp")
                            else -> JournalHost(store, null, {}, ioDispatcher = kotlinx.coroutines.Dispatchers.IO)
                        }
                    }
                }
            }
        }
        compose.runOnIdle {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first() as ComponentActivity
            activity.enableEdgeToEdge(statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.rgb(7,21,34)))
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.window.insetsController?.hide(android.view.WindowInsets.Type.systemBars())
        }
        Thread.sleep(4000)
        capture("01-inicio.png")
        compose.runOnIdle { screen = 1 }
        compose.waitForIdle()
        capture("02-pronostico.png")
        compose.onNodeWithTag("forecast-list").performScrollToNode(hasText(app.getString(R.string.field_why)))
        compose.onNodeWithTag("forecast-list").performScrollToIndex(5)
        capture("03-actividad-y-factores.png")
        compose.onNodeWithTag("forecast-list").performScrollToNode(hasText(app.getString(R.string.hourly_forecast_title)))
        compose.onNodeWithText(app.getString(R.string.show)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("forecast-list").performScrollToIndex(8)
        capture("04-tiempo-por-horas.png")
        compose.runOnIdle { screen = 2 }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Embalse de Alange").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Lago de ejemplo").performScrollTo()
        capture("05-diario-de-pesca.png")
        compose.onNodeWithText("Embalse de Alange").performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.field_session_map)).performScrollTo()
        compose.onAllNodesWithText(app.getString(R.string.field_coordinates, 38.78, -6.25)).onLast().performScrollTo()
        Thread.sleep(4000)
        capture("06-mapa-del-puesto.png")
        compose.onAllNodesWithText(app.getString(R.string.field_history_note)).onFirst().performScrollTo()
        capture("07-detalle-de-captura.png")
        exportBrandGraphics()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        Thread.sleep(500)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file = File(app.getExternalFilesDir(null), "play-store/$name").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun toForecast(root: JSONObject, zone: ZoneId): SimpleForecast {
        val points = root.getJSONArray("forecasts")
        val groups = (0 until points.length()).map { points.getJSONObject(it) }.groupBy { it.getString("time").take(10) }
        val days = groups.map { (date, values) ->
            val hours = values.mapNotNull { point ->
                val at = com.david.carpcast.util.TimeUtils.parseTimeMs(point.getString("time")) ?: return@mapNotNull null
                val small = JSONObject().put("species", "carp").put("forecasts", JSONArray().put(point))
                val activity = point.getJSONObject("activity")
                val factors = activity.optJSONArray("influences")
                forecastAtCatch(small.toString(), at + 1, "carp")?.hour?.copy(
                    confidencePct = (activity.optDouble("confidence", 0.0) * 100).toInt(),
                    waterTempSource = activity.optString("waterTempSource"),
                    influences = (0 until (factors?.length() ?: 0)).map { i ->
                        val factor = factors!!.getJSONObject(i)
                        com.david.carpcast.scoring.FactorInfluence(factor.getString("key"), factor.getDouble("contribution"))
                    })
            }
            val scores = hours.mapNotNull { it.score }
            val temp = hours.mapNotNull { it.temperature }
            val winds = hours.mapNotNull { it.windSpeed }
            val pressures = hours.mapNotNull { it.pressure }
            SimpleForecast(generatedAt = date, locationName = "Embalse de Alange", dayAvg = scores.average().toInt(),
                bestHourTime = hours.maxByOrNull { it.score ?: 0 }?.time.orEmpty(), bestHourScore = scores.maxOrNull() ?: 0,
                worstHourTime = hours.minByOrNull { it.score ?: 0 }?.time.orEmpty(), worstHourScore = scores.minOrNull() ?: 0,
                avgConfidencePct = hours.mapNotNull { it.confidencePct }.average().toInt(), precipSum = hours.mapNotNull { it.precipitation }.sum(), cloudAvg = hours.mapNotNull { it.cloudCover }.average(),
                humidityAvg = values.map { it.getJSONObject("weather").optDouble("humidity", 0.0) }.average(),
                tempMin = temp.minOrNull() ?: 0.0, tempMax = temp.maxOrNull() ?: 0.0, windMin = winds.minOrNull() ?: 0.0,
                windAvgMin = winds.minOrNull() ?: 0.0, windAvgMax = winds.maxOrNull() ?: 0.0, windMax = winds.maxOrNull() ?: 0.0,
                pressureMin = pressures.minOrNull() ?: 0.0, pressureMax = pressures.maxOrNull() ?: 0.0,
                bestWindows = emptyList(), sunrise = "", sunset = "", moonInfo = "", hydroText = null, hourly = hours)
        }
        val selected = days.firstOrNull { it.generatedAt > LocalDate.now(zone).toString() } ?: days.first()
        return selected.copy(otherDays = days.filterNot { it.generatedAt == selected.generatedAt })
    }

    private fun exportBrandGraphics() {
        val output = File(app.getExternalFilesDir(null), "play-store").apply { mkdirs() }
        val drawable = app.getDrawable(R.mipmap.ic_launcher)!!
        val icon = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(icon)
        drawable.setBounds(0, 0, 512, 512)
        drawable.draw(canvas)
        File(output, "icono-512.png").outputStream().use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val banner = Bitmap.createBitmap(1024, 500, Bitmap.Config.ARGB_8888)
        banner.setHasAlpha(false)
        val c = android.graphics.Canvas(banner)
        c.drawColor(android.graphics.Color.rgb(7,21,34))
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.shader = android.graphics.LinearGradient(0f, 0f, 1024f, 500f, intArrayOf(0xff102d43.toInt(),0xff071522.toInt()),null, android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,1024f,500f,paint)
        paint.shader = null
        drawable.setBounds(700, 112, 966, 378)
        drawable.draw(c)
        paint.color = android.graphics.Color.WHITE
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        paint.textSize = 82f
        c.drawText("CarpCast", 72f, 210f, paint)
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        paint.textSize = 32f
        paint.color = 0xffbdd5e8.toInt()
        c.drawText("Planifica tu pesca.", 76f, 280f, paint)
        c.drawText("Guarda cada captura.", 76f, 327f, paint)
        File(output, "grafico-funciones-1024x500.png").outputStream().use { banner.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
