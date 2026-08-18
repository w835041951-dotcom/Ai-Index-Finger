package com.aiindexfinger.scheduler

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleCompletionTest {
    private val utc = ZoneId.of("UTC")
    private val newYork = ZoneId.of("America/New_York")

    @Test
    fun `once completion removes the schedule`() {
        val completion = completeScheduleOccurrence(
            listOf(WorkflowSchedule("id", "Name", 100)),
            "id",
            100,
            100,
            utc,
        )

        assertTrue(completion.accepted)
        assertEquals(emptyList<WorkflowSchedule>(), completion.schedules)
        assertNull(completion.nextSchedule)
    }

    @Test
    fun `daily completion advances beyond delayed delivery`() {
        val first = Instant.parse("2025-01-01T09:00:00Z").toEpochMilli()
        val completed = Instant.parse("2025-01-03T10:00:00Z").toEpochMilli()
        val completion = completeScheduleOccurrence(
            listOf(WorkflowSchedule("id", "Name", first, recurrence = ScheduleRecurrence.Daily)),
            "id",
            first,
            completed,
            utc,
        )

        assertEquals(
            Instant.parse("2025-01-04T09:00:00Z").toEpochMilli(),
            completion.nextSchedule?.scheduledAtMillis,
        )
        assertEquals(9 * 60, completion.nextSchedule?.recurrenceLocalTimeMinutes)
    }

    @Test
    fun `daily completion returns to its local anchor after a daylight saving gap`() {
        val first = Instant.parse("2025-03-08T07:30:00Z").toEpochMilli()
        val schedule = WorkflowSchedule(
            "id",
            "Name",
            first,
            recurrence = ScheduleRecurrence.Daily,
            recurrenceLocalTimeMinutes = 2 * 60 + 30,
        )

        val gapDay = requireNotNull(
            completeScheduleOccurrence(listOf(schedule), "id", first, first, newYork).nextSchedule,
        )
        val followingDay = completeScheduleOccurrence(
            listOf(gapDay),
            "id",
            gapDay.scheduledAtMillis,
            gapDay.scheduledAtMillis,
            newYork,
        ).nextSchedule

        assertEquals(Instant.parse("2025-03-09T07:30:00Z").toEpochMilli(), gapDay.scheduledAtMillis)
        assertEquals(
            Instant.parse("2025-03-10T06:30:00Z").toEpochMilli(),
            followingDay?.scheduledAtMillis,
        )
    }

    @Test
    fun `cancelled schedule is not recreated by completion`() {
        val completion = completeScheduleOccurrence(emptyList(), "id", 100, 100, utc)

        assertFalse(completion.accepted)
        assertEquals(emptyList<WorkflowSchedule>(), completion.schedules)
    }

    @Test
    fun `discard removes only the exact stale pending occurrence`() {
        val stale = WorkflowSchedule("id", "Stale", 100, occurrenceId = "old")

        val discarded = discardScheduleOccurrence(listOf(stale), "id", 100, "old")
        val replacement = WorkflowSchedule("id", "Replacement", 100, occurrenceId = "new")
        val staleWorker = discardScheduleOccurrence(listOf(replacement), "id", 100, "old")
        val missed = stale.copy(status = ScheduleStatus.Missed)
        val missedWorker = discardScheduleOccurrence(listOf(missed), "id", 100, "old")

        assertTrue(discarded.accepted)
        assertEquals(emptyList<WorkflowSchedule>(), discarded.schedules)
        assertFalse(staleWorker.accepted)
        assertEquals(listOf(replacement), staleWorker.schedules)
        assertFalse(missedWorker.accepted)
        assertEquals(listOf(missed), missedWorker.schedules)
    }

    @Test
    fun `completion rejects same-time replacement with a different occurrence ID`() {
        val replacement = WorkflowSchedule("id", "Replacement", 100, occurrenceId = "new")

        val completion = completeScheduleOccurrence(
            listOf(replacement),
            "id",
            100,
            100,
            utc,
            expectedOccurrenceId = "old",
            nextOccurrenceId = "next",
        )

        assertFalse(completion.accepted)
        assertEquals(listOf(replacement), completion.schedules)
    }

    @Test
    fun `recurring completion advances occurrence identity`() {
        val current = WorkflowSchedule(
            "id",
            "Current",
            100,
            recurrence = ScheduleRecurrence.Daily,
            occurrenceId = "old",
        )

        val completion = completeScheduleOccurrence(
            listOf(current),
            "id",
            100,
            100,
            utc,
            expectedOccurrenceId = "old",
            nextOccurrenceId = "next",
        )

        assertTrue(completion.accepted)
        assertEquals("next", completion.nextSchedule?.occurrenceId)
        assertEquals("old", completion.nextSchedule?.previousOccurrenceId)
        assertEquals(100L, completion.nextSchedule?.previousScheduledAtMillis)
    }

    @Test
    fun `post completion discard removes the newly advanced stale occurrence`() {
        val current = WorkflowSchedule(
            "id",
            "Current",
            100,
            recurrence = ScheduleRecurrence.Daily,
            occurrenceId = "old",
        )
        val completion = completeScheduleOccurrence(
            listOf(current),
            "id",
            100,
            100,
            utc,
            expectedOccurrenceId = "old",
            nextOccurrenceId = "next",
        )
        val next = requireNotNull(completion.nextSchedule)

        val discard = discardScheduleOccurrence(
            completion.schedules,
            next.workflowId,
            next.scheduledAtMillis,
            next.occurrenceId,
        )

        assertTrue(discard.accepted)
        assertTrue(discard.schedules.isEmpty())
    }

    @Test
    fun `replacement rejects completion from stale work`() {
        val replacement = WorkflowSchedule("id", "Name", 200, recurrence = ScheduleRecurrence.Weekly)
        val completion = completeScheduleOccurrence(listOf(replacement), "id", 100, 100, utc)

        assertFalse(completion.accepted)
        assertEquals(listOf(replacement), completion.schedules)
        assertNull(completion.nextSchedule)
    }

    @Test
    fun `missed once occurrence remains available for existing feedback`() {
        val schedule = WorkflowSchedule("id", "Name", 100)

        val completion = missScheduleOccurrence(listOf(schedule), "id", 100, 200, utc)

        assertTrue(completion.accepted)
        assertEquals(ScheduleStatus.Missed, completion.schedules.single().status)
        assertNull(completion.nextSchedule)
    }

    @Test
    fun `missed daily occurrence advances beyond all elapsed occurrences`() {
        val first = Instant.parse("2025-01-01T09:00:00Z").toEpochMilli()
        val missedAt = Instant.parse("2025-01-03T10:00:00Z").toEpochMilli()
        val schedule = WorkflowSchedule("id", "Name", first, recurrence = ScheduleRecurrence.Daily)

        val completion = missScheduleOccurrence(listOf(schedule), "id", first, missedAt, utc)

        assertEquals(
            Instant.parse("2025-01-04T09:00:00Z").toEpochMilli(),
            completion.nextSchedule?.scheduledAtMillis,
        )
        assertEquals(ScheduleStatus.Pending, completion.schedules.single().status)
        assertTrue(completion.schedules.single().missedOccurrencePending)
    }

    @Test
    fun `missed weekly occurrence advances to next future week`() {
        val first = Instant.parse("2025-01-01T09:00:00Z").toEpochMilli()
        val missedAt = Instant.parse("2025-01-15T10:00:00Z").toEpochMilli()
        val schedule = WorkflowSchedule("id", "Name", first, recurrence = ScheduleRecurrence.Weekly)

        val completion = missScheduleOccurrence(listOf(schedule), "id", first, missedAt, utc)

        assertEquals(
            Instant.parse("2025-01-22T09:00:00Z").toEpochMilli(),
            completion.nextSchedule?.scheduledAtMillis,
        )
        assertTrue(completion.schedules.single().missedOccurrencePending)
    }

    @Test
    fun `stale missed occurrence cannot replace a newer schedule`() {
        val replacement = WorkflowSchedule("id", "Name", 200, recurrence = ScheduleRecurrence.Daily)

        val completion = missScheduleOccurrence(listOf(replacement), "id", 100, 300, utc)

        assertFalse(completion.accepted)
        assertEquals(listOf(replacement), completion.schedules)
        assertNull(completion.nextSchedule)
    }

    @Test
    fun `consuming missed feedback preserves recurring schedule and clears signal`() {
        val recurring = WorkflowSchedule(
            "id",
            "Name",
            200,
            recurrence = ScheduleRecurrence.Daily,
            missedOccurrencePending = true,
        )

        val consumed = consumeMissedSchedule(listOf(recurring), "id")

        assertEquals(listOf(recurring.copy(missedOccurrencePending = false)), consumed)
    }

    @Test
    fun `consuming missed feedback removes terminal one time schedule`() {
        val missed = WorkflowSchedule("id", "Name", 100, status = ScheduleStatus.Missed)

        assertEquals(emptyList<WorkflowSchedule>(), consumeMissedSchedule(listOf(missed), "id"))
    }

}