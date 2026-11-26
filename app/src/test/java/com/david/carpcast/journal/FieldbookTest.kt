package com.david.carpcast.journal

import com.david.carpcast.ui.ForecastWindows
import com.david.carpcast.ui.HourlyEntry
import com.david.carpcast.scoring.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class FieldbookTest {
    private val start = Instant.parse("2026-01-01T10:00:00Z").toEpochMilli()

    @Test fun zeroCatchSessionRetainsEffortAndCountsAsZero() {
        val session = FishingSession(startedAtMs = start, endedAtMs = start + 4 * 3_600_000L, locationName = "Test", rods = 2)
        session.validate()
        assertEquals(8.0, session.rodHours()!!, 0.0)
        assertEquals(0.0, session.catchesPerRodHour()!!, 0.0)
    }

    @Test fun manualCaptureTimeRequiresValidHoursAndMinutes() {
        assertEquals(java.time.LocalTime.of(23, 59), parseCaptureClock("23:59"))
        assertEquals(java.time.LocalTime.MIDNIGHT, parseCaptureClock("00:00"))
        for (invalid in listOf("24:00", "12:60", "", "10:35:12", "9:00")) assertNull(parseCaptureClock(invalid))
    }

    @Test fun manualDateAcceptsCalendarFormatAndRejectsInvalidDates() {
        assertEquals(java.time.LocalDate.of(2026, 10, 25), parseJournalDate("25/10/2026"))
        assertEquals(java.time.LocalDate.of(2026, 10, 25), parseJournalDate("2026-10-25"))
        assertNull(parseJournalDate("31/02/2026"))
        assertNull(parseJournalDate("invalid"))
    }

    @Test fun futureAndReversedSessionDatesAreRejected() {
        val now = System.currentTimeMillis()
        val session = FishingSession(startedAtMs = now - 3_600_000, locationName = "Test")
        assertTrue(runCatching { session.copy(startedAtMs = now + 60_000).validate() }.isFailure)
        assertTrue(runCatching { session.copy(endedAtMs = now + 60_000).validate() }.isFailure)
        assertTrue(runCatching { session.copy(endedAtMs = session.startedAtMs).validate() }.isFailure)
    }

    @Test fun activeSessionsDoNotReportFinalCaptureRate() {
        val session = FishingSession(startedAtMs = start, locationName = "Test", rods = 2)
        assertNull(session.rodHours())
        assertNull(session.catchesPerRodHour())
    }

    @Test fun captureOutsideSessionIsRejected() {
        val session = FishingSession(startedAtMs = start, endedAtMs = start + 3_600_000L, locationName = "Test",
            catches = listOf(CatchRecord(atMs = start - 1)))
        assertTrue(runCatching { session.validate() }.isFailure)
        assertTrue(runCatching { session.copy(catches = listOf(CatchRecord(atMs = start + 3_600_001L))).validate() }.isFailure)
    }

    @Test fun invalidEffortAndNonfiniteMeasurementsAreRejected() {
        val session = FishingSession(startedAtMs = start, locationName = "Test")
        assertTrue(runCatching { session.copy(rods = 0).validate() }.isFailure)
        assertTrue(runCatching { session.copy(depthM = Double.NaN).validate() }.isFailure)
    }

    @Test fun recommendationNeverUsesPastHoursOrIncompleteTail() {
        val hours = (0..3).map { i -> HourlyEntry("2026-01-01T${10+i}:00:00Z", score = if (i == 0) 100 else 50) }
        val windows = ForecastWindows.upcoming(hours, start + 30 * 60_000L)
        assertEquals(listOf("11:00 – 13:00" to 50), windows)
        assertTrue(ForecastWindows.upcoming(hours, start + 3 * 3_600_000L).isEmpty())
    }

    @Test fun recommendationsDoNotBridgeMissingHoursOrOverlap() {
        val hours = listOf(10,11,13,14).map { HourlyEntry("2026-01-01T$it:00:00Z", score = 70) }
        assertEquals(listOf("10:00 – 12:00" to 70, "13:00 – 15:00" to 70), ForecastWindows.upcoming(hours, start))
    }

    @Test fun explanationsReconcileWithTheActualMeteoScore() {
        val weather = WeatherData("2026-01-01T10:00:00Z", 20.0, 70.0, 1008.0, windSpeed = 10.0, precipitation = 0.0, cloudCover = 60.0, isDay = true)
        val result = ActivityScoring.calculate(ActivityCalculateArgs(weather,
            AstroData("2026-01-01T07:00:00Z", "2026-01-01T18:00:00Z", moonPhase = 0.25), species = FishSpecies.carp,
            context = ScoringContextMs(start, lat = 40.0, waterTemp = 20.0)))
        val model = result.breakdown.meteoModel!!
        assertTrue(model.influences.isNotEmpty())
        assertEquals(result.breakdown.subscores.meteo.toDouble(), 50 + model.influences.sumOf { it.contribution }, 0.51)
    }
}
