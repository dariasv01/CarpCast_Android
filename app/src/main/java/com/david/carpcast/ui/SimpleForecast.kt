package com.david.carpcast.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.david.carpcast.ui.theme.CarpCastTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Alignment
import java.util.Locale
import kotlin.math.min
import java.time.Instant
import java.time.OffsetDateTime
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.david.carpcast.R

data class SimpleForecast(
    val generatedAt: String,
    val locationName: String,
    val dayAvg: Int,
    val bestHourTime: String,
    val bestHourScore: Int,
    val worstHourTime: String,
    val worstHourScore: Int,
    val avgConfidencePct: Int,
    val precipSum: Double,
    val cloudAvg: Double,
    val humidityAvg: Double,
    val tempMin: Double,
    val tempMax: Double,
    val windAvgMin: Double,
    val windAvgMax: Double,
    val windMax: Double,
    val pressureMin: Double,
    val pressureMax: Double,
    val bestWindows: List<Pair<String, Int>>, // Pair<"HH:mm - HH:mm", score>
    val sunrise: String,
    val sunset: String,
    val moonInfo: String,
    val hydroText: String?,
    // Serie horaria para la nueva sección "por horas"
    val hourly: List<HourlyEntry> = emptyList(),
    // Nuevos: lista de otros días para mostrar en la UI
    val otherDays: List<SimpleForecast> = emptyList()
)

// Representa un punto horario simple usado en la UI
data class HourlyEntry(
    val time: String, // ISO o "HH:mm"
    val temperature: Double? = null,
    val precipitation: Double? = null,
    val windSpeed: Double? = null,
    val score: Int? = null,
    val confidencePct: Int? = null,
    // Nuevos campos
    val precipitationProbability: Double? = null, // 0..1
    val cloudCover: Double? = null, // 0..100
    val isDay: Boolean? = null,
    val condition: String? = null, // "clear" | "cloudy" | "rain" | "snow" | etc.
    val windDirectionDeg: Double? = null // grados 0..360
)

// Helper: intenta extraer solo la hora (HH:mm) desde strings ISO o formatos comunes
private fun formatTimeOnly(dateStr: String?): String {
    if (dateStr.isNullOrBlank()) return ""
    val outFmt = DateTimeFormatter.ofPattern("HH:mm")
    // Try Instant (UTC Z)
    try {
        val inst = try { Instant.parse(dateStr) } catch (_: Exception) { null }
        if (inst != null) {
            val z = ZoneId.systemDefault()
            val ldt = LocalDateTime.ofInstant(inst, z)
            return outFmt.format(ldt)
        }
    } catch (_: Exception) {}

    // Try OffsetDateTime
    try {
        val odt = try { OffsetDateTime.parse(dateStr) } catch (_: Exception) { null }
        if (odt != null) {
            val local = odt.atZoneSameInstant(ZoneId.systemDefault()).toLocalTime()
            return outFmt.format(local)
        }
    } catch (_: Exception) {}

    // Try LocalDateTime
    try {
        val ldt = try { LocalDateTime.parse(dateStr) } catch (_: Exception) { null }
        if (ldt != null) return outFmt.format(ldt.toLocalTime())
    } catch (_: Exception) {}

    // Fallback: regex extraction
    val generic = Regex("(\\d{1,2}:\\d{2})(?::\\d{2})?").find(dateStr)
    if (generic != null) return generic.groupValues[1].padStart(5, '0')

    return if (dateStr.length >= 5) dateStr.substring(0,5) else dateStr
}

// intentar parsear una string ISO a LocalTime; devuelve null si falla
private fun parseToLocalTime(dateStr: String?): java.time.LocalTime? {
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
    // Try to extract HH:mm
    val generic = Regex("(\\d{1,2}:\\d{2})(?::\\d{2})?").find(dateStr)
    if (generic != null) {
        val parts = generic.groupValues[1].split(":")
        val hh = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val mm = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return java.time.LocalTime.of(hh % 24, mm)
    }
    return null
}

private fun degToCompass(deg: Double?): String {
    if (deg == null) return ""
    val directions = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
    val idx = ((deg % 360 + 360) % 360 / 22.5).toInt() % 16
    return directions[idx]
}

