package com.sentinel.bridge.feature.pipeline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@DisplayName("IsoDateTimes")
class IsoDateTimesTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    @Test
    @DisplayName("parses a full date-time")
    fun parsesDateTime() {
        val expected = LocalDateTime.of(2026, 3, 14, 15, 30, 0)
            .atZone(zone).toInstant().toEpochMilli()

        assertEquals(expected, IsoDateTimes.toEpochMillis("2026-03-14T15:30:00", zone))
    }

    @Test
    @DisplayName("a bare date resolves to the start of that day")
    fun parsesDateAsStartOfDay() {
        val expected = LocalDate.of(2026, 3, 14).atStartOfDay(zone).toInstant().toEpochMilli()

        assertEquals(expected, IsoDateTimes.toEpochMillis("2026-03-14", zone))
    }

    @Test
    @DisplayName("tolerates a trailing Z and surrounding whitespace")
    fun toleratesZuluAndWhitespace() {
        val expected = LocalDateTime.of(2026, 3, 14, 15, 30, 0)
            .atZone(zone).toInstant().toEpochMilli()

        assertEquals(expected, IsoDateTimes.toEpochMillis("  2026-03-14T15:30:00Z  ", zone))
    }

    @Test
    @DisplayName("returns null rather than a wrong time for unusable input")
    fun returnsNullForUnusableInput() {
        // A wrong timestamp on a calendar event is worse than no timestamp, so anything
        // that cannot be read confidently must come back as null.
        assertNull(IsoDateTimes.toEpochMillis(null, zone))
        assertNull(IsoDateTimes.toEpochMillis("", zone))
        assertNull(IsoDateTimes.toEpochMillis("   ", zone))
        assertNull(IsoDateTimes.toEpochMillis("next Tuesday", zone))
        assertNull(IsoDateTimes.toEpochMillis("14/03/2026", zone))
        assertNull(IsoDateTimes.toEpochMillis("2026-13-45", zone))
    }
}
