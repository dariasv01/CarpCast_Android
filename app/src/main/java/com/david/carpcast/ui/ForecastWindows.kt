package com.david.carpcast.ui

import com.david.carpcast.util.TimeUtils
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

object ForecastWindows {
    /** Complete, consecutive future hours only; a two-hour window ends after its second hour. */
    fun upcoming(hours: List<HourlyEntry>, nowMs: Long, windowHours: Int = 2, limit: Int = 3): List<Pair<String, Int>> {
        require(windowHours > 0 && limit >= 0)
        val sorted = hours.mapNotNull { hour ->
            val ms = TimeUtils.parseTimeMs(hour.time) ?: return@mapNotNull null
            if (ms < nowMs || hour.score == null) null else Triple(ms, hour.time, hour.score)
        }.distinctBy { it.first }.sortedBy { it.first }
        data class Candidate(val start: Long, val end: Long, val label: String, val score: Int)
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        val candidates = sorted.windowed(windowHours).mapNotNull { points ->
            if (points.zipWithNext().any { (a,b) -> b.first - a.first != 3_600_000L }) return@mapNotNull null
            val start = runCatching { OffsetDateTime.parse(points.first().second) }.getOrNull() ?: return@mapNotNull null
            val end = runCatching { OffsetDateTime.parse(points.last().second).plusHours(1) }.getOrNull() ?: return@mapNotNull null
            Candidate(points.first().first, points.last().first + 3_600_000L,
                "${start.format(fmt)} – ${end.format(fmt)}", points.map { it.third }.average().toInt())
        }.sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.start })
        val selected = mutableListOf<Candidate>()
        for (c in candidates) {
            if (selected.size >= limit) break
            if (selected.none { c.start < it.end && c.end > it.start }) selected += c
        }
        return selected.map { it.label to it.score }
    }
}
