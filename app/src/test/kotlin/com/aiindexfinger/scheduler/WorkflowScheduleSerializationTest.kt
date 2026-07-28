package com.aiindexfinger.scheduler

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkflowScheduleSerializationTest {
    @Test
    fun scheduleRoundTrips() {
        val schedule = WorkflowSchedule(
            workflowId = "workflow-1",
            workflowName = "Morning task",
            scheduledAtMillis = 123_456_789,
        )

        val encoded = Json.encodeToString(WorkflowSchedule.serializer(), schedule)
        val decoded = Json.decodeFromString(WorkflowSchedule.serializer(), encoded)

        assertEquals(schedule, decoded)
    }
}