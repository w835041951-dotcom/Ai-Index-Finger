package com.aiindexfinger.scheduler

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ScheduleDateTimeTest {
    private val newYork = ZoneId.of("America/New_York")

    @Test
    fun `converts an ordinary local time to its exact instant`() {
        val epochMillis = localScheduleEpochMillis(
            LocalDate.of(2025, 1, 15),
            LocalTime.of(9, 30),
            newYork,
        )

        assertEquals(Instant.parse("2025-01-15T14:30:00Z").toEpochMilli(), epochMillis)
    }

    @Test
    fun `rejects a local time skipped by daylight saving`() {
        assertThrows(IllegalArgumentException::class.java) {
            localScheduleEpochMillis(
                LocalDate.of(2025, 3, 9),
                LocalTime.of(2, 30),
                newYork,
            )
        }
    }

    @Test
    fun `uses the earlier offset for an overlapping local time`() {
        val epochMillis = localScheduleEpochMillis(
            LocalDate.of(2025, 11, 2),
            LocalTime.of(1, 30),
            newYork,
        )

        assertEquals(Instant.parse("2025-11-02T05:30:00Z").toEpochMilli(), epochMillis)
    }
}