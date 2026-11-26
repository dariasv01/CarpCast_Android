package com.david.carpcast.journal

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.david.carpcast.R
import com.david.carpcast.location.OpenStreetMapView
import com.david.carpcast.location.SavedFishingLocations
import com.david.carpcast.model.LocationModel

@Composable
fun JournalLocationPicker(name: String, latitude: Double?, longitude: Double?, enabled: Boolean, onSelect: (LocationModel) -> Unit) {
    val context = LocalContext.current
    val favorites = remember(context) { SavedFishingLocations.read(context) }
    var mapOpen by rememberSaveable { mutableStateOf(false) }
    val pointName = stringResource(R.string.field_map_point)
    val selected = if (latitude != null && longitude != null) LocationModel(name, latitude, longitude) else null
    Text(stringResource(R.string.field_saved_places), style = MaterialTheme.typography.titleSmall)
    if (favorites.isEmpty()) {
        Text(stringResource(R.string.field_no_saved_places), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            favorites.forEach { place ->
                FilterChip(
                    selected = place.latitude == latitude && place.longitude == longitude,
                    onClick = { onSelect(place) }, label = { Text(place.name) }, enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    if (selected != null) Text(stringResource(R.string.field_coordinates, selected.latitude, selected.longitude), style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = { mapOpen = !mapOpen }, enabled = enabled) {
        Text(stringResource(if (mapOpen) R.string.field_hide_map else R.string.field_choose_map))
    }
    if (mapOpen) {
        Text(stringResource(R.string.field_map_hint), style = MaterialTheme.typography.bodySmall)
        // Madrid is only the initial viewport when no saved/selected position exists; never a selection.
        val center = selected ?: favorites.firstOrNull() ?: LocationModel("", 40.4168, -3.7038)
        if (LocalInspectionMode.current) {
            Box(Modifier.fillMaxWidth().height(300.dp)) { Text(stringResource(R.string.map_preview)) }
        } else {
            OpenStreetMapView(location = center, modifier = Modifier.fillMaxWidth().height(300.dp),
                zoom = if (selected != null || favorites.isNotEmpty()) 13 else 6,
                markers = (favorites + listOfNotNull(selected)).distinctBy { it.latitude to it.longitude },
                onMapClick = { if (enabled) onSelect(it.copy(name = pointName)) },
                onMarkerClick = { if (enabled) onSelect(it) })
        }
    }
}