// Dado forecast.moonInfo (por ejemplo "Fase: 75% · 20%" o "Iluminación: 75%" o "Creciente"),
// devuelve (phaseName, illuminationString?). phaseName en español.
private fun parseMoonPhaseText(moonInfo: String?): Pair<String, String?> {
    if (moonInfo.isNullOrBlank()) return Pair("Desconocida", null)
    val s = moonInfo.lowercase(Locale.getDefault()).trim()
    // Buscar porcentaje de iluminación
    val pctRegex = Regex("(\\d{1,3})%")
    val pctMatch = pctRegex.find(s)
    val illum = pctMatch?.groups?.get(1)?.value?.toIntOrNull()?.let { "$it%" }

    // Mapa extensivo de frases/keywords en inglés -> español (incluye variantes)
    val translations = mapOf(
        "new moon" to "Luna nueva",
        "new" to "Luna nueva",
        "full moon" to "Luna llena",
        "full" to "Luna llena",
        "first quarter" to "Cuarto creciente",
        "last quarter" to "Cuarto menguante",
        "first" to "Cuarto creciente",
        "last" to "Cuarto menguante",
        "quarter" to "Cuarto",
        "waxing gibbous" to "Gibosa creciente",
        "waning gibbous" to "Gibosa menguante",
        "waxing crescent" to "Creciente",
        "waning crescent" to "Menguante",
        "waxing" to "Creciente",
        "waning" to "Menguante",
        "gibbous" to "Gibosa",
        "crescent" to "Creciente",
        "crescent moon" to "Creciente",
        "waxing crescent" to "Creciente",
        "waning crescent" to "Menguante",
        "half moon" to "Media luna",
        "first" to "Cuarto creciente",
        "last" to "Cuarto menguante",
        // Spanish common words -> keep Spanish (idempotente)
        "creciente" to "Creciente",
        "menguante" to "Menguante",
        "gibosa" to "Gibosa",
        "llena" to "Luna llena",
        "nueva" to "Luna nueva",
        "cuarto" to "Cuarto"
    )

    // Primero intentar buscar coincidencias exactas de frases más largas
    val keysByLength = translations.keys.sortedByDescending { it.length }
    for (k in keysByLength) {
        if (s.contains(k)) return Pair(translations[k] ?: translations[k.trim()] ?: k, illum)
    }

    // Si hay un porcentaje, mapearlo a nombre de fase (si no se detectó palabra)
    if (pctMatch != null) {
        val num = pctMatch.groups[1]?.value?.toDoubleOrNull() ?: -1.0
        if (num >= 0) {
            val frac = num / 100.0
            val phase = when {
                frac < 0.0625 -> "Luna nueva"
                frac < 0.1875 -> "Creciente"
                frac < 0.3125 -> "Cuarto creciente"
                frac < 0.4375 -> "Gibosa creciente"
                frac < 0.5625 -> "Luna llena"
                frac < 0.6875 -> "Gibosa menguante"
                frac < 0.8125 -> "Cuarto menguante"
                frac < 0.9375 -> "Menguante"
                else -> "Luna nueva"
            }
            return Pair(phase, illum)
        }
    }

    // Si no se ha reconocido nada, devolver el texto original capitalizado pero
    // intentando transformar palabras sueltas (por ejemplo "waxing" -> "Creciente")
    val tokens = s.split(Regex("[^a-zA-ZáéíóúñÑ0-9]+"))
    for (t in tokens) {
        val mapped = translations[t]
        if (mapped != null) return Pair(mapped, illum)
    }

    // Fallback: devolver original capitalizado (pero preferir palabra en español si detectada)
    val cleaned = moonInfo.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    return Pair(cleaned, illum)
}

