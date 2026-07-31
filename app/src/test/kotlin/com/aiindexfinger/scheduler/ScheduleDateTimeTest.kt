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

    @Test
    fun `daily recurrence keeps local time`() {
        val next = nextOccurrenceEpochMillis(
            Instant.parse("2025-01-15T14:30:00Z").toEpochMilli(),
            ScheduleRecurrence.Daily,
            newYork,
        )

        assertEquals(Instant.parse("2025-01-16T14:30:00Z").toEpochMilli(), next)
    }

    @Test
    fun `weekly recurrence keeps local weekday and time`() {
        val next = nextOccurrenceEpochMillis(
            Instant.parse("2025-01-15T14:30:00Z").toEpochMilli(),
            ScheduleRecurrence.Weekly,
            newYork,
        )

        assertEquals(Instant.parse("2025-01-22T14:30:00Z").toEpochMilli(), next)
    }

    @Test
    fun `daily recurrence moves a gap time forward by the transition`() {
        val next = nextOccurrenceEpochMillis(
            Instant.parse("2025-03-08T07:30:00Z").toEpochMilli(),
            ScheduleRecurrence.Daily,
            newYork,
        )

        assertEquals(Instant.parse("2025-03-09T07:30:00Z").toEpochMilli(), next)
    }

    @Test
    fun `daily recurrence uses earlier offset during overlap`() {
        val next = nextOccurrenceEpochMillis(
            Instant.parse("2025-11-01T05:30:00Z").toEpochMilli(),
            ScheduleRecurrence.Daily,
            newYork,
        )

        assertEquals(Instant.parse("2025-11-02T05:30:00Z").toEpochMilli(), next)
    }

    @Test
    fun `next occurrence follows the supplied current timezone rules`() {
        val previous = Instant.parse("2025-11-01T06:30:00Z").toEpochMilli()

        val newYorkNext = nextOccurrenceEpochMillis(
            previous,
            ScheduleRecurrence.Daily,
            newYork,
        )
        val utcNext = nextOccurrenceEpochMillis(previous, ScheduleRecurrence.Daily, ZoneId.of("UTC"))

        assertEquals(Instant.parse("2025-11-02T07:30:00Z").toEpochMilli(), newYorkNext)
        assertEquals(Instant.parse("2025-11-02T06:30:00Z").toEpochMilli(), utcNext)
    }

    @Test
    fun `once has no next occurrence`() {
        assertEquals(
            null,
            nextOccurrenceEpochMillis(123, ScheduleRecurrence.Once, newYork),
        )
    }
}