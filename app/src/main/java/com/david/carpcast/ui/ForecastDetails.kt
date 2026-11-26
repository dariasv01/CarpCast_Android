package com.david.carpcast.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.david.carpcast.R
import kotlin.math.abs

@Composable
fun ForecastOrigin(generatedAt: String, cached: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.field_updated, formatForecastTimestamp(generatedAt)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (cached) Text(stringResource(R.string.field_cached_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun ForecastExplanation(forecast: SimpleForecast) {
    val start = forecast.bestWindows.firstOrNull()?.first?.take(5)
    val hour = forecast.hourly.firstOrNull { it.time.substringAfter('T').take(5) == start }
    val factors = hour?.influences?.groupBy { it.key }?.map { (key, items) -> key to items.sumOf { it.contribution } }
        ?.filter { abs(it.second) > 0.05 }?.sortedByDescending { abs(it.second) }?.take(3).orEmpty()
    FieldCard {
        Text(stringResource(R.string.field_why), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (factors.isEmpty()) Text(stringResource(R.string.field_why_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        else {
            Text(stringResource(R.string.field_why_detail, start ?: "—"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            factors.forEach { (key, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(featureLabel(key)), modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(if (value > 0) R.string.field_favors else R.string.field_hurts),
                        color = if (value > 0) AppAccentGreen else AppAccentRed, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun ForecastDataCard(forecast: SimpleForecast) {
    val sources = forecast.hourly.mapNotNull { it.waterTempSource }.toSet()
    val sourceLabel = when {
        sources == setOf("measured") -> R.string.water_measured
        sources.contains("measured") -> R.string.water_mixed
        sources.contains("estimated") -> R.string.water_estimated
        else -> R.string.water_unknown
    }
    FieldCard {
        Text(stringResource(R.string.field_data_sources), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.confidence_compact, forecast.avgConfidencePct), color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.field_weather_source))
        Text(stringResource(sourceLabel))
        Text(stringResource(R.string.field_missing_data), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.data_quality_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun featureLabel(key: String): Int = when(key) {
    "wind" -> R.string.field_feature_wind
    "gustRatio" -> R.string.field_feature_gustRatio
    "cloud" -> R.string.field_feature_cloud
    "layers" -> R.string.field_feature_layers
    "precip" -> R.string.field_feature_precip
    "pop" -> R.string.field_feature_pop
    "pressure" -> R.string.field_feature_pressure
    "ptrend3h" -> R.string.field_feature_ptrend3h
    "temp" -> R.string.field_feature_temp
    "rh" -> R.string.field_feature_rh
    "tdSpread" -> R.string.field_feature_tdSpread
    "radiation" -> R.string.field_feature_radiation
    "dawn" -> R.string.field_feature_dawn
    "dP" -> R.string.field_feature_dP
    "dPmean" -> R.string.field_feature_dPmean
    "dT" -> R.string.field_feature_dT
    "rain" -> R.string.field_feature_rain
    "rainDays" -> R.string.field_feature_rainDays
    "wstab" -> R.string.field_feature_wstab
    else -> R.string.field_feature_other
}