@Composable
private fun DaySummaryCard(forecast: SimpleForecast) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(forecast.bestHourTime, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(forecast.locationName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                }
                CircularScore(score = forecast.dayAvg, size = 96.dp)
            }

            Text(stringResource(id = R.string.factors_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow(stringResource(id = R.string.day_avg_label), "${forecast.dayAvg}/100")
                InfoRow(stringResource(id = R.string.best_hour_label), "${forecast.bestHourTime} (${forecast.bestHourScore}/100)")
                InfoRow(stringResource(id = R.string.worst_hour_label), "${forecast.worstHourTime} (${forecast.worstHourScore}/100)")
                InfoRow(stringResource(id = R.string.avg_confidence_label), "${forecast.avgConfidencePct}%")
            }

            Text(stringResource(id = R.string.block_summary_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.WbSunny, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CircularScoreResumen(score: Int, size: androidx.compose.ui.unit.Dp = 56.dp) {
    val sweep = (score.coerceIn(0, 100) / 100f) * 360f
    val stroke = with(LocalDensity.current) { 10.dp.toPx() }
    // Leer colores/brush desde el tema fuera del draw scope
    val arcBackgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val arcBrush = Brush.sweepGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary))
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.toPx()
            val h = size.toPx()
            val diameter = min(w, h)
            val pad = stroke / 2f
            val rect = androidx.compose.ui.geometry.Rect(pad, pad, diameter - pad, diameter - pad)
            // Fondo del anillo adaptado al tema
            drawArc(color = arcBackgroundColor, startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = Offset(rect.left, rect.top), size = Size(rect.width, rect.height), style = Stroke(stroke))
            // Gradiente del anillo usando brush precomputado
            drawArc(
                brush = arcBrush,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(when {
                score >= 80 -> stringResource(id = R.string.excellent_label)
                score >= 60 -> stringResource(id = R.string.good_label)
                score >= 40 -> stringResource(id = R.string.regular_label)
                else -> stringResource(id = R.string.weak_label)
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CircularScore(score: Int, size: androidx.compose.ui.unit.Dp = 80.dp) {
    val sweep = (score.coerceIn(0, 100) / 100f) * 360f
    val stroke = with(LocalDensity.current) { 10.dp.toPx() }
    // Leer colores/brush desde el tema fuera del draw scope
    val arcBackgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val arcBrush = Brush.sweepGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary))
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.toPx()
            val h = size.toPx()
            val diameter = min(w, h)
            val pad = stroke / 2f
            val rect = androidx.compose.ui.geometry.Rect(pad, pad, diameter - pad, diameter - pad)
            // Fondo del anillo adaptado al tema
            drawArc(color = arcBackgroundColor, startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = Offset(rect.left, rect.top), size = Size(rect.width, rect.height), style = Stroke(stroke))
            // Gradiente del anillo usando brush precomputado
            drawArc(
                brush = arcBrush,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(when {
                score >= 80 -> stringResource(id = R.string.excellent_label)
                score >= 60 -> stringResource(id = R.string.good_label)
                score >= 40 -> stringResource(id = R.string.regular_label)
                else -> stringResource(id = R.string.weak_label)
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DayWeatherCard(forecast: SimpleForecast) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeatherMetricBox(title = stringResource(id = R.string.temperature_label), value = "${forecast.tempMin}°C – ${forecast.tempMax}°C", color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.weight(1f))
                WeatherMetricBox(title = stringResource(id = R.string.rain_total_label), value = "${String.format(Locale.US, "%.2f", forecast.precipSum)} mm", color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeatherMetricBoxDos(title = stringResource(id = R.string.wind_label), value = "${String.format(Locale.US, "%.2f",forecast.windAvgMin)} km/h avg · ${forecast.windAvgMax} km/h máx", nota = stringResource(id = R.string.gusts_note, "${forecast.windMax}"), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.weight(1f))
                // evitar plantilla de cadena innecesaria en el argumento de la string resource
                WeatherMetricBoxDos(title = stringResource(id = R.string.pressure_label), value = "${forecast.pressureMin} – ${forecast.pressureMax} hPa", nota = stringResource(id = R.string.variation_note, String.format(Locale.US, "%.2f", forecast.pressureMax - forecast.pressureMin)), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.weight(1f))
            }
            Text(stringResource(id = R.string.n_clouds_humidity, String.format(Locale.getDefault(), "%.2f",forecast.cloudAvg), String.format(Locale.getDefault(), "%.2f",forecast.humidityAvg)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeatherMetricBox(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier
        .clip(RoundedCornerShape(12.dp))
        .background(color)
        .padding(10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun WeatherMetricBoxDos(title: String, value: String, nota: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier
        .clip(RoundedCornerShape(12.dp))
        .background(color)
        .padding(10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(nota, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BestWindowsCard(forecast: SimpleForecast) {
    val gradient = Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary))
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(modifier = Modifier
            .background(gradient, RoundedCornerShape(16.dp))
            .padding(14.dp)
            .fillMaxWidth()
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(id = R.string.best_windows_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    forecast.bestWindows.forEach { (range, score) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                // usar color derivado del tema (onPrimary ya que el contenedor usa gradient primario)
                                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.WbSunny, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(range, color = MaterialTheme.colorScheme.onPrimary)
                            }
                            Text("$score/100", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallInfoRow(forecast: SimpleForecast) {
    // Use IntrinsicSize so the three SmallCards get the same height automatically
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallCard(icon = Icons.Default.WbSunny, title = stringResource(id = R.string.sunrise_label), subtitle = formatTimeOnly(forecast.sunrise), modifier = Modifier.weight(1f))
        // Mostrar fase lunar como texto y (si existe) la iluminación
        val (phaseName, illum) = parseMoonPhaseText(forecast.moonInfo)
        val moonSubtitle = if (illum != null) "$phaseName · ${stringResource(id = R.string.moon_illumination_label)} $illum" else phaseName
        SmallCard(icon = Icons.Default.WbSunny, title = stringResource(id = R.string.moon_label), subtitle = moonSubtitle, modifier = Modifier.weight(1f))
        SmallCard(icon = Icons.Default.Cloud, title = stringResource(id = R.string.hydro_label), subtitle = forecast.hydroText ?: stringResource(id = R.string.na_label), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SmallCard(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .padding(8.dp)
            .fillMaxHeight(),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        // Usar Box para centrar todo, y Column centrada horizontalmente + verticalmente
        Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                Spacer(Modifier.height(6.dp))
                Text(title, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text(subtitle, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun HourlyForecastCard(forecast: SimpleForecast) {
    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(id = R.string.hourly_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            // Mostrar una fila horizontal con tarjetas por hora
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val list = forecast.hourly.take(24)
                items(list.size) { idx ->
                    val h = list[idx]
                    Column(
                        modifier = Modifier
                            .width(84.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(formatTimeOnly(h.time), style = MaterialTheme.typography.labelSmall)
                        // Determinar si es de día: preferir h.isDay, si es null inferir comparando con sunrise/sunset del forecast
                        val isDayEffective: Boolean = h.isDay ?: run {
                            val hourTime = parseToLocalTime(h.time)
                            val sunriseTime = parseToLocalTime(forecast.sunrise)
                            val sunsetTime = parseToLocalTime(forecast.sunset)
                            if (hourTime != null && sunriseTime != null && sunsetTime != null) {
                                // si sunrise <= hour < sunset -> day
                                if (sunriseTime <= sunsetTime) {
                                    hourTime >= sunriseTime && hourTime < sunsetTime
                                } else {
                                    // lugares polares: sunrise after sunset (midnight sun) -> fallback true
                                    true
                                }
                            } else {
                                true // fallback: mostrar día
                            }
                        }

                        // Icono reactivo según condición y isDayEffective
                        val condLower = h.condition?.lowercase(Locale.getDefault())
                        val emoji = when (condLower) {
                            "rain", "showers" -> "🌧️"
                            "snow", "sleet" -> "❄️"
                            "cloudy", "overcast" -> "☁️"
                            "clear-night", "night" -> "🌙"
                            "thunder", "thunderstorm" -> "⛈️"
                            else -> if (!isDayEffective) "🌙" else "☀️"
                        }
                        Text(emoji, style = MaterialTheme.typography.titleMedium)
                        Text(h.temperature?.let { String.format(Locale.getDefault(), "%.1f°C", it) } ?: "--", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)

                        // Mostrar la actividad (score) y la confianza si existen
                        if (h.score != null) {
                            Spacer(Modifier.height(4.dp))
                            Text("${h.score}/100", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            h.confidencePct?.let {
                                Text("${h.cloudCover}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Estado textual: Lloviendo / Nublado / Despejado / Noche
                        val precip = h.precipitation ?: 0.0
                        val precipProb = h.precipitationProbability ?: 0.0
                        val cloud = h.cloudCover ?: -1.0
                        val isRaining = precip > 0.0 || condLower?.contains("rain") == true || condLower?.contains("showers") == true
                        val isCloudy = (cloud >= 50.0) || condLower?.contains("cloud") == true || condLower?.contains("overcast") == true
                        val statusText = when {
                            isRaining -> stringResource(id = R.string.status_raining)
                            isCloudy -> stringResource(id = R.string.status_cloudy)
                            !isDayEffective -> stringResource(id = R.string.status_night)
                            else -> stringResource(id = R.string.status_clear)
                        }
                        Text(statusText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        // Si existe probabilidad de lluvia, mostrarla como pista (ej: Prob: 30%)
                        if (precipProb > 0.0) {
                            Text("${stringResource(id = R.string.prob_rain_label)} ${precipProb}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // Mostrar velocidad y dirección del viento si existen
                        h.windSpeed?.let { ws ->
                            val dir = degToCompass(h.windDirectionDeg)
                            val windText = if (dir.isNotBlank()) String.format(Locale.getDefault(), "Viento: %.0f km/h · %s", ws, dir) else String.format(Locale.getDefault(), "Viento: %.0f km/h", ws)
                            Text(windText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                     }
                 }
             }
         }
     }
 }

 // Mapear nombre de fase a emoji simple
 private fun moonEmojiForPhase(phaseName: String?): String {
     if (phaseName.isNullOrBlank()) return "🌙"
     val s = phaseName.lowercase(Locale.getDefault())
     return when {
         s.contains("llena") -> "🌕"
         s.contains("nueva") || s.contains("nueva") -> "🌑"
         s.contains("creciente") || s.contains("waxing") -> "🌓"
         s.contains("gibbosa") -> "🌔"
         s.contains("menguante") || s.contains("waning") -> "🌗"
         s.contains("cuarto") -> "🌓"
         else -> "🌙"
     }
 }

 // Sección nueva: próximos días (mostrar compactamente varias jornadas)
 @Composable
 private fun UpcomingDaysSection(forecast: SimpleForecast, onDayClick: (SimpleForecast) -> Unit = {}) {
     if (forecast.otherDays.isEmpty()) return
     Column(modifier = Modifier.fillMaxWidth()) {
         Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
             Text(stringResource(id = R.string.upcoming_days_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
//             Text(
//                 "Ver todos",
//                 modifier = Modifier.clickable { /* por ahora no hace nada, puede abrir listado completo */ },
//                 style = MaterialTheme.typography.labelMedium,
//                 color = MaterialTheme.colorScheme.primary
//             )
         }
         Spacer(Modifier.height(8.dp))
         LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
             val list = forecast.otherDays.take(7)
             items(list.size) { idx ->
                 CompactDayCard(day = list[idx], onClick = { onDayClick(list[idx]) })
             }
         }
     }
 }

 private fun formatCompactDate(d: String): String {
     // intentar parsear ISO yyyy-MM-dd o dd/MM/yyyy o ya formateado
     try {
         val ld = java.time.LocalDate.parse(d)
         val fmt = DateTimeFormatter.ofPattern("EEE dd/MM", Locale.getDefault())
         return ld.format(fmt)
     } catch (_: Exception) {}
     // fallback keep original
     return d
 }

 @Composable
 private fun CompactDayCard(day: SimpleForecast, onClick: () -> Unit = {}) {
     Card(
         modifier = Modifier
             .width(200.dp)
             .clickable { onClick() }
             .clip(RoundedCornerShape(12.dp)),
         elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
     ) {
         Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
             Text(formatCompactDate(day.generatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
             Text(day.locationName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
             Spacer(Modifier.height(6.dp))
             Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                 Column {
                     Text("${stringResource(id = R.string.avg_label)} ${day.dayAvg}/100", style = MaterialTheme.typography.bodySmall)
                     Text("${String.format(Locale.getDefault(), "%.0f", day.tempMin)}° / ${String.format(Locale.getDefault(), "%.0f", day.tempMax)}°", style = MaterialTheme.typography.labelSmall)
                 }
                 CircularScoreResumen(score = day.dayAvg, size = 90.dp)
             }
             Spacer(Modifier.height(6.dp))
             // Mostrar fase lunar para ese día (nombre + iluminación si está disponible)
             val (phaseNameDay, illumDay) = parseMoonPhaseText(day.moonInfo)
             val moonEmoji = moonEmojiForPhase(phaseNameDay)
             Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                 Text(moonEmoji, style = MaterialTheme.typography.titleSmall)
                 Column {
                     val moonSubtitleDay = if (illumDay != null) "$phaseNameDay · $illumDay" else phaseNameDay
                     Text(moonSubtitleDay, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                     // pequeñas métricas: precip y nube
                     Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                         Text(stringResource(R.string.precip_emoji_mm, day.precipSum), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                         Text(stringResource(R.string.cloud_emoji_pct, day.cloudAvg), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                     }
                 }
             }
         }
     }
 }

class SimpleForecastPreviewParameterProvider : PreviewParameterProvider<SimpleForecast> {
    override val values = run {
        // base sample
        val base = SimpleForecast(
            generatedAt = "2023-10-27 10:00",
            locationName = "Embalse de Ejemplo",
            dayAvg = 78,
            bestHourTime = "14:00",
            bestHourScore = 92,
            worstHourTime = "04:00",
            worstHourScore = 45,
            avgConfidencePct = 85,
            precipSum = 0.5,
            cloudAvg = 25.0,
            humidityAvg = 60.0,
            tempMin = 15.0,
            tempMax = 25.0,
            windAvgMin = 5.0,
            windAvgMax = 15.0,
            windMax = 25.0,
            pressureMin = 1010.0,
            pressureMax = 1015.0,
            bestWindows = listOf(
                "13:00 - 15:00" to 90,
                "18:00 - 20:00" to 85
            ),
            sunrise = "07:30",
            sunset = "19:45",
            moonInfo = "Creciente",
            hydroText = "Estable",
            hourly = listOf(
                HourlyEntry(time = "2026-02-02T00:00:00Z", temperature = 12.6, precipitation = 0.0, windSpeed = 15.0, score = 46, confidencePct = 75),
                HourlyEntry(time = "2026-02-02T01:00:00Z", temperature = 13.1, precipitation = 0.0, windSpeed = 16.9, score = 45, confidencePct = 85),
                HourlyEntry(time = "2026-02-02T02:00:00Z", temperature = 13.0, precipitation = 0.4, windSpeed = 11.6, score = 51, confidencePct = 85),
                HourlyEntry(time = "2026-02-02T03:00:00Z", temperature = 13.0, precipitation = 0.4, windSpeed = 14.9, score = 51, confidencePct = 85)
            )
        )

        val day2 = base.copy(generatedAt = "2026-02-03", dayAvg = 62, locationName = "Embalse - Día 2")
        val day3 = base.copy(generatedAt = "2026-02-04", dayAvg = 55, locationName = "Embalse - Día 3")

        // attach otherDays to base for preview
        val previewMain = base.copy(otherDays = listOf(day2, day3))
        sequenceOf(previewMain)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES,)
@Composable
private fun ForecastScreenPreview(
    @PreviewParameter(SimpleForecastPreviewParameterProvider::class) forecast: SimpleForecast
) {
    CarpCastTheme {
        ForecastScreen(forecast = forecast)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Light Preview")
@Composable
private fun ForecastScreenPreviewLight(
    @PreviewParameter(SimpleForecastPreviewParameterProvider::class) forecast: SimpleForecast
) {
    CarpCastTheme {
        ForecastScreen(forecast = forecast)
    }
}

@ExperimentalMaterial3Api
@Suppress("DEPRECATION")
@Composable
fun ForecastScreen(forecast: SimpleForecast, onBack: () -> Unit = {}, onRefresh: () -> Unit = {}) {
    // Estado local para la selección de un día de la lista "Próximos días"
    val selectedDayState = remember { mutableStateOf<SimpleForecast?>(null) }
    val currentForecast = selectedDayState.value ?: forecast

    // Acciones de la barra superior: si hay un día seleccionado, el back limpia la selección;
    // si no, llama al callback externo.
    val topBackAction = {
        if (selectedDayState.value != null) selectedDayState.value = null else onBack()
    }
    // Refresh también limpia la selección local antes de delegar
    val topRefreshAction = {
        selectedDayState.value = null
        onRefresh()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().systemBarsPadding(),
        // Force scaffold to use theme background so it follows dark/light automatically
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text(currentForecast.locationName) },
                navigationIcon = {
                    IconButton(onClick = topBackAction) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(id = R.string.back_desc))
                    }
                },
                actions = {
                    IconButton(onClick = topRefreshAction) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(id = R.string.refresh_desc))
                    }
                },
                // Ensure top app bar uses surface token so it adapts to theme
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        // Surface garantiza que el color de fondo del tema se use como fondo real
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(innerPadding)) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(stringResource(id = R.string.summary_day_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        DaySummaryCard(currentForecast)
                    }

                    item {
                        Text(stringResource(id = R.string.meteorology_day_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        DayWeatherCard(currentForecast)
                    }

                    // Sección de próximos días: usamos el forecast original como fuente de otherDays
                    item {
                        Spacer(Modifier.height(8.dp))
                        UpcomingDaysSection(forecast) { day -> selectedDayState.value = day }
                    }

                    // Nueva sección: por horas (muestra hours del day seleccionado o del día principal)
                    item {
                        Spacer(Modifier.height(8.dp))
                        HourlyForecastCard(currentForecast)
                    }

                    item {
                        Spacer(Modifier.height(8.dp))
                        BestWindowsCard(currentForecast)
                    }

                    item {
                        Spacer(Modifier.height(8.dp))
                        SmallInfoRow(currentForecast)
                    }

                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(id = R.string.sources_label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                        Text("${stringResource(id = R.string.generated_label)} ${currentForecast.generatedAt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
 }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastHost(
    forecastState: MutableState<SimpleForecast?>,
    loadingState: MutableState<Boolean>,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Loading view: mostrar SOLO cuando el usuario ha pulsado el botón de refrescar
        AnimatedVisibility(
            visible = loadingState.value,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { fullWidth -> -fullWidth / 2 }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { fullWidth -> -fullWidth })
        ) {
            LoadingScreen()
        }

        // Forecast view (visible cuando forecastState.value != null)
        AnimatedVisibility(
            visible = forecastState.value != null,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth / 2 })
        ) {
            forecastState.value?.let { model ->
                ForecastScreen(forecast = model, onBack = onBack, onRefresh = onRefresh)
            }
        }

        // If forecast not yet loaded and not loading by user, show subtle placeholder
        if (forecastState.value == null && !loadingState.value) {
            // Simple placeholder instructing user to refresh
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(id = R.string.no_forecast_msg), style = MaterialTheme.typography.bodyMedium)
            }
         }
     }
 }

@Composable
private fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(id = R.string.loading_text), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(16.dp))
        LoadingSlider()
    }
 }

@Composable
private fun LoadingSlider() {
    val transition = rememberInfiniteTransition()
    val progressState = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1200, easing = LinearEasing), repeatMode = RepeatMode.Restart)
    )
    val progress = progressState.value

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val thumbColor = MaterialTheme.colorScheme.primary

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .height(10.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(trackColor)
    ) {
        val maxW = this.maxWidth
        val thumbW = 80.dp.coerceAtMost(maxW)
        val xOffsetDp = (maxW - thumbW) * progress

        val xPx = with(LocalDensity.current) { xOffsetDp.toPx() }
        val xDp = with(LocalDensity.current) { xPx.toDp() }

        // thumb: desplazar usando offset en Dp (evita avisos del analizador por llamadas cualificadas)
        Box(
            modifier = Modifier
                .offset(x = xDp)
                .width(thumbW)
                .fillMaxHeight()
                .background(thumbColor, shape = RoundedCornerShape(8.dp))
        )
     }
 }
