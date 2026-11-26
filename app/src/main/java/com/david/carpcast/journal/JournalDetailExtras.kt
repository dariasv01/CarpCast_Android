package com.david.carpcast.journal

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.david.carpcast.R
import com.david.carpcast.location.OpenStreetMapView
import com.david.carpcast.model.LocationModel
import com.david.carpcast.ui.*
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SessionMap(session: FishingSession) {
    FieldCard {
        Text(stringResource(R.string.field_session_map), style = MaterialTheme.typography.titleMedium)
        if (session.latitude != null && session.longitude != null) {
            val place = LocationModel(session.locationName, session.latitude, session.longitude)
            if (LocalInspectionMode.current) Box(Modifier.fillMaxWidth().height(240.dp)) { Text(stringResource(R.string.map_preview)) }
            else OpenStreetMapView(location = place, modifier = Modifier.fillMaxWidth().height(240.dp), zoom = 15)
            Text(stringResource(R.string.field_coordinates, place.latitude, place.longitude), style = MaterialTheme.typography.bodySmall)
        } else Text(stringResource(R.string.field_map_missing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CatchForecastDetails(session: FishingSession, record: CatchRecord) {
    val forecast = remember(session.forecastJson, session.historicalForecastJson, record.atMs, record.species) { forecastAtCatch(session.forecastJson ?: session.historicalForecastJson, record.atMs, record.species) }
    Text(stringResource(if (forecast?.historical == true) R.string.field_history_title else R.string.field_catch_forecast), style = MaterialTheme.typography.titleSmall)
    if (forecast == null) {
        Text(stringResource(if (session.forecastJson == null) R.string.field_catch_forecast_missing else R.string.field_catch_forecast_outside),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val hour = forecast.hour
    val range = runCatching {
        val start = OffsetDateTime.parse(hour.time)
        val fmt = DateTimeFormatter.ofPattern("HH:mm XXX")
        "${start.format(fmt)} – ${start.plusHours(1).format(fmt)}"
    }.getOrDefault(hour.time)
    Text(stringResource(R.string.field_catch_forecast_hour, range), style = MaterialTheme.typography.bodySmall)
    hour.score?.let { Text(stringResource(R.string.field_catch_forecast_index, speciesName(forecast.species), it), color = activityAccentColor(it)) }
    if (hour.score == null) Text(stringResource(R.string.field_no_species_index, speciesName(record.species)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    WeatherCondition(hour)
    hour.temperature?.let { Text(String.format(Locale.getDefault(), "%.1f °C", it), style = MaterialTheme.typography.bodyMedium) }
    WeatherNumbers(listOf(hour))
    hour.pressure?.let { Text(stringResource(R.string.pressure_short_label) + ": " + String.format(Locale.getDefault(), "%.0f hPa", it), style = MaterialTheme.typography.bodySmall) }
    if (forecast.generatedAt.isNotBlank()) ForecastOrigin(forecast.generatedAt, false)
    if (forecast.savedAfterCatch && !forecast.historical) Text(stringResource(R.string.field_catch_forecast_late), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    Text(stringResource(if (forecast.historical) R.string.field_history_note else R.string.field_catch_forecast_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
