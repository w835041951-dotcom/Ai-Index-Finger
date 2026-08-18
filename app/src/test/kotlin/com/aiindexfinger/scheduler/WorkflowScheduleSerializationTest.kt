package com.aiindexfinger.scheduler

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkflowScheduleSerializationTest {
    @Test
    fun scheduleRoundTrips() {
        val schedule = WorkflowSchedule(
            workflowId = "workflow-1",
            workflowName = "Morning task",
            scheduledAtMillis = 123_456_789,
            recurrence = ScheduleRecurrence.Daily,
            recurrenceLocalTimeMinutes = 9 * 60 + 30,
            occurrenceId = "occurrence-1",
        )

        val encoded = Json.encodeToString(WorkflowSchedule.serializer(), schedule)
        val decoded = Json.decodeFromString(WorkflowSchedule.serializer(), encoded)

        assertEquals(schedule, decoded)
    }

    @Test
    fun legacyScheduleDefaultsToPendingAndOnce() {
        val decoded = Json.decodeFromString(
            WorkflowSchedule.serializer(),
            """{"workflowId":"legacy","workflowName":"Legacy","scheduledAtMillis":123}""",
        )

        assertEquals(ScheduleStatus.Pending, decoded.status)
        assertEquals(ScheduleRecurrence.Once, decoded.recurrence)
        assertEquals(false, decoded.missedOccurrencePending)
        assertEquals(null, decoded.recurrenceLocalTimeMinutes)
        assertEquals(null, decoded.occurrenceId)
    }

    @Test
    fun invalidRecurrenceAnchorIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            Json.decodeFromString(
                WorkflowSchedule.serializer(),
                """{"workflowId":"invalid","workflowName":"Invalid","scheduledAtMillis":123,"recurrence":"Daily","recurrenceLocalTimeMinutes":1440}""",
            )
        }
    }
}