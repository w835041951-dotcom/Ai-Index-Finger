package com.aiindexfinger.scheduler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduledWorkflowEventTest {
    @Test
    fun `publishes the same workflow as distinct consumable events`() {
        val controller = ScheduledWorkflowEventController()

        controller.publish("workflow")
        val first = requireNotNull(controller.event.value)
        controller.consume(first.sequence)
        assertNull(controller.event.value)

        controller.publish("workflow")
        val second = requireNotNull(controller.event.value)

        assertEquals("workflow", second.workflowId)
        assertNotEquals(first.sequence, second.sequence)
    }

    @Test
    fun `stale consumer cannot clear a newer event`() {
        val controller = ScheduledWorkflowEventController()
        controller.publish("first")
        val first = requireNotNull(controller.event.value)
        controller.publish("second")

        controller.consume(first.sequence)

        assertEquals("second", controller.event.value?.workflowId)
    }

    @Test
    fun `queues different reminders published before consumption`() {
        val controller = ScheduledWorkflowEventController()
        controller.publish("first")
        controller.publish("second")

        val first = requireNotNull(controller.event.value)
        assertEquals("first", first.workflowId)
        controller.consume(first.sequence)

        assertEquals("second", controller.event.value?.workflowId)
    }

    @Test
    fun `trigger removes only the matching in memory schedule`() {
        val schedules = listOf(
            WorkflowSchedule("first", "First", 100),
            WorkflowSchedule("second", "Second", 200),
        )

        assertEquals(
            listOf(WorkflowSchedule("second", "Second", 200)),
            removeTriggeredSchedule(schedules, "first"),
        )
    }
}