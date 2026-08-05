package com.sentinel.bridge.feature.pipeline

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * Converts the ISO-8601 strings the model produces into epoch milliseconds.
 *
 * Google Calendar's insert intent takes `EXTRA_EVENT_BEGIN_TIME` and
 * `EXTRA_EVENT_END_TIME` as epoch millis, so the broadcast carries both the original
 * ISO text (readable, and what Google Tasks wants for a due date) and a millis form.
 *
 * The model is asked for absolute dates, but it is not guaranteed to comply — an
 * unparseable value yields `null` so the caller can omit the extra rather than
 * broadcast a wrong time.
 */
object IsoDateTimes {

    /** Millis in one hour, used as the default meeting length. */
    const val DEFAULT_EVENT_DURATION_MS = 60 * 60 * 1000L

    /**
     * Parses an ISO-8601 date or date-time into epoch millis in the device's zone.
     *
     * Accepts `2026-03-14T15:30:00` and `2026-03-14`; a bare date is taken as the
     * start of that day.
     *
     * @param value ISO-8601 text, possibly null or blank.
     * @param zone Zone used to resolve local values, defaulting to the device's.
     * @return Epoch millis, or `null` if [value] is absent or not parseable.
     */
    fun toEpochMillis(value: String?, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val text = value?.trim()?.removeSuffix("Z")
        if (text.isNullOrEmpty()) return null

        return try {
            LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            try {
                LocalDate.parse(text).atStartOfDay(zone).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}
