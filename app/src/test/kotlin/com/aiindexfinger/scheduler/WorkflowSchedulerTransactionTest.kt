package com.aiindexfinger.scheduler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowSchedulerTransactionTest {
    @Test
    fun `enqueue failure removes first schedule`() {
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
    fun `enqueue failure restores replaced schedule`() {
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
    fun `storage failure still requests work cancellation`() {
        var cancelRequested = false

        val failure = runCatching {
            cancelScheduledWork(
                workflowId = "workflow",
                removeSchedule = { throw ScheduleStorageException(IllegalStateException("corrupt")) },
                cancelWork = { cancelRequested = true },
            )
        }.exceptionOrNull()

        assertTrue(cancelRequested)
        assertTrue(failure is ScheduleStorageException)
    }

    @Test
    fun `successful cancellation returns updated schedules`() {
        val remaining = listOf(WorkflowSchedule("other", "Other", 2_000L))
        var cancelRequested = false

        val result = cancelScheduledWork(
            workflowId = "workflow",
            removeSchedule = { remaining },
            cancelWork = { cancelRequested = true },
        )

        assertTrue(cancelRequested)
        assertEquals(remaining, result)
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