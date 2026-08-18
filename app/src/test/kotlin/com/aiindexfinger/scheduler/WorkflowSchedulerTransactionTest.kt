package com.aiindexfinger.scheduler

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowSchedulerTransactionTest {
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