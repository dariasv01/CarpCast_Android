package com.david.carpcast.repository

import org.json.JSONArray
import org.json.JSONObject

/**
 * Parser flexible para la API Open-Meteo Flood. Intenta extraer observaciones
 * (time, value) desde varias estructuras conocidas; si no puede, devuelve
 * un sample con parameter="flood_raw" y el JSON crudo en `time`.
 */
object FloodParser {
    fun parseFloodJsonToSamples(json: String): List<HydrologySample> {
        try {
            val root = JSONObject(json)

            // Buscar arrays comunes: "observations", "data", "timeseries", "stations"
            val candidateKeys = listOf("observations", "data", "timeseries", "timeSeries", "stations", "features")
            for (key in candidateKeys) {
                if (root.has(key)) {
                    val arr = root.optJSONArray(key) ?: continue
                    val res = parseArrayForObservations(arr)
                    if (res.isNotEmpty()) return res
                }
            }

            // También si root tiene "features" estilo GeoJSON
            if (root.has("features")) {
                val f = root.optJSONArray("features")
                if (f != null) {
                    val res = parseGeoJsonFeatures(f)
                    if (res.isNotEmpty()) return res
                }
            }

            // Intentar buscar en objetos anidados de primer nivel con arrays
            val names = root.keys()
            while (names.hasNext()) {
                val k = names.next()
                val o = root.opt(k)
                if (o is JSONArray) {
                    val res = parseArrayForObservations(o)
                    if (res.isNotEmpty()) return res
                }
            }

            // Fallback: devolver JSON crudo como single sample
            return listOf(HydrologySample(siteId = null, parameter = "flood_raw", value = null, unit = null, time = json))
        } catch (_: Exception) {
            return listOf(HydrologySample(siteId = null, parameter = "flood_raw", value = null, unit = null, time = json))
        }
    }

    private fun parseArrayForObservations(arr: JSONArray): List<HydrologySample> {
        val out = mutableListOf<HydrologySample>()
        for (i in 0 until arr.length()) {
            val el = arr.optJSONObject(i) ?: continue

            // Si el elemento tiene una propiedad 'properties', usarla
            val node = if (el.has("properties") && el.optJSONObject("properties") != null) el.optJSONObject("properties") else el

            // Hora
            val time: String? = when {
                node.has("time") -> node.optString("time")
                node.has("timestamp") -> node.optString("timestamp")
                node.has("dateTime") -> node.optString("dateTime")
                else -> null
            }

            // Detectar keys conocidas para valor y configurar parameter según la key encontrada
            val knownKeys = listOf("water_level", "level", "stage", "value", "waterLevel", "observed", "flood_risk", "risk_level", "probability")
            var param: String? = null
            var value: Double? = null

            for (k in knownKeys) {
                if (node.has(k)) {
                    param = k
                    // algunos nodos pueden ser objetos: intentar sacar campo 'value' interno
                    val candidate = node.opt(k)
                    when (candidate) {
                        is Number -> value = candidate.toDouble()
                        is String -> value = (candidate as String).toDoubleOrNull()
                        is JSONObject -> {
                            if (candidate.has("value")) value = candidate.optString("value").toDoubleOrNull()
                            else if (candidate.has("level")) value = candidate.optString("level").toDoubleOrNull()
                        }
                    }
                    break
                }
            }

            // Si no se encontró una key conocida, intentar 'value' genérico
            if (param == null && node.has("value")) {
                param = "value"
                value = node.optString("value").toDoubleOrNull()
            }

            // Unidad (si existe)
            val unit: String? = when {
                node.has("unit") -> node.optString("unit")
                node.has("uom") -> node.optString("uom")
                node.has("unitCode") -> node.optString("unitCode")
                else -> null
            }

            // site id / station id
            val siteId: String? = when {
                node.has("station_id") -> node.optString("station_id")
                node.has("station") -> node.optString("station")
                node.has("id") -> node.optString("id")
                else -> null
            }

            if (time != null || value != null) {
                out.add(HydrologySample(siteId = siteId, parameter = param ?: "flood", value = value, unit = unit, time = time))
            }
        }
        return out
    }

    private fun parseGeoJsonFeatures(features: JSONArray): List<HydrologySample> {
        val out = mutableListOf<HydrologySample>()
        for (i in 0 until features.length()) {
            val f = features.optJSONObject(i) ?: continue
            val props = f.optJSONObject("properties") ?: continue
            // check properties for observations
            val time: String? = when {
                props.has("time") -> props.optString("time")
                props.has("timestamp") -> props.optString("timestamp")
                else -> null
            }
            // detect known keys
            val knownKeys = listOf("water_level", "level", "stage", "value", "waterLevel", "observed", "flood_risk", "risk_level", "probability")
            var param: String? = null
            var value: Double? = null
            for (k in knownKeys) {
                if (props.has(k)) {
                    param = k
                    val candidate = props.opt(k)
                    when (candidate) {
                        is Number -> value = candidate.toDouble()
                        is String -> value = (candidate as String).toDoubleOrNull()
                        is JSONObject -> {
                            if (candidate.has("value")) value = candidate.optString("value").toDoubleOrNull()
                            else if (candidate.has("level")) value = candidate.optString("level").toDoubleOrNull()
                        }
                    }
                    break
                }
            }
            val unit: String? = if (props.has("unit")) props.optString("unit") else if (props.has("uom")) props.optString("uom") else null
            val siteId: String? = if (props.has("station")) props.optString("station") else props.optString("id", null)
            if (time != null || value != null) {
                out.add(HydrologySample(siteId = siteId, parameter = param ?: "flood", value = value, unit = unit, time = time))
            }
        }
        return out
    }
}

// Resultado de la obtención de hidrología (éxito con muestras o fallo con razón)
sealed class HydrologyFetchResult {
    data class Success(val samples: List<HydrologySample>) : HydrologyFetchResult()
    data class Failure(val type: FailureType, val message: String?) : HydrologyFetchResult()

    enum class FailureType {
        NETWORK, NOT_FOUND, PARSE, TIMEOUT, UNKNOWN
    }
}
