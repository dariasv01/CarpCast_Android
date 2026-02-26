package com.david.carpcast.hydrology

import com.david.carpcast.network.FloodOpenMeteoApi
import com.david.carpcast.network.UsgsApi
import com.david.carpcast.network.UsgsSiteApi
import com.david.carpcast.network.GeocodingApi
import com.david.carpcast.repository.FloodParser
import com.david.carpcast.repository.HydrologyParser
import com.david.carpcast.repository.HydrologySample
import com.david.carpcast.repository.HydrologyFetchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HydrologyClient(
    private val usgsApi: UsgsApi,
    private val usgsSiteApi: UsgsSiteApi,
    private val floodApi: FloodOpenMeteoApi,
    private val geocodingApi: GeocodingApi
) {

    // Heurística para determinar si coord está en EEUU usando reverse geocode display_name
    private suspend fun isInUS(lat: Double, lon: Double): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val rev = geocodingApi.reverse(lat = lat, lon = lon)
            rev.display_name.contains("United States", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    // Buscar estaciones NWIS cercanas con parámetro de caudal (00060) y radios incrementales
    private suspend fun findNearbyUsgsSiteIds(lat: Double, lon: Double, radiiKm: List<Int> = listOf(10, 50, 200)): List<String> = withContext(Dispatchers.IO) {
        for (r in radiiKm) {
            try {
                val params = mapOf(
                    "format" to "json",
                    "lat" to lat.toString(),
                    "lon" to lon.toString(),
                    "within" to r.toString(),
                    "parameterCd" to "00060" // streamflow
                )
                val resp = usgsSiteApi.searchSites(params)
                if (!resp.isSuccessful) continue
                val body = resp.body()?.string() ?: continue
                val root = org.json.JSONObject(body)
                val sitesArr = root.optJSONArray("site") ?: continue
                val ids = mutableListOf<String>()
                for (i in 0 until sitesArr.length()) {
                    val s = sitesArr.optJSONObject(i) ?: continue
                    val siteCodeArr = s.optJSONArray("siteCode")
                    if (siteCodeArr != null && siteCodeArr.length() > 0) {
                        val codeObj = siteCodeArr.optJSONObject(0)
                        val id = codeObj?.optString("value")
                        if (!id.isNullOrBlank()) ids.add(id)
                    }
                }
                if (ids.isNotEmpty()) return@withContext ids
            } catch (_: Exception) {
                // try next radius
            }
        }
        return@withContext emptyList()
    }

    // Obtener lecturas por siteIds desde NWIS instant-values y parsear con HydrologyParser
    private suspend fun fetchUsgsInstantValues(siteIdsCommaSeparated: String): List<HydrologySample> = withContext(Dispatchers.IO) {
        try {
            val resp = usgsApi.getInstantValues(sites = siteIdsCommaSeparated)
            if (!resp.isSuccessful) return@withContext emptyList()
            val body = resp.body()?.string() ?: return@withContext emptyList()
            return@withContext HydrologyParser.parseFromUsgsInstantJson(body)
        } catch (_: Exception) {
            return@withContext emptyList()
        }
    }

    // Obtener muestras desde la API Flood (parseadas)
    private suspend fun fetchFloodSamples(lat: Double, lon: Double): List<HydrologySample> = withContext(Dispatchers.IO) {
        try {
            val resp = floodApi.getFlood(latitude = lat, longitude = lon)
            if (!resp.isSuccessful) return@withContext emptyList()
            val body = resp.body()?.string() ?: return@withContext emptyList()
            return@withContext FloodParser.parseFloodJsonToSamples(body)
        } catch (_: Exception) {
            return@withContext emptyList()
        }
    }

    // Helper para combinar listas: preservar USGS samples primero, luego añadir Flood samples que no estén duplicadas por (parameter,time)
    private fun combineSamples(usgs: List<HydrologySample>, flood: List<HydrologySample>): List<HydrologySample> {
        val out = mutableListOf<HydrologySample>()
        out.addAll(usgs)
        val seen = usgs.map { Pair(it.parameter ?: "", it.time ?: "") }.toMutableSet()
        for (f in flood) {
            val key = Pair(f.parameter ?: "", f.time ?: "")
            if (!seen.contains(key)) {
                out.add(f)
                seen.add(key)
            }
        }
        return out
    }

    // Public API: obtener hidrología por coordenadas. Opción B: combinar USGS + Flood cuando sea posible
    suspend fun getHydrologyByLocation(lat: Double, lon: Double): HydrologyFetchResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val inUs = isInUS(lat, lon)
            val floodSamples = fetchFloodSamples(lat, lon)

            if (inUs) {
                val ids = findNearbyUsgsSiteIds(lat, lon)
                if (ids.isEmpty()) {
                    // si no hay estaciones, pero tenemos flood samples, devolverlas
                    if (floodSamples.isNotEmpty()) return@withContext HydrologyFetchResult.Success(floodSamples)
                    return@withContext HydrologyFetchResult.Failure(HydrologyFetchResult.FailureType.NOT_FOUND, "No nearby USGS stations and no flood data")
                }
                val usgsSamples = fetchUsgsInstantValues(ids.joinToString(","))
                val combined = combineSamples(usgsSamples, floodSamples)
                return@withContext HydrologyFetchResult.Success(combined)
            } else {
                if (floodSamples.isNotEmpty()) return@withContext HydrologyFetchResult.Success(floodSamples)
                return@withContext HydrologyFetchResult.Failure(HydrologyFetchResult.FailureType.NOT_FOUND, "No flood data available")
            }
        } catch (t: Throwable) {
            HydrologyFetchResult.Failure(HydrologyFetchResult.FailureType.UNKNOWN, t.message)
        }
    }
}
