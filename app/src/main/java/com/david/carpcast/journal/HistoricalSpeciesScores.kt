package com.david.carpcast.journal

import com.google.gson.JsonParser
import org.json.JSONObject

private val scoredSpecies = setOf("carp", "barbel", "bass", "pike", "catfish")

/** Adds missing species indices while retaining the original weather, dates and selected-species score. */
fun mergeHistoricalSpeciesScores(original: String, calculated: String): String {
    val base = JSONObject(original)
    val fresh = JSONObject(calculated)
    require(base.optString("source") == "historical_reanalysis" && fresh.optString("source") == "historical_reanalysis")
    require(base.getDouble("lat") == fresh.getDouble("lat") && base.getDouble("lon") == fresh.getDouble("lon"))
    val source = fresh.getJSONArray("forecasts")
    val byTime = (0 until source.length()).associate { source.getJSONObject(it).getString("time") to source.getJSONObject(it) }
    val points = base.getJSONArray("forecasts")
    for (i in 0 until points.length()) {
        val point = points.getJSONObject(i)
        val scores = requireNotNull(byTime[point.getString("time")]).getJSONObject("activityBySpecies")
        val existing = point.optJSONObject("activityBySpecies") ?: JSONObject()
        val selected = base.optString("species")
        if (selected in scoredSpecies && !existing.has(selected) && point.optJSONObject("activity") != null) {
            existing.put(selected, JSONObject().put("overall", point.getJSONObject("activity").getInt("overall")))
        }
        for (species in scoredSpecies) {
            val value = scores.getJSONObject(species)
            require(value.getInt("overall") in 0..100)
            if (!existing.has(species)) existing.put(species, value)
        }
        point.put("activityBySpecies", existing)
    }
    return base.toString()
}

fun onlyAddsSpeciesScores(original: String, updated: String): Boolean = runCatching {
    val old = JSONObject(original)
    val next = JSONObject(updated)
    val oldPoints = old.getJSONArray("forecasts")
    val nextPoints = next.getJSONArray("forecasts")
    require(oldPoints.length() == nextPoints.length())
    for (i in 0 until oldPoints.length()) {
        val before = oldPoints.getJSONObject(i)
        val after = nextPoints.getJSONObject(i)
        val existing = before.optJSONObject("activityBySpecies")
        val added = after.optJSONObject("activityBySpecies")
        if (added != null) for (key in added.keys()) {
            require(key in scoredSpecies && added.getJSONObject(key).getInt("overall") in 0..100)
        }
        if (existing != null) for (key in existing.keys()) {
            require(added != null && JsonParser.parseString(existing.get(key).toString()) == JsonParser.parseString(added.get(key).toString()))
        }
        before.remove("activityBySpecies")
        after.remove("activityBySpecies")
    }
    JsonParser.parseString(old.toString()) == JsonParser.parseString(next.toString())
}.getOrDefault(false)
