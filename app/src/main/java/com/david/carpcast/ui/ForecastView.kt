package com.david.carpcast.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import com.david.carpcast.ui.components.BestWindow
import com.david.carpcast.ui.components.BestWindowsRank
import com.david.carpcast.ui.components.DaySelector
import com.david.carpcast.ui.components.FishingFactor
import com.david.carpcast.ui.components.FishingFactors
import com.david.carpcast.ui.components.HourlyItemCompact
import com.david.carpcast.ui.components.ScoreHero
import kotlin.math.roundToInt
import com.david.carpcast.R
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ForecastView(
    forecast: SimpleForecast?,
    loading: Boolean,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (forecast == null && loading) {
            // simple loading centered
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Surface
        }

        if (forecast == null) {
            // empty state
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = stringResource(id = R.string.forecast_no_data), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onRefresh) { Text(text = stringResource(id = R.string.retry_label)) }
                }
            }
            return@Surface
        }

        // estado local: pestañas de días
        val otherDays = forecast.otherDays ?: emptyList()
        var selectedDayIndex by remember { mutableStateOf(0) }

        // derivar lista combinada de días (hoy + otros)
        val allDays: List<SimpleForecast> = remember(forecast) {
            mutableListOf<SimpleForecast>().apply {
                add(forecast)
                addAll(otherDays)
            }
        }

        // derive selected forecast
        val selectedForecast: SimpleForecast = allDays.getOrNull(selectedDayIndex) ?: forecast

        // derive selected values
        val selectedHourly = selectedForecast.hourly ?: emptyList()
        val selectedBestWindows = selectedForecast.bestWindows ?: emptyList()
        // parse selected best windows into LocalTime ranges once and keep score
        val primeRangesWithScore: List<Triple<LocalTime, LocalTime, Int>> = remember(selectedBestWindows) {
            val list = mutableListOf<Triple<LocalTime, LocalTime, Int>>()
            for (pair in selectedBestWindows) {
                val ranges = parseRangesFromLabel(pair.first)
                for (r in ranges) list.add(Triple(r.first, r.second, pair.second))
            }
            list
        }

        // build a set of exact hour labels (HH:mm) covered by prime ranges for fast exact matching
        val primeHourLabels: Set<String> = remember(primeRangesWithScore) {
            val fmt = DateTimeFormatter.ofPattern("HH:mm")
            val set = mutableSetOf<String>()
            for ((s, e, _) in primeRangesWithScore) {
                var cur = s
                // include start and iterate by hours until passing end; handle crossing-midnight
                while (true) {
                    set.add(cur.format(fmt))
                    if (s <= e) {
                        if (cur >= e) break
                    } else {
                        // crosses midnight: stop when we reach e after wrapping
                        if (cur == e) break
                    }
                    cur = cur.plusHours(1)
                }
            }
            set
        }

        // compute daily activity trend: compare avg of first N vs last N hours
        val trendText: String? = remember(selectedHourly) {
            val N = 3
            if (selectedHourly.size >= 2) {
                val first = selectedHourly.take(N).mapNotNull { it.score }.map { it.toDouble() }
                val last = selectedHourly.takeLast(N).mapNotNull { it.score }.map { it.toDouble() }
                if (first.isEmpty() || last.isEmpty()) return@remember null
                val avgFirst = first.sum() / first.size
                val avgLast = last.sum() / last.size
                val diff = avgLast - avgFirst
                val threshold = 6.0 // points
                when {
                    diff >= threshold -> "Rising"
                    diff <= -threshold -> "Falling"
                    else -> "Stable"
                }
            } else null
        }

        val selectedFactors = listOf(
            FishingFactor(stringResource(id = R.string.hydro_label), selectedForecast.hydroText ?: stringResource(id = R.string.na_label)),
            FishingFactor(stringResource(id = R.string.pressure_label), formatRange(selectedForecast.pressureMin, selectedForecast.pressureMax, stringResource(id = R.string.unit_hpa))),
            FishingFactor(stringResource(id = R.string.moon_label), selectedForecast.moonInfo ?: stringResource(id = R.string.na_label)),
            FishingFactor(stringResource(id = R.string.sunrise_label), selectedForecast.sunrise ?: stringResource(id = R.string.na_label)),
            FishingFactor(stringResource(id = R.string.wind_label), formatRange(selectedForecast.windAvgMin, selectedForecast.windAvgMax, stringResource(id = R.string.unit_kmh)))
        )

        // build day selector items (label, miniScore)
        val daySelectorItems = remember(allDays) {
            allDays.map { sd ->
                val label = sd.generatedAt.split(',').firstOrNull()?.trim() ?: sd.generatedAt
                val score = sd.dayAvg.coerceIn(0,100)
                label to score
            }
        }

        val listState = rememberLazyListState()

        // autoscroll to hourly on day change (small delay to let layout settle)
        LaunchedEffect(selectedDayIndex) {
            // small delay to allow recomposition
            delay(120)
            try {
                listState.animateScrollToItem(6)
            } catch (_: Exception) {}
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                // top bar with back + location + refresh
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onBack() }) {
                        Icon(painter = rememberVectorPainter(Icons.Default.ArrowBack), contentDescription = stringResource(id = R.string.back_desc))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = forecast.locationName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row {
                        Button(onClick = onRefresh) {
                            Icon(painter = rememberVectorPainter(Icons.Default.Refresh), contentDescription = stringResource(id = R.string.refresh_desc))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(id = R.string.refresh_label))
                        }
                    }
                }
            }

            item {
                // Score hero for selected day (animated)
                Crossfade(targetState = selectedForecast) { sf ->
                    ScoreHero(
                        score = sf.dayAvg.coerceIn(0,100),
                        stateText = when {
                            sf.dayAvg >= 75 -> stringResource(id = R.string.excellent_label)
                            sf.dayAvg >= 40 -> stringResource(id = R.string.good_label)
                            else -> stringResource(id = R.string.regular_label)
                        },
                        bestRange = selectedBestWindows.firstOrNull()?.first,
                        confidence = sf.avgConfidencePct,
                        selectedLabel = daySelectorItems.getOrNull(selectedDayIndex)?.first,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                // Fishing factors (from selected)
                FishingFactors(factors = selectedFactors.map { FishingFactor(it.key, it.value) }, modifier = Modifier.fillMaxWidth())
            }

            item {
                HorizontalDivider()
            }

            item {
                // Prime window (destacar)
                val windows = selectedBestWindows.map { BestWindow(it.first, it.second) }
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (windows.isNotEmpty()) {
                        val prime = windows.first()
                        // parse prime label into start/end
                        val timeRegex = "(\\d{1,2}:\\d{2}).*(\\d{1,2}:\\d{2})".toRegex()
                        val m = timeRegex.find(prime.label)
                        val start = m?.groupValues?.get(1) ?: prime.label
                        val end = m?.groupValues?.get(2) ?: ""
                        // title
                        Text(text = stringResource(id = R.string.prime_window_title) + " 🔥", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = stringResource(id = R.string.prime_window_time, start, end), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "${prime.score} / 100", style = MaterialTheme.typography.displaySmall)
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    BestWindowsRank(windows = windows, modifier = Modifier.fillMaxWidth())
                    // DEBUG: show prime hours parsed (small, unobtrusive)
                    val debugSample = primeHourLabels.take(8).joinToString(", ")
                    Text(text = "Prime hours: ${primeHourLabels.size} ${if (debugSample.isNotEmpty()) "· $debugSample" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // DEBUG: show raw best window labels
                    val rawWindows = selectedBestWindows.joinToString(" | ") { it.first }
                    Text(text = "Raw windows: $rawWindows", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Day selector (incluye Hoy + otros días) con mini scores
                DaySelector(items = daySelectorItems, selectedIndex = selectedDayIndex, onSelect = { idx -> selectedDayIndex = idx }, modifier = Modifier.fillMaxWidth())
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = stringResource(id = R.string.hourly_title) + " — " + (daySelectorItems.getOrNull(selectedDayIndex)?.first ?: ""), style = MaterialTheme.typography.titleMedium)
            }

            // Hourly list compact (from selected)
            items(selectedHourly) { hourly ->
                // Expandable hourly row with details wrapped (no prime marker in selector)
                val timeLabel = hourly.time?.let { isoToHHmmOrFallback(it) } ?: "—"
                val tempLabel = hourly.temperature?.let { "${formatDouble(it)}°" } ?: stringResource(id = R.string.na_label)
                val scoreVal = hourly.score ?: 0
                var expanded by remember { mutableStateOf(false) }

                // compute isPrime using exact hour labels set OR range comparison as fallback
                val isPrime = remember(timeLabel, primeHourLabels, primeRangesWithScore) {
                    val padded = timeLabel.padStart(5, '0')
                    if (primeHourLabels.contains(padded)) return@remember true
                    val t = parseLocalTimeSafe(timeLabel) ?: return@remember false
                    primeRangesWithScore.any { (s, e, _) ->
                        if (s <= e) t >= s && t <= e
                        else t >= s || t <= e
                    }
                }

                Column(modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .padding(vertical = 2.dp)
                ) {
                    // compact row (mark as prime only when it belongs to any best window)
                    HourlyItemCompact(hour = timeLabel, temp = tempLabel, score = scoreVal, onClick = { expanded = !expanded }, isPrime = isPrime)
                    if (expanded) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(shape = MaterialTheme.shapes.small, tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = stringResource(id = R.string.confidence_label, hourly.confidencePct?.toString() ?: stringResource(id = R.string.na_label)),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(id = R.string.wind_label_value, hourly.windSpeed?.let { formatDouble(it) } ?: stringResource(id = R.string.na_label), stringResource(id = R.string.unit_kmh)),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(id = R.string.precipitation_value, hourly.precipitation?.let { formatDouble(it) } ?: stringResource(id = R.string.na_label), stringResource(id = R.string.unit_mm)),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(id = R.string.condition_label, hourly.condition ?: stringResource(id = R.string.na_label)),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
            }

            item {
                // Weather summary / detalles (desde selectedForecast) - menos prominente
                Text(text = stringResource(id = R.string.details_title), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = stringResource(id = R.string.temp_min_max, formatDouble(selectedForecast.tempMin), formatDouble(selectedForecast.tempMax), stringResource(id = R.string.unit_celsius)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = stringResource(id = R.string.precipitation_label, formatDouble(selectedForecast.precipSum), stringResource(id = R.string.unit_mm)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = stringResource(id = R.string.clouds_avg_label, formatDouble(selectedForecast.cloudAvg)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}


/* Helpers de formateo sencillos (evitan decimales innecesarios) */

private fun formatDouble(v: Double?): String {
    if (v == null || v.isNaN() || v.isInfinite()) return "—"
    val rounded = (v * 10.0).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) "${rounded.toInt()}" else "$rounded"
}

private fun formatRange(min: Double?, max: Double?, unit: String): String {
    val a = if (min == null || min.isNaN() || min.isInfinite()) null else formatDouble(min)
    val b = if (max == null || max.isNaN() || max.isInfinite()) null else formatDouble(max)
    return when {
        a != null && b != null -> "$a - $b $unit"
        a != null -> "$a $unit"
        b != null -> "$b $unit"
        else -> "—"
    }
}


/* iso helpers (mínimos, reutilizar parseIso del Activity cuando sea posible) */
private fun isoToHHmmOrFallback(s: String): String {
    try {
        // intentamos extraer patrón HH:mm
        val regex = "(\\d{1,2}:\\d{2})".toRegex()
        val m = regex.find(s)
        if (m != null) return m.groupValues[1].padStart(5, '0')
    } catch (_: Exception) {}
    return if (s.length >= 5) s.substring(0, 5) else s
}

// helper: check if time HH:mm is inside range label like 'HH:mm - HH:mm', 'HH:mm–HH:mm', 'HH:mm to HH:mm'
private fun isTimeInRange(time: String, rangeLabel: String?): Boolean {
    if (rangeLabel.isNullOrEmpty()) return false
    try {
        val t = parseLocalTimeSafe(time) ?: return false
        // regex for explicit ranges: start (sep) end. Separator can be '-' en dash or the word 'to'
        val rangeRegex = "(\\d{1,2}:\\d{2})\\s*(?:-|–|to)\\s*(\\d{1,2}:\\d{2})".toRegex(RegexOption.IGNORE_CASE)
        val matches = rangeRegex.findAll(rangeLabel)
        for (m in matches) {
            val sStr = m.groupValues.getOrNull(1)?.padStart(5, '0')
            val eStr = m.groupValues.getOrNull(2)?.padStart(5, '0')
            val s = parseLocalTimeSafe(sStr)
            val e = parseLocalTimeSafe(eStr)
            if (s != null && e != null) {
                if (s <= e) {
                    if (t >= s && t <= e) return true
                } else {
                    // crosses midnight
                    if (t >= s || t <= e) return true
                }
            }
        }
    } catch (_: Exception) {}
    return false
}

private fun parseLocalTimeSafe(s: String?): LocalTime? {
    if (s == null) return null
    try {
        val parts = s.trim().padStart(5, '0').split(":" )
        val hh = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val mm = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return LocalTime.of((hh % 24), mm)
    } catch (_: Exception) {}
    return null
}

// helper: parse range label like 'HH:mm - HH:mm' to LocalTime pair
private fun parseRangeToLocalTimes(rangeLabel: String?): Pair<LocalTime, LocalTime>? {
    if (rangeLabel.isNullOrEmpty()) return null
    try {
        // explicit range: start (sep) end. Separator can be '-' or 'to'
        val rangeRegex = "(\\d{1,2}:\\d{2})\\s*(?:-|–|to)\\s*(\\d{1,2}:\\d{2})".toRegex(RegexOption.IGNORE_CASE)
        val m = rangeRegex.find(rangeLabel)
        if (m != null) {
            val s = parseLocalTimeSafe(m.groupValues.getOrNull(1)?.padStart(5, '0'))
            val e = parseLocalTimeSafe(m.groupValues.getOrNull(2)?.padStart(5, '0'))
            if (s != null && e != null) return s to e
        }
    } catch (_: Exception) {}
    return null
}

// helper: parse all ranges from a label, returns list of (start,end)
private fun parseRangesFromLabel(rangeLabel: String?): List<Pair<LocalTime, LocalTime>> {
    val out = mutableListOf<Pair<LocalTime, LocalTime>>()
    if (rangeLabel.isNullOrEmpty()) return out
    try {
        val rangeRegex = "(\\d{1,2}:\\d{2})\\s*(?:-|–|to)\\s*(\\d{1,2}:\\d{2})".toRegex(RegexOption.IGNORE_CASE)
        val matches = rangeRegex.findAll(rangeLabel)
        for (m in matches) {
            val s = parseLocalTimeSafe(m.groupValues.getOrNull(1)?.padStart(5, '0'))
            val e = parseLocalTimeSafe(m.groupValues.getOrNull(2)?.padStart(5, '0'))
            if (s != null && e != null) out.add(s to e)
        }
    } catch (_: Exception) {}
    return out
}
