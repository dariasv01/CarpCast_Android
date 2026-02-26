package com.david.carpcast.repository

import org.json.JSONObject

// Parser y DTO para respuestas NWIS instant-values (USGS)
data class HydrologySample(
    val siteId: String?,
    val parameter: String?,
    val value: Double?,
    val unit: String?,
    val time: String?
)

object HydrologyParser {
    /**
     * Parsea el JSON de USGS NWIS `instant-values` y devuelve una lista de HydrologySample.
     * Si el JSON no contiene la estructura esperada devuelve lista vacía.
     */
    fun parseFromUsgsInstantJson(json: String): List<HydrologySample> {
        try {
            val root = JSONObject(json)
            val valueObj = root.optJSONObject("value") ?: return emptyList()
            val tsArray = valueObj.optJSONArray("timeSeries") ?: return emptyList()

            val out = mutableListOf<HydrologySample>()
            for (i in 0 until tsArray.length()) {
                val ts = tsArray.optJSONObject(i) ?: continue
                val sourceInfo = ts.optJSONObject("sourceInfo")
                var siteId: String? = null
                if (sourceInfo != null) {
                    val siteCodes = sourceInfo.optJSONArray("siteCode")
                    if (siteCodes != null && siteCodes.length() > 0) {
                        siteId = siteCodes.optJSONObject(0)?.optString("value")
                    }
                }

                val variable = ts.optJSONObject("variable")
                val parameter = variable?.optString("variableName")
                val unit = variable?.optJSONObject("unit")?.optString("unitCode")

                // values -> array de objetos; cada uno tiene "value" array con lecturas
                var readingValue: Double? = null
                var readingTime: String? = null
                val valuesArr = ts.optJSONArray("values")
                if (valuesArr != null && valuesArr.length() > 0) {
                    val firstVals = valuesArr.optJSONObject(0)?.optJSONArray("value")
                    if (firstVals != null && firstVals.length() > 0) {
                        // seleccionar la última lectura (más reciente)
                        val vObj = firstVals.optJSONObject(firstVals.length() - 1)
                        if (vObj != null) {
                            val vStr = vObj.optString("value")
                            readingValue = vStr.toDoubleOrNull()
                            readingTime = vObj.optString("dateTime")
                        }
                    }
                }

                out.add(HydrologySample(siteId, parameter, readingValue, unit, readingTime))
            }

            return out
        } catch (_: Exception) {
            return emptyList()
        }
    }
}
