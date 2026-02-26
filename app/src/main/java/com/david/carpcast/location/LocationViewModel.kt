package com.david.carpcast.location

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.david.carpcast.model.LocationModel
import com.david.carpcast.network.NominatimResult
import com.david.carpcast.repository.ForecastRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LocationViewModel(
    application: Application,
    private val repo: ForecastRepository
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, ForecastRepository())

    var searchQuery by mutableStateOf("")
        private set

    var searchResults by mutableStateOf<List<LocationModel>>(emptyList())
        private set

    var selectedLocation by mutableStateOf<LocationModel?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    // Favoritos (persistidos en SharedPreferences como JSON simple)
    var favorites by mutableStateOf<List<LocationModel>>(emptyList())
        private set

    private var searchJob: Job? = null
    private val debounceMs = 400L

    private val prefsName = "carpcast_prefs"
    private val favsKey = "favorites"

    init {
        // cargar favoritos desde SharedPreferences
        loadFavorites()
    }

    fun onQueryChanged(q: String) {
        searchQuery = q
        searchJob?.cancel()
        if (q.isBlank()) {
            searchResults = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(debounceMs)
            performSearch(q)
        }
    }

    private suspend fun performSearch(q: String) {
        isLoading = true
        try {
            val results: List<NominatimResult> = repo.geocode(q)
            searchResults = results.mapNotNull { r ->
                val lat = r.lat.toDoubleOrNull() ?: return@mapNotNull null
                val lon = r.lon.toDoubleOrNull() ?: return@mapNotNull null
                LocationModel(r.display_name, lat, lon)
            }
        } catch (_: Throwable) {
            searchResults = emptyList()
        } finally {
            isLoading = false
        }
    }

    fun selectLocation(loc: LocationModel) {
        selectedLocation = loc
        searchQuery = ""
        searchResults = emptyList()
    }

    fun clearSelection() {
        selectedLocation = null
    }

    fun addFavorite(loc: LocationModel) {
        if (favorites.any { it.latitude == loc.latitude && it.longitude == loc.longitude }) return
        favorites = favorites + loc
        saveFavorites()
    }

    fun removeFavorite(loc: LocationModel) {
        favorites = favorites.filterNot { it.latitude == loc.latitude && it.longitude == loc.longitude }
        saveFavorites()
    }

    fun toggleFavorite(loc: LocationModel) {
        if (favorites.any { it.latitude == loc.latitude && it.longitude == loc.longitude }) removeFavorite(loc)
        else addFavorite(loc)
    }

    fun isFavorite(loc: LocationModel?): Boolean {
        if (loc == null) return false
        return favorites.any { it.latitude == loc.latitude && it.longitude == loc.longitude }
    }

    private fun loadFavorites() {
        try {
            val prefs = getApplication<Application>().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            val raw = prefs.getString(favsKey, null) ?: return
            // simple parse assuming JSONArray of objects {name, lat, lon}
            val list = mutableListOf<LocationModel>()
            val arr = org.json.JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name", "fav")
                val lat = o.optDouble("lat", Double.NaN)
                val lon = o.optDouble("lon", Double.NaN)
                if (lat.isFinite() && lon.isFinite()) list.add(LocationModel(name, lat, lon))
            }
            favorites = list
        } catch (_: Exception) {
            // ignore parse errors
        }
    }

    private fun saveFavorites() {
        try {
            val arr = org.json.JSONArray()
            favorites.forEach { f ->
                val o = org.json.JSONObject()
                o.put("name", f.name)
                o.put("lat", f.latitude)
                o.put("lon", f.longitude)
                arr.put(o)
            }
            val prefs = getApplication<Application>().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            prefs.edit().putString(favsKey, arr.toString()).apply()
        } catch (_: Exception) {}
    }
}
