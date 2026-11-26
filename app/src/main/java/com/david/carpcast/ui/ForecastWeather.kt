package com.david.carpcast.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.david.carpcast.R
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

enum class Sky { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM, UNKNOWN }

/** WMO codes take precedence. Older offline forecasts use observed precipitation/cloud cover. */
fun skyFor(hour: HourlyEntry): Sky = when (hour.weatherCode) {
    0 -> Sky.CLEAR
    1, 2 -> Sky.PARTLY
    3 -> Sky.CLOUDY
    45, 48 -> Sky.FOG
    51, 53, 55, 56, 57 -> Sky.DRIZZLE
    61, 63, 65, 66, 67, 80, 81, 82 -> Sky.RAIN
    71, 73, 75, 77, 85, 86 -> Sky.SNOW
    95, 96, 99 -> Sky.STORM
    else -> when {
        (hour.precipitation ?: 0.0) > 0.0 -> Sky.RAIN
        hour.cloudCover != null -> when {
            hour.cloudCover >= 80 -> Sky.CLOUDY
            hour.cloudCover >= 20 -> Sky.PARTLY
            else -> Sky.CLEAR
        }
        else -> Sky.UNKNOWN
    }
}

@Composable
fun WeatherCondition(hour: HourlyEntry) {
    val sky = skyFor(hour)
    val label = stringResource(when (sky) {
        Sky.CLEAR -> R.string.wx_clear
        Sky.PARTLY -> R.string.wx_partly
        Sky.CLOUDY -> R.string.wx_cloudy
        Sky.FOG -> R.string.wx_fog
        Sky.DRIZZLE -> R.string.wx_drizzle
        Sky.RAIN -> R.string.wx_rain
        Sky.SNOW -> R.string.wx_snow
        Sky.STORM -> R.string.wx_storm
        Sky.UNKNOWN -> R.string.wx_unknown
    })
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WeatherGlyph(sky, hour.isDay != false)
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.widthIn(max = 150.dp))
    }
}

/** Code-native icons scale cleanly and need no network image downloads. Text supplies accessibility. */
@Composable
private fun WeatherGlyph(sky: Sky, day: Boolean) {
    Canvas(Modifier.size(36.dp)) {
        val u = size.width / 36f
        fun point(x: Float, y: Float) = Offset(x*u, y*u)
        val sun = Color(0xFFFFCE64)
        val cloud = Color(0xFFC5D9EC)
        val water = Color(0xFF78C4FF)
        if (sky == Sky.UNKNOWN) {
            drawCircle(cloud, 10*u, center, style = Stroke(2*u))
            return@Canvas
        }
        if (sky == Sky.CLEAR || sky == Sky.PARTLY) {
            val c = point(if (sky == Sky.CLEAR) 18f else 12f, if (sky == Sky.CLEAR) 18f else 12f)
            if (day) {
                drawCircle(sun, 6*u, c)
                for (i in 0..7) {
                    val a = i*Math.PI/4
                    drawLine(sun, c+Offset(cos(a).toFloat()*9*u,sin(a).toFloat()*9*u), c+Offset(cos(a).toFloat()*12*u,sin(a).toFloat()*12*u), 2*u)
                }
            } else {
                drawArc(sun, 45f, 270f, false, c-Offset(8*u,8*u), Size(16*u,16*u), style = Stroke(4*u))
            }
        }
        if (sky != Sky.CLEAR) {
            drawCircle(cloud, 7*u, point(19f,16f))
            drawCircle(cloud, 5*u, point(11f,20f))
            drawCircle(cloud, 6*u, point(26f,20f))
            drawRect(cloud, point(11f,19f), Size(15*u,7*u))
            when (sky) {
                Sky.RAIN, Sky.DRIZZLE -> for (x in listOf(12f,20f,28f)) drawLine(water, point(x,29f),point(x-2,33f),2*u)
                Sky.SNOW -> for (x in listOf(12f,20f,28f)) drawCircle(Color.White, 1.7f*u,point(x,31f))
                Sky.FOG -> for (y in listOf(29f,33f)) drawLine(cloud,point(7f,y),point(30f,y),1.5f*u)
                Sky.STORM -> { drawLine(sun,point(21f,26f),point(16f,31f),3*u); drawLine(sun,point(16f,31f),point(22f,31f),3*u); drawLine(sun,point(22f,31f),point(18f,36f),3*u) }
                else -> Unit
            }
        }
    }
}

private fun number(value: Double?, unit: String, decimals: Int = 0): String =
    value?.takeIf { it.isFinite() }?.let { String.format(Locale.getDefault(), "%.${decimals}f %s", it, unit) } ?: "—"

@Composable
fun WeatherNumbers(hours: List<HourlyEntry>) {
    val pop = hours.mapNotNull { it.precipitationProbability }.maxOrNull()?.times(100)
    val precip = hours.mapNotNull { it.precipitation }.takeIf { it.isNotEmpty() }?.sum()
    val wind = hours.mapNotNull { it.windSpeed }.maxOrNull()
    val gust = hours.mapNotNull { it.windGust }.maxOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.wx_probability, number(pop, "%")), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.wx_amount, number(precip, "mm", 1)), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.wx_wind, number(wind, "km/h")) + " · " + stringResource(R.string.wx_gust, number(gust, "km/h")), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun WeatherDayCard(forecast: SimpleForecast) {
    val hours = forecast.hourly
    // Most frequent daylight condition; exceptional rain/snow/storm also remains visible hourly.
    val daylight = hours.filter { it.isDay == true }.ifEmpty { hours }
    val representative = daylight.groupBy(::skyFor).maxByOrNull { it.value.size }?.value?.firstOrNull()
    FieldCard {
        Text(stringResource(R.string.wx_title), style = MaterialTheme.typography.titleMedium)
        if (representative != null) WeatherCondition(representative)
        else Text(stringResource(R.string.wx_unknown))
        val temps = hours.mapNotNull { it.temperature }
        Text(stringResource(R.string.wx_range, number(temps.minOrNull(), "°C"), number(temps.maxOrNull(), "°C")), style = MaterialTheme.typography.titleSmall)
        WeatherNumbers(hours)
        Text(stringResource(R.string.wx_daily_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun WindowWeather(forecast: SimpleForecast, label: String) {
    val start = label.substringBefore(" – ")
    val index = forecast.hourly.indexOfFirst { hour ->
        runCatching { java.time.OffsetDateTime.parse(hour.time).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) == start }.getOrDefault(false)
    }
    if (index >= 0) {
        val hours = forecast.hourly.drop(index).take(2)
        val representative = hours.maxByOrNull { when (skyFor(it)) {
            Sky.STORM -> 8; Sky.SNOW -> 7; Sky.RAIN -> 6; Sky.DRIZZLE -> 5; Sky.FOG -> 4; Sky.CLOUDY -> 3; Sky.PARTLY -> 2; Sky.CLEAR -> 1; Sky.UNKNOWN -> 0
        } }
        if (representative != null) WeatherCondition(representative)
        WeatherNumbers(hours)
    }
}
