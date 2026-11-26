package com.david.carpcast.location

import android.content.Context
import com.david.carpcast.model.LocationModel
import org.json.JSONArray

/** The same favorites used by Home and the fishing journal, including existing installations. */
object SavedFishingLocations {
    fun read(context: Context): List<LocationModel> = runCatching {
        val raw = context.getSharedPreferences("carpcast_prefs", Context.MODE_PRIVATE).getString("favorites", null)
            ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val value = array.optJSONObject(index) ?: return@mapNotNull null
            val lat = value.optDouble("lat", Double.NaN)
            val lon = value.optDouble("lon", Double.NaN)
            if (!lat.isFinite() || lat !in -90.0..90.0 || !lon.isFinite() || lon !in -180.0..180.0) null
            else LocationModel(value.optString("name", ""), lat, lon)
        }
    }.getOrDefault(emptyList())
}
