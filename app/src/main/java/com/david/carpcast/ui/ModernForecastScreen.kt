package com.david.carpcast.ui

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.runtime.MutableState
import kotlin.math.roundToInt

// Modern, minimal, data-first forecast screen (dark primary version)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernForecastScreen(
    forecast: SimpleForecast,
    onBack: () -> Unit = {},
    onRefresh: () -> Unit = {},
    isLoadingState: MutableState<Boolean> = mutableStateOf(false),
    selectedDay: SimpleForecast? = null
) {
    // Mantener selección local
    val selectedDayState = remember { mutableStateOf<SimpleForecast?>(null) }

    // Si el caller provee una selección externa, priorizarla
    if (selectedDay != null && selectedDayState.value?.generatedAt != selectedDay.generatedAt) {
        selectedDayState.value = selectedDay
    }
    // Cuando cambie el objeto forecast (por ejemplo tras un refresh), intentar mantener la selección
    LaunchedEffect(key1 = forecast.generatedAt) {
        Log.d("ModernForecastScreen", "Observed forecast.generatedAt=${forecast.generatedAt}")
        val currentSel = selectedDayState.value
        if (currentSel != null) {
            val matched = forecast.otherDays.firstOrNull { it.generatedAt == currentSel.generatedAt }
            if (matched != null) {
                selectedDayState.value = matched
                Log.d("ModernForecastScreen", "Restored selection to generatedAt=${matched.generatedAt}")
            } else {
                selectedDayState.value = null
                Log.d("ModernForecastScreen", "Cleared selection because day not present in new forecast")
            }
        }
    }
    val currentForecast = selectedDayState.value ?: forecast

    // Observe loading state for debugging and add UI-side safety to avoid stuck spinner
    LaunchedEffect(key1 = isLoadingState.value) {
        Log.d("ModernForecastScreen", "isLoadingState changed -> ${isLoadingState.value}")
        if (isLoadingState.value) {
            // If Activity fails to clear loading, auto-clear after 18s to keep UI responsive
            try {
                kotlinx.coroutines.delay(18_000)
                if (isLoadingState.value) {
                    isLoadingState.value = false
                    Log.d("ModernForecastScreen", "Auto-cleared isLoadingState after 18s")
                }
            } catch (_: Exception) { }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = AppNavy) {
        Scaffold(topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = forecast.locationName,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFFB8D3FF),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = com.david.carpcast.R.string.back_desc), tint = Color(0xFFB8D3FF))
                    }
                },
                actions = {
                    IconButton(onClick = { selectedDayState.value = null; onRefresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(id = com.david.carpcast.R.string.refresh_desc), tint = Color(0xFFB8D3FF))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }, containerColor = Color.Transparent) { innerPadding ->

            Box(modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)) {
                LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item { HeroCard(forecast = currentForecast) }
                    item { PrimeWindowCompact(forecast = currentForecast) }
                    item {
                        NextDaysRow(otherDays = forecast.otherDays, highlightedGeneratedAt = selectedDayState.value?.generatedAt, onSelectDay = { day ->
                            Log.d("ModernForecastScreen", "User selected day: ${day.generatedAt}")
                            selectedDayState.value = day
                        })
                    }
                    // Insert Daily Activity Curve between NextDaysRow and HourlyCollapsible
                    item {
                        val primaryRange = currentForecast.bestWindows.firstOrNull()?.first ?: computeBestWindowsFromHourly(currentForecast.hourly).firstOrNull()?.first
                        DailyActivityCurve(forecast = currentForecast, primeRange = primaryRange)
                    }
                    item {
                        val primaryRange = currentForecast.bestWindows.firstOrNull()?.first ?: computeBestWindowsFromHourly(currentForecast.hourly).firstOrNull()?.first
                        HourlyCollapsible(forecast = currentForecast, primeRange = primaryRange)
                    }
                    item { EnvironmentalFactorsCardCompact(forecast = currentForecast) }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }

                // Overlay loading spinner centered (renders above LazyColumn content)
                if (isLoadingState.value) {
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
                        androidx.compose.material3.CircularProgressIndicator(color = Color(0xFF7FC1FF))
                    }
                    // small debug stop button top-right to force clear the loading state if it gets stuck
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp), contentAlignment = Alignment.TopEnd) {
                        IconButton(onClick = {
                            isLoadingState.value = false
                            Log.d("ModernForecastScreen", "User forced clear of isLoadingState via debug button")
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(id = com.david.carpcast.R.string.stop_loading), tint = Color(0xFFB8D3FF))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(forecast: SimpleForecast) {
    // gradient derived from the activity score (tema de la actividad)
    val gradient = activityGradientForScore(forecast.dayAvg)
    val accent = activityAccentColor(forecast.dayAvg)

    Card(
        shape = AppCardShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = AppCardElevation(default = 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, AppCardShape)
    ) {
        Box(modifier = Modifier
            .background(gradient, RoundedCornerShape(16.dp))
            .padding(18.dp)) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(id = com.david.carpcast.R.string.fishing_activity_title), style = MaterialTheme.typography.labelMedium, color = Color(0xFFB8D3FF))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text(text = forecast.dayAvg.toString(), fontSize = 52.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Text(text = activityLabelSimple(forecast.dayAvg), style = MaterialTheme.typography.titleSmall, color = accent)
                // Texto de tendencia dinámico basado en las próximas horas del mismo día
                val (trendText, trendColor) = activityTrendLocalized(forecast)
                Text(text = trendText, style = MaterialTheme.typography.bodySmall, color = trendColor)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = stringResource(id = com.david.carpcast.R.string.best_time_label, forecast.bestWindows.firstOrNull()?.first ?: forecast.bestHourTime), style = MaterialTheme.typography.bodySmall, color = Color(0xFFBFD9FF))
                    Text(text = stringResource(id = com.david.carpcast.R.string.confidence_compact, forecast.avgConfidencePct), style = MaterialTheme.typography.bodySmall, color = Color(0xFFBFD9FF))
                }
            }
        }
    }
}

@Composable
private fun activityLabelSimple(score: Int): String {
    return when {
        score >= 80 -> stringResource(id = com.david.carpcast.R.string.excellent_label)
        score >= 60 -> stringResource(id = com.david.carpcast.R.string.good_label)
        score >= 40 -> stringResource(id = com.david.carpcast.R.string.regular_label)
        else -> stringResource(id = com.david.carpcast.R.string.weak_label)
    }
}

// activity accent color and gradient are provided by shared UiTokens.kt

@Composable
private fun PrimeWindowCompact(forecast: SimpleForecast) {
    // Use provided bestWindows if available, otherwise compute from hourly entries
    val provided = forecast.bestWindows
    val windows = if (provided.isNotEmpty()) provided else computeBestWindowsFromHourly(forecast.hourly)
    if (windows.isEmpty()) return
    val primary = windows.first()
    val others = windows.drop(1).take(2)

    Card(shape = AppCardShape, modifier = Modifier.fillMaxWidth(), elevation = AppCardElevation()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(id = com.david.carpcast.R.string.fire_emoji) + " " + stringResource(id = com.david.carpcast.R.string.prime_window_label_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(text = primary.second.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text(text = primary.first, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ThinProgress(value = (primary.second.coerceIn(0,100) / 100f), prominent = true, color = activityAccentColor(primary.second))

            if (others.isNotEmpty()) {
                Text(text = stringResource(id = com.david.carpcast.R.string.also_good_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (o in others) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(o.first, style = MaterialTheme.typography.bodySmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ThinProgress(value = (o.second.coerceIn(0,100) / 100f), modifier = Modifier.width(110.dp), prominent = false, color = activityAccentColor(o.second))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(o.second.toString(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Compute best windows from hourly scores as a fallback when bestWindows is not provided
private fun computeBestWindowsFromHourly(hourly: List<HourlyEntry>, windowHours: Int = 2, maxResults: Int = 3): List<Pair<String, Int>> {
    if (hourly.isEmpty()) return emptyList()
    data class Candidate(val start: LocalTime, val score: Double)

    val fmt = DateTimeFormatter.ofPattern("HH:mm")
    val candidates = mutableListOf<Candidate>()

    // Build an ordered list of (time, score)
    val timesAndScores = hourly.mapNotNull { h ->
        val t = try { parseToLocalTime(h.time) } catch (_: Exception) { null }
        val s = h.score
        if (t != null && s != null) t to s.toDouble() else null
    }.sortedBy { it.first }

    if (timesAndScores.isEmpty()) return emptyList()

    for (i in timesAndScores.indices) {
        val start = timesAndScores[i].first
        // collect scores for the next windowHours hours inclusive of start
        val windowScores = mutableListOf<Double>()
        var j = i
        while (j < timesAndScores.size) {
            val cur = timesAndScores[j].first
            val hoursBetween = java.time.Duration.between(start, cur).toHours()
            val adjustedHours = if (hoursBetween < 0) (24 + hoursBetween) else hoursBetween
            if (adjustedHours >= windowHours) break
            windowScores.add(timesAndScores[j].second)
            j++
        }
        if (windowScores.isNotEmpty()) {
            val avg = windowScores.average()
            candidates.add(Candidate(start, avg))
        }
    }

    val top = candidates.sortedByDescending { it.score }.distinctBy { it.start }.take(maxResults)
    return top.map { c ->
        val end = c.start.plusHours(windowHours.toLong())
        val label = "${c.start.format(fmt)} – ${end.format(fmt)}"
        label to c.score.toInt()
    }
}

@Composable
private fun NextDaysRow(otherDays: List<SimpleForecast>, highlightedGeneratedAt: String?, onSelectDay: (SimpleForecast) -> Unit) {
    if (otherDays.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(id = com.david.carpcast.R.string.next_days_comparison_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFFB8D3FF),)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(otherDays.size) { idx ->
                val d = otherDays[idx]
                val selected = highlightedGeneratedAt != null && d.generatedAt == highlightedGeneratedAt
                val accent = activityAccentColor(d.dayAvg)
                val selBg = if (selected) accent.copy(alpha = 0.12f) else Color(0xFF071426).copy(alpha = 0.6f)
                Card(shape = AppCardShape, modifier = Modifier.width(120.dp).height(96.dp).clickable { onSelectDay(d) }, elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 8.dp else 4.dp)) {
                    Column(modifier = Modifier
                        .background(selBg)
                        .padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Text(text = formatCompactDate(d.generatedAt), style = MaterialTheme.typography.labelSmall, color = Color(0xFFBFD9FF))
                        Text(text = d.dayAvg.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        ThinProgress(value = (d.dayAvg.coerceIn(0,100) / 100f), color = accent)
                    }
                }
            }
        }
    }
}

private fun formatCompactDate(d: String): String {
    return try {
        val ld = LocalDate.parse(d)
        ld.format(DateTimeFormatter.ofPattern("EEE dd", Locale.getDefault()))
    } catch (_: Exception) { d }
}

@Composable
private fun HourlyCollapsible(forecast: SimpleForecast, primeRange: String?) {
    val expanded = rememberSaveable { mutableStateOf(true) }
    Card(shape = AppCardShape, modifier = Modifier.fillMaxWidth(), elevation = AppCardElevation()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(id = com.david.carpcast.R.string.hourly_forecast_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(text = if (expanded.value) stringResource(id = com.david.carpcast.R.string.hide) else stringResource(id = com.david.carpcast.R.string.show), modifier = Modifier.clickable { expanded.value = !expanded.value }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }

            AnimatedVisibility(visible = expanded.value, enter = fadeIn(), exit = fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val hours = forecast.hourly
                    val primeByTop = getTopScoresHours(hours, 3)

                    if (hours.isNotEmpty()) {
                        for (h in hours) HourlyRow(h, prime = primeByTop.contains(h))
                    }
                }
            }
        }
    }
}

private fun getTopScoresHours(hourly: List<HourlyEntry>, topDistinct: Int = 3): List<HourlyEntry> {
    val valid = hourly.filter { it.score != null }
    if (valid.isEmpty()) return emptyList()
    val distinctScoresDesc = valid.map { it.score!! }.distinct().sortedDescending()
    val topScores = distinctScoresDesc.take(topDistinct).toSet()
    return valid.filter { it.score!! in topScores }
        .sortedWith(compareByDescending<HourlyEntry> { it.score!! }.thenBy { parseToLocalTime(it.time) ?: LocalTime.MIDNIGHT })
}

@Composable
private fun HourlyRow(h: HourlyEntry, prime: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(text = formatTimeOnly(h.time), style = MaterialTheme.typography.bodySmall)
        ThinProgress(value = ((h.score ?: 0).coerceIn(0,100) / 100f), modifier = Modifier.width(180.dp), prominent = prime)
        Column(horizontalAlignment = Alignment.End) {
            Text(text = stringResource(id = if(prime){com.david.carpcast.R.string.fire_score}else{com.david.carpcast.R.string.score}, (h.score ?: 0)), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text(text = h.temperature?.let { String.format(Locale.getDefault(), "%.0f %s", it, stringResource(id = com.david.carpcast.R.string.unit_celsius)) } ?: "--", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun EnvironmentalFactorsCardCompact(forecast: SimpleForecast) {
    Card(shape = AppCardShape, modifier = Modifier.fillMaxWidth(), elevation = AppCardElevation()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(id = com.david.carpcast.R.string.environmental_factors_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = stringResource(id = com.david.carpcast.R.string.wind_short_label), style = MaterialTheme.typography.bodyMedium)
                Text(text = String.format(Locale.getDefault(), "%.0f–%.0f %s", forecast.windAvgMin, forecast.windAvgMax, stringResource(id = com.david.carpcast.R.string.unit_kmh)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = stringResource(id = com.david.carpcast.R.string.pressure_short_label), style = MaterialTheme.typography.bodyMedium)
                Text(text = String.format(Locale.getDefault(), "%.0f–%.0f %s", forecast.pressureMin, forecast.pressureMax, stringResource(id = com.david.carpcast.R.string.unit_hpa)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = stringResource(id = com.david.carpcast.R.string.clouds_short_label), style = MaterialTheme.typography.bodyMedium)
                Text(text = String.format(Locale.getDefault(), "%.0f%%", forecast.cloudAvg), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = stringResource(id = com.david.carpcast.R.string.precip_short_label), style = MaterialTheme.typography.bodyMedium)
                Text(text = String.format(Locale.getDefault(), "%.1f %s", forecast.precipSum, stringResource(id = com.david.carpcast.R.string.unit_mm)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// Composable que calcula la tendencia de actividad basada en las próximas horas del mismo día
@Composable
private fun activityTrendLocalized(forecast: SimpleForecast): Pair<String, Color> {
    val hourly = forecast.hourly
    if (hourly.isEmpty()) return "— ${stringResource(id = com.david.carpcast.R.string.hero_trend_stable)}" to Color(0xFFBFD9FF)

    val now = Instant.now().atZone(ZoneId.systemDefault()).toLocalDateTime()

    // Construir lista de pares (LocalDateTime, score) intentando parsear timestamp completo
    val futureSameDay = hourly.mapNotNull { h ->
        val score = h.score?.toDouble() ?: return@mapNotNull null
        // Intentar parsear timestamp completo (ISO)
        val dt = try {
            val inst = Instant.parse(h.time)
            LocalDateTime.ofInstant(inst, ZoneId.systemDefault())
        } catch (_: Exception) {
            // Fallback: parsear solo la hora y asumir mismo día
            val t = parseToLocalTime(h.time) ?: return@mapNotNull null
            now.toLocalDate().atTime(t)
        }
        // Asegurarse que sea del mismo día y en el futuro
        if (dt.toLocalDate() == now.toLocalDate() && dt.isAfter(now)) dt to score else null
    }.sortedBy { it.first }

    if (futureSameDay.isEmpty()) return "— ${stringResource(id = com.david.carpcast.R.string.hero_trend_stable)}" to Color(0xFFBFD9FF)

    // Tomar entre 3 y 5 próximas horas: preferir hasta 5 si están disponibles
    val maxTake = 5
    val sampled = futureSameDay.take(maxTake)
    // Si no hay al menos 1, devolver neutro (ya filtrado). Usamos los que haya (idealmente 3-5)
    val scores = sampled.map { it.second }
    val nextAverage = scores.average()

    val currentScore = forecast.dayAvg.toDouble()

    val trend = when {
        nextAverage > currentScore + 3.0 -> "↑ ${stringResource(id = com.david.carpcast.R.string.hero_trend_rising)}" to Color(0xFF9FD8B8)
        nextAverage < currentScore - 3.0 -> "↓ ${stringResource(id = com.david.carpcast.R.string.hero_trend_falling)}" to Color(0xFFFF9AA2)
        else -> "→ ${stringResource(id = com.david.carpcast.R.string.hero_trend_stable)}" to Color(0xFFBFD9FF)
    }

    return trend
}

private fun parseToLocalTime(dateStr: String?): LocalTime? {
    if (dateStr.isNullOrBlank()) return null
    try {
        val inst = try { Instant.parse(dateStr) } catch (_: Exception) { null }
        if (inst != null) return LocalDateTime.ofInstant(inst, ZoneId.systemDefault()).toLocalTime()
    } catch (_: Exception) {}
    try {
        val odt = try { OffsetDateTime.parse(dateStr) } catch (_: Exception) { null }
        if (odt != null) return odt.atZoneSameInstant(ZoneId.systemDefault()).toLocalTime()
    } catch (_: Exception) {}
    try {
        val ldt = try { LocalDateTime.parse(dateStr) } catch (_: Exception) { null }
        if (ldt != null) return ldt.toLocalTime()
    } catch (_: Exception) {}
    val regex = Regex("(\\d{1,2}:\\d{2})(?::\\d{2})?")
    val match = regex.find(dateStr)
    if (match != null) {
        val parts = match.groupValues[1].split(":")
        val hh = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val mm = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return LocalTime.of(hh % 24, mm)
    }
    return null
}

private fun isTimeInRange(hourTime: LocalTime?, range: String?): Boolean {
    if (hourTime == null || range.isNullOrBlank()) return false
    val parts = range.split("-", "–", "—").map { it.trim() }
    val start = parts.getOrNull(0)?.let { parseToLocalTime(it) }
    val end = parts.getOrNull(1)?.let { parseToLocalTime(it) }
    if (start == null || end == null) return false
    return if (start <= end) hourTime >= start && hourTime <= end else true
}

private fun formatTimeOnly(dateStr: String?): String {
    if (dateStr.isNullOrBlank()) return "--:--"
    try {
        val inst = try { Instant.parse(dateStr) } catch (_: Exception) { null }
        if (inst != null) {
            val ldt = LocalDateTime.ofInstant(inst, ZoneId.systemDefault())
            return ldt.toLocalTime().toString().substring(0,5)
        }
    } catch (_: Exception) {}
    val regex = Regex("(\\d{1,2}:\\d{2})(?::\\d{2})?")
    val match = regex.find(dateStr)
    if (match != null) return match.groupValues[1].padStart(5, '0')
    return if (dateStr.length >= 5) dateStr.substring(0,5) else dateStr
}

// Build a list of 24 hourly scores (one per hour) from the hourly entries.
private fun build24HourScores(hourly: List<HourlyEntry>, fallback: Int): List<Float> {
    val arr = Array<Float?>(24) { null }
    for (h in hourly) {
        val t = parseToLocalTime(h.time) ?: continue
        val hr = t.hour % 24
        val s = h.score
        if (s != null) arr[hr] = s.toFloat()
    }
    // Fill nulls by interpolation from nearest neighbors or fallback
    for (i in 0 until 24) {
        if (arr[i] == null) {
            // find prev
            var p = i - 1
            while (p >= 0 && arr[p] == null) p--
            var n = i + 1
            while (n < 24 && arr[n] == null) n++
            arr[i] = when {
                p >= 0 && n < 24 -> (arr[p]!! + arr[n]!!) / 2f
                p >= 0 -> arr[p]
                n < 24 -> arr[n]
                else -> fallback.toFloat()
            }
        }
    }
    return arr.map { it ?: fallback.toFloat() }
}

@Composable
private fun DailyActivityCurve(forecast: SimpleForecast, primeRange: String?) {
    val hoursData = build24HourScores(forecast.hourly, forecast.dayAvg)
    val n = hoursData.size
    if (n == 0) return

    // Determine min/max for chart scale with a small padding
    val dataMin = hoursData.minOrNull() ?: 0f
    val dataMax = hoursData.maxOrNull() ?: 100f
    val vMin = (dataMin - 6f).coerceAtLeast(0f)
    val vMax = (dataMax + 6f).coerceAtMost(100f)

    // Prime hours set: compute from primeRange string
    val primeHours = mutableSetOf<Int>()
    forecast.bestWindows.forEach { w ->
        val parts = w.first.split("–", "-", "—").map { it.trim() }
        val start = parseToLocalTime(parts.getOrNull(0))
        val end = parseToLocalTime(parts.getOrNull(1))
        if (start != null && end != null) {
            var h = start.hour
            while (true) {
                primeHours.add(h % 24)
                if (h % 24 == end.hour % 24) break
                h = (h + 1) % 24
                if (h == start.hour) break
            }
        }
    }

    // UI state for tooltip
    val selectedIndex = rememberSaveable { mutableStateOf<Int?>(null) }
    val tooltipX = remember { mutableStateOf(0f) }
    val density = LocalDensity.current

    Card(shape = AppCardShape, modifier = Modifier.fillMaxWidth(), elevation = AppCardElevation()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Daily Activity", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

            Box(modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .pointerInput(hoursData) {
                    detectTapGestures { pos ->
                        // convert tap x to index
                        val paddingH = 18f * density.density
                        val availableWidth = size.width - paddingH * 2
                        val relative = ((pos.x - paddingH) / availableWidth).coerceIn(0f, 1f)
                        val idx = (relative * (n - 1)).roundToInt().coerceIn(0, n - 1)
                        selectedIndex.value = idx
                        tooltipX.value = paddingH + (idx.toFloat() / (n - 1)) * availableWidth
                    }
                }
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val padX = 18f
                    val padY = 12f
                    val w = size.width - padX * 2f
                    val h = size.height - padY * 2f

                    // compute points
                    val points = List(n) { i ->
                        val x = padX + (i.toFloat() / (n - 1)) * w
                        val y = padY + (1f - (hoursData[i] - vMin) / (vMax - vMin)) * h
                        Offset(x, y)
                    }

                    // draw fill under curve
                    val path = Path()
                    if (points.isNotEmpty()) {
                        path.moveTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            val prev = points[i - 1]
                            val cur = points[i]
                            val midX = (prev.x + cur.x) / 2f
                            val midY = (prev.y + cur.y) / 2f
                            path.quadraticTo(prev.x, prev.y, midX, midY)
                        }
                        val last = points.last()
                        path.lineTo(last.x, last.y)
                        path.lineTo(padX + w, padY + h)
                        path.lineTo(padX, padY + h)
                        path.close()
                    }
                    drawPath(path = path, brush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(activityAccentColor(forecast.dayAvg).copy(alpha = 0.22f), Color.Transparent)), alpha = 0.9f)

                    // draw smooth line over
                    val linePath = Path()
                    if (points.isNotEmpty()) {
                        linePath.moveTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            val prev = points[i - 1]
                            val cur = points[i]
                            val midX = (prev.x + cur.x) / 2f
                            val midY = (prev.y + cur.y) / 2f
                            linePath.quadraticTo(prev.x, prev.y, midX, midY)
                        }
                        val last = points.last()
                        linePath.lineTo(last.x, last.y)
                    }
                    drawPath(path = linePath, color = activityAccentColor(forecast.dayAvg), style = Stroke(width = 3f))

                    // draw markers for prime hours
                    for (hIdx in primeHours) {
                        if (hIdx in 0 until points.size) {
                            val p = points[hIdx]
                            drawCircle(color = activityAccentColor(forecast.dayAvg), radius = 5f, center = p)
                        }
                    }

                    // draw small dots for all points (subtle)
                    for (i in points.indices) {
                        drawCircle(color = Color.White.copy(alpha = 0.06f), radius = 2f, center = points[i])
                    }
                }

                // Tooltip overlay
                val sel = selectedIndex.value
                if (sel != null) {
                    val score = hoursData[sel].roundToInt()
                    val hourLabel = String.format(Locale.getDefault(), "%02d:00", sel)
                    // position tooltip using pixel translations (graphicsLayer)
                    Box(modifier = Modifier
                        .graphicsLayer {
                            translationX = tooltipX.value - 36f
                            translationY = -42f
                        }
                        .background(color = Color(0xFF021428), shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                        .padding(6.dp)) {
                        Column {
                            Text(text = hourLabel, style = MaterialTheme.typography.labelSmall, color = Color(0xFFBFD9FF))
                            Text(text = "Score $score", style = MaterialTheme.typography.bodySmall, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
