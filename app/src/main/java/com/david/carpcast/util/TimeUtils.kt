package com.david.carpcast.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object TimeUtils {
    // Robust ISO datetime parser: acepta Instant.parse, OffsetDateTime, LocalDateTime (assume UTC), and pattern yyyy-MM-dd'T'HH:mm
    // Returns epoch millis or null if cannot parse
    fun parseTimeMs(timeIso: String?): Long? {
        if (timeIso == null) return null
        // 1) intentar parseo como Instant/ISO_INSTANT
        try {
            return Instant.parse(timeIso).toEpochMilli()
        } catch (_: Exception) {
        }
        // 2) intentar OffsetDateTime (con offset como +01:00)
        try {
            val odt = OffsetDateTime.parse(timeIso)
            return odt.toInstant().toEpochMilli()
        } catch (_: Exception) {
        }
        // 3) intentar LocalDateTime sin zona: asumimos UTC
        try {
            val ldt = LocalDateTime.parse(timeIso, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            return ldt.toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (_: Exception) {
        }
        // 4) manejar formato corto yyyy-MM-dd'T'HH:mm
        try {
            val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
            val ldt2 = LocalDateTime.parse(timeIso, fmt)
            return ldt2.toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (_: Exception) {
        }
        return null
    }
}

