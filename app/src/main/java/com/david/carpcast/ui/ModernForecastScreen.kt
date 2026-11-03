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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.testTag
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
    selectedDay: SimpleForecast? = null,
    generatedAt: String = "",
    speciesId: String = "carp",
    cached: Boolean = false,
    onRecord: () -> Unit = {}
) {
    val selectedDate = rememberSaveable { mutableStateOf(selectedDay?.generatedAt ?: forecast.generatedAt) }
    val now = androidx.compose.runtime.produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(30_000) }
    }
    val days = (listOf(forecast) + forecast.otherDays).distinctBy { it.generatedAt }.sortedBy { it.generatedAt }
    val selected = days.firstOrNull { it.generatedAt == selectedDate.value } ?: forecast
    val windows = remember(selected.hourly, now.value) { ForecastWindows.upcoming(selected.hourly, now.value) }
    val currentForecast = selected.copy(bestWindows = windows)

    Scaffold(containerColor = AppNavy, topBar = {
        TopAppBar(title = {
            Column {
                Text(com.david.carpcast.journal.speciesName(speciesId), style = MaterialTheme.typography.titleMedium)
                Text(forecast.locationName, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(com.david.carpcast.R.string.back_desc)) }
        }, actions = {
            IconButton(onClick = onRefresh, enabled = !isLoadingState.value) { Icon(Icons.Default.Refresh, contentDescription = stringResource(com.david.carpcast.R.string.refresh_desc)) }
        })
    }) { padding ->
        LazyColumn(Modifier.testTag("forecast-list").fillMaxSize().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (isLoadingState.value) item { androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item { ForecastOrigin(generatedAt, cached) }
            item { HeroCard(currentForecast) }
            item { WeatherDayCard(currentForecast) }
            item { PrimeWindowCompact(currentForecast) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    androidx.compose.material3.Button(onClick = onRecord, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !isLoadingState.value) {
                        Text(stringResource(com.david.carpcast.R.string.field_start_here))
                    }
                    Text(stringResource(com.david.carpcast.R.string.field_start_today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { NextDaysRow(days, selected.generatedAt) { selectedDate.value = it.generatedAt } }
            item { DailyActivityCurve(currentForecast, windows.firstOrNull()?.first) }
            if (windows.isNotEmpty()) item { ForecastExplanation(currentForecast) }
            item { HourlyCollapsible(currentForecast, windows.firstOrNull()?.first) }
            item { EnvironmentalFactorsCardCompact(currentForecast) }
            item { ForecastDataCard(currentForecast) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun HeroCard(forecast: SimpleForecast) {
    Card(shape = AppCardShape, colors = CardDefaults.cardColors(containerColor = Color.Transparent), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().background(activityGradientForScore(forecast.dayAvg)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(com.david.carpcast.R.string.field_daily_index), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(forecast.dayAvg.toString(), fontSize = 58.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("/ 100", modifier = Modifier.padding(bottom = 10.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(activityLabelSimple(forecast.dayAvg), style = MaterialTheme.typography.titleMedium, color = activityAccentColor(forecast.dayAvg))
            Text(formatCompactDate(forecast.generatedAt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val windows = provided
    if (windows.isEmpty()) {
        FieldCard { Text(stringResource(com.david.carpcast.R.string.field_no_future_window), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        return
    }
    val primary = windows.first()
    val others = windows.drop(1).take(2)

    Card(shape = AppCardShape, modifier = Modifier.fillMaxWidth(), elevation = AppCardElevation()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(com.david.carpcast.R.string.field_next_window), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(text = primary.second.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text(text = primary.first, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            WindowWeather(forecast, primary.first)
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
                        WindowWeather(forecast, o.first)
                    }
                }
            }
        }
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
                Card(shape = AppCardShape, modifier = Modifier.width(120.dp).height(108.dp).clickable { onSelectDay(d) }, elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 8.dp else 4.dp)) {
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
    val expanded = rememberSaveable { mutableStateOf(false) }
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
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(formatTimeOnly(h.time), style = MaterialTheme.typography.titleSmall)
            WeatherCondition(h)
            Text(h.temperature?.let { String.format(Locale.getDefault(), "%.0f °C", it) } ?: "—", fontWeight = FontWeight.SemiBold)
        }
        WeatherNumbers(listOf(h))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ThinProgress(value = ((h.score ?: 0).coerceIn(0,100) / 100f), modifier = Modifier.weight(1f), prominent = prime)
            Text(stringResource(if (prime) com.david.carpcast.R.string.fire_score else com.david.carpcast.R.string.score, h.score ?: 0), style = MaterialTheme.typography.labelSmall)
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
        return OffsetDateTime.parse(dateStr).toLocalTime()
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
        return OffsetDateTime.parse(dateStr).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
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

    // Fixed scale keeps daily and cross-day variations visually comparable.
    val vMin = 0f
    val vMax = 100f

    // Prime hours set: compute from primeRange string
    val primeHours = mutableSetOf<Int>()
    forecast.bestWindows.forEach { w ->
        val parts = w.first.split("–", "-", "—").map { it.trim() }
        val start = parseToLocalTime(parts.getOrNull(0))
        val end = parseToLocalTime(parts.getOrNull(1))
        if (start != null && end != null) {
            var h = start.hour
            while (true) {
                if (h % 24 == end.hour % 24) break
                primeHours.add(h % 24)
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
            Text(text = stringResource(com.david.carpcast.R.string.field_daily_curve), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

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
                    val padX = 18.dp.toPx()
                    val padY = 12.dp.toPx()
                    val w = size.width - padX * 2f
                    val h = size.height - padY * 2f

                    for (level in listOf(0f, 50f, 100f)) {
                        val y = padY + (1f - level / 100f) * h
                        drawLine(Color.White.copy(alpha = 0.08f), Offset(padX, y), Offset(padX + w, y))
                    }

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
                            Text(text = stringResource(com.david.carpcast.R.string.field_chart_score, score), style = MaterialTheme.typography.bodySmall, color = Color.White)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("00:00", "06:00", "12:00", "18:00", "23:00").forEach { hour ->
                    Text(hour, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(stringResource(com.david.carpcast.R.string.field_chart_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
