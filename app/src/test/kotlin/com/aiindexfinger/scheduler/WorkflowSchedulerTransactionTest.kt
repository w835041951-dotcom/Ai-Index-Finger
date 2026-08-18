package com.aiindexfinger.scheduler

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowSchedulerTransactionTest {
    @Test
    fun `repeated exact occurrence enqueue is idempotent`() = runBlocking {
        val requestId = UUID.randomUUID()
        val persistedWork = mutableSetOf<UUID>()
        var enqueueCount = 0

        repeat(2) {
            enqueueScheduledWorkIfMissing(
                requestId = requestId,
                workExists = persistedWork::contains,
                enqueue = {
                    enqueueCount++
                    persistedWork += requestId
                },
            )
        }

        assertEquals(1, enqueueCount)
        assertEquals(setOf(requestId), persistedWork)
    }

    @Test
    fun `enqueue failure removes first schedule`() = runBlocking {
        val schedule = WorkflowSchedule("workflow", "Workflow", 2_000L)
        val schedules = mutableListOf<WorkflowSchedule>()

        val failure = runCatching {
            persistScheduledWork(
                schedule = schedule,
                loadSchedules = { schedules.toList() },
                persistSchedule = { schedules.replace(it) },
                removeSchedule = { schedules.remove(it) },
                enqueue = { error("enqueue failed") },
            )
        }.exceptionOrNull()

        assertEquals("enqueue failed", failure?.message)
        assertTrue(schedules.isEmpty())
    }

    @Test
    fun `enqueue failure restores replaced schedule`() = runBlocking {
        val previous = WorkflowSchedule("workflow", "Old name", 2_000L)
        val replacement = WorkflowSchedule("workflow", "New name", 3_000L)
        val schedules = mutableListOf(previous)

        val failure = runCatching {
            persistScheduledWork(
                schedule = replacement,
                loadSchedules = { schedules.toList() },
                persistSchedule = { schedules.replace(it) },
                removeSchedule = { schedules.remove(it) },
                enqueue = { error("enqueue failed") },
            )
        }.exceptionOrNull()

        assertEquals("enqueue failed", failure?.message)
        assertEquals(listOf(previous), schedules)
    }

    @Test
    fun `storage failure still requests work cancellation`() = runBlocking {
        var cancelRequested = false

        val failure = runCatching {
            cancelScheduledWork(
                workflowId = "workflow",
                loadSchedules = { throw ScheduleStorageException(IllegalStateException("corrupt")) },
                persistSchedule = { error("must not restore") },
                removeSchedule = { throw ScheduleStorageException(IllegalStateException("corrupt")) },
                cancelWork = { cancelRequested = true },
            )
        }.exceptionOrNull()

        assertTrue(cancelRequested)
        assertTrue(failure is ScheduleStorageException)
    }

    @Test
    fun `successful cancellation returns updated schedules`() = runBlocking {
        val remaining = listOf(WorkflowSchedule("other", "Other", 2_000L))
        var cancelRequested = false

        val result = cancelScheduledWork(
            workflowId = "workflow",
            loadSchedules = { listOf(WorkflowSchedule("workflow", "Workflow", 1_000L)) + remaining },
            persistSchedule = { error("must not restore") },
            removeSchedule = { remaining },
            cancelWork = { cancelRequested = true },
        )

        assertTrue(cancelRequested)
        assertEquals(remaining, result)
    }

    @Test
    fun `schedule success waits for asynchronous enqueue completion`() = runBlocking {
        val schedule = WorkflowSchedule("workflow", "Workflow", 2_000L)
        val schedules = mutableListOf<WorkflowSchedule>()
        val enqueueStarted = CompletableDeferred<Unit>()
        val allowEnqueue = CompletableDeferred<Unit>()
        val result = async {
            persistScheduledWork(
                schedule = schedule,
                loadSchedules = { schedules.toList() },
                persistSchedule = { schedules.replace(it) },
                removeSchedule = { schedules.remove(it) },
                enqueue = {
                    enqueueStarted.complete(Unit)
                    allowEnqueue.await()
                },
            )
        }

        enqueueStarted.await()
        assertFalse(result.isCompleted)
        allowEnqueue.complete(Unit)

        assertEquals(listOf(schedule), result.await())
    }

    @Test
    fun `asynchronous cancellation failure restores removed schedule`() = runBlocking {
        val original = WorkflowSchedule("workflow", "Workflow", 2_000L)
        val schedules = mutableListOf(original)

        val failure = runCatching {
            cancelScheduledWork(
                workflowId = original.workflowId,
                loadSchedules = { schedules.toList() },
                persistSchedule = { schedules.replace(it) },
                removeSchedule = { schedules.remove(it) },
                cancelWork = { throw IllegalStateException("cancel failed") },
            )
        }.exceptionOrNull()

        assertEquals("cancel failed", failure?.message)
        assertEquals(listOf(original), schedules)
    }

    @Test
    fun `cancelled enqueue preserves persisted schedule for reconciliation`() = runBlocking {
        val schedule = WorkflowSchedule("workflow", "Workflow", 2_000L)
        val schedules = mutableListOf<WorkflowSchedule>()

        val failure = runCatching {
            persistScheduledWork(
                schedule = schedule,
                loadSchedules = { schedules.toList() },
                persistSchedule = { schedules.replace(it) },
                removeSchedule = { schedules.remove(it) },
                enqueue = { throw CancellationException("outcome unknown") },
            )
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertEquals(listOf(schedule), schedules)
    }

    @Test
    fun `cancelled cancel keeps removal for stale worker rejection`() = runBlocking {
        val original = WorkflowSchedule("workflow", "Workflow", 2_000L)
        val schedules = mutableListOf(original)

        val failure = runCatching {
            cancelScheduledWork(
                workflowId = original.workflowId,
                loadSchedules = { schedules.toList() },
                persistSchedule = { schedules.replace(it) },
                removeSchedule = { schedules.remove(it) },
                cancelWork = { throw CancellationException("outcome unknown") },
            )
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertTrue(schedules.isEmpty())
    }

    @Test
    fun `notification outcome is persisted without completing the occurrence first`() = runBlocking {
        val original = WorkflowSchedule(
            "workflow",
            "Workflow",
            2_000L,
            occurrenceId = "occurrence",
        )
        val events = mutableListOf<String>()

        val delivery = deliverScheduledOccurrence(
            workflowId = original.workflowId,
            expectedAtMillis = original.scheduledAtMillis,
            expectedOccurrenceId = original.occurrenceId,
            loadSchedules = { listOf(original) },
            deliverNotification = {
                events += "notify"
                false
            },
            completeOccurrence = {
                events += "complete"
                error("failed notifications must not complete")
            },
            missOccurrence = {
                events += "miss"
                ScheduleCompletion(true, listOf(original.copy(status = ScheduleStatus.Missed)), null)
            },
            enqueue = { error("one-time misses do not enqueue") },
            restoreSchedule = { error("successful persistence does not restore") },
        )

        assertTrue(delivery.accepted)
        assertFalse(delivery.notificationDelivered)
        assertEquals(listOf("notify", "miss"), events)
    }

    @Test
    fun `successful notification completes the exact occurrence`() = runBlocking {
        val original = WorkflowSchedule(
            "workflow",
            "Workflow",
            2_000L,
            occurrenceId = "occurrence",
        )
        val events = mutableListOf<String>()

        val delivery = deliverScheduledOccurrence(
            workflowId = original.workflowId,
            expectedAtMillis = original.scheduledAtMillis,
            expectedOccurrenceId = original.occurrenceId,
            loadSchedules = { listOf(original) },
            deliverNotification = {
                events += "notify"
                true
            },
            completeOccurrence = {
                events += "complete"
                ScheduleCompletion(true, emptyList(), null)
            },
            missOccurrence = {
                events += "miss"
                error("successful notifications must not be missed")
            },
            enqueue = { error("one-time completion does not enqueue") },
            restoreSchedule = { error("successful persistence does not restore") },
        )

        assertTrue(delivery.accepted)
        assertTrue(delivery.notificationDelivered)
        assertEquals(listOf("notify", "complete"), events)
    }

    @Test
    fun `stale occurrence cannot display a notification`() = runBlocking {
        val replacement = WorkflowSchedule(
            "workflow",
            "Replacement",
            2_000L,
            occurrenceId = "new",
        )
        var notificationRequested = false

        val delivery = deliverScheduledOccurrence(
            workflowId = replacement.workflowId,
            expectedAtMillis = replacement.scheduledAtMillis,
            expectedOccurrenceId = "old",
            loadSchedules = { listOf(replacement) },
            deliverNotification = {
                notificationRequested = true
                true
            },
            completeOccurrence = { error("stale occurrence must not complete") },
            missOccurrence = { error("stale occurrence must not be missed") },
            enqueue = { error("stale occurrence must not enqueue") },
            restoreSchedule = { error("stale occurrence must not restore") },
        )

        assertFalse(delivery.accepted)
        assertFalse(notificationRequested)
    }

    @Test
    fun `continuation enqueue failure restores the undelivered pending occurrence`() = runBlocking {
        val original = WorkflowSchedule(
            "workflow",
            "Workflow",
            2_000L,
            recurrence = ScheduleRecurrence.Daily,
            occurrenceId = "old",
        )
        val next = original.copy(
            scheduledAtMillis = 3_000L,
            missedOccurrencePending = true,
            occurrenceId = "next",
        )
        var schedules = listOf(original)

        val failure = runCatching {
            deliverScheduledOccurrence(
                workflowId = original.workflowId,
                expectedAtMillis = original.scheduledAtMillis,
                expectedOccurrenceId = original.occurrenceId,
                loadSchedules = { schedules },
                deliverNotification = { false },
                completeOccurrence = { error("failed notifications must not complete") },
                missOccurrence = {
                    schedules = listOf(next)
                    ScheduleCompletion(true, schedules, next)
                },
                enqueue = { error("enqueue failed") },
                restoreSchedule = { restored ->
                    schedules = listOf(restored)
                    schedules
                },
            )
        }.exceptionOrNull()

        assertEquals("enqueue failed", failure?.message)
        assertEquals(listOf(original), schedules)
    }

    @Test
    fun `retry enqueues the exact continuation after enqueue and rollback both fail`() = runBlocking {
        val original = WorkflowSchedule(
            "workflow",
            "Workflow",
            2_000L,
            recurrence = ScheduleRecurrence.Daily,
            occurrenceId = "old",
        )
        val next = original.copy(
            scheduledAtMillis = 3_000L,
            occurrenceId = "next",
            previousOccurrenceId = original.occurrenceId,
            previousScheduledAtMillis = original.scheduledAtMillis,
        )
        var schedules = listOf(original)
        val firstFailure = runCatching {
            deliverScheduledOccurrence(
                workflowId = original.workflowId,
                expectedAtMillis = original.scheduledAtMillis,
                expectedOccurrenceId = original.occurrenceId,
                loadSchedules = { schedules },
                deliverNotification = { true },
                completeOccurrence = {
                    schedules = listOf(next)
                    ScheduleCompletion(true, schedules, next)
                },
                missOccurrence = { error("delivered notification must not be missed") },
                enqueue = { error("enqueue failed") },
                restoreSchedule = { error("rollback failed") },
            )
        }.exceptionOrNull()
        assertEquals("enqueue failed", firstFailure?.message)
        assertEquals("rollback failed", firstFailure?.suppressed?.single()?.message)
        assertEquals(listOf(next), schedules)

        var notificationRequested = false
        var recoveredContinuation: WorkflowSchedule? = null
        val retry = deliverScheduledOccurrence(
            workflowId = original.workflowId,
            expectedAtMillis = original.scheduledAtMillis,
            expectedOccurrenceId = original.occurrenceId,
            loadSchedules = { schedules },
            deliverNotification = {
                notificationRequested = true
                true
            },
            completeOccurrence = { error("retry must not complete the next occurrence") },
            missOccurrence = { error("retry must not miss the next occurrence") },
            enqueue = { recoveredContinuation = it },
            restoreSchedule = { error("recovery must not roll back") },
        )

        assertFalse(retry.accepted)
        assertFalse(notificationRequested)
        assertEquals(next, recoveredContinuation)
    }

    @Test
    fun `legacy null occurrence identity can recover its first continuation`() = runBlocking {
        val legacy = WorkflowSchedule(
            "legacy",
            "Legacy",
            2_000L,
            recurrence = ScheduleRecurrence.Daily,
        )
        val next = legacy.copy(
            scheduledAtMillis = 3_000L,
            occurrenceId = "generated-next",
            previousOccurrenceId = null,
            previousScheduledAtMillis = legacy.scheduledAtMillis,
        )
        var recoveredContinuation: WorkflowSchedule? = null

        val retry = deliverScheduledOccurrence(
            workflowId = legacy.workflowId,
            expectedAtMillis = legacy.scheduledAtMillis,
            expectedOccurrenceId = null,
            loadSchedules = { listOf(next) },
            deliverNotification = { error("recovery must not notify again") },
            completeOccurrence = { error("recovery must not advance again") },
            missOccurrence = { error("recovery must not mark the next occurrence missed") },
            enqueue = { recoveredContinuation = it },
            restoreSchedule = { error("recovery must not restore") },
        )

        assertFalse(retry.accepted)
        assertEquals(next, recoveredContinuation)
    }

    private fun MutableList<WorkflowSchedule>.replace(schedule: WorkflowSchedule): List<WorkflowSchedule> {
        removeAll { it.workflowId == schedule.workflowId }
        add(schedule)
        return toList()
    }

    private fun MutableList<WorkflowSchedule>.remove(workflowId: String): List<WorkflowSchedule> {
        removeAll { it.workflowId == workflowId }
        return toList()
    }
}