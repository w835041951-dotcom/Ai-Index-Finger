package com.aiindexfinger.scheduler

import android.app.NotificationManager
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import com.aiindexfinger.data.WorkflowLibrary
import com.aiindexfinger.data.WorkflowLoadResult
import com.aiindexfinger.model.Step
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.WorkflowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ScheduleNotificationIdentityTest {
    @Test
    fun `notification delivery converts ordinary failures but preserves fatal errors`() {
        assertTrue(scheduledNotificationDelivered {})
        assertFalse(scheduledNotificationDelivered { error("notification service unavailable") })
        assertThrows(AssertionError::class.java) {
            scheduledNotificationDelivered { throw AssertionError("fatal") }
        }
    }

    @Test
    fun `worker retries scheduling failures but not storage corruption`() {
        assertTrue(
            shouldRetryScheduledWorker(
                ScheduleWorkOperationException(IllegalStateException("work manager unavailable")),
            ),
        )
        assertTrue(
            shouldRetryScheduledWorker(
                ScheduleStorageWriteException(java.io.IOException("storage temporarily unavailable")),
            ),
        )
        assertFalse(
            shouldRetryScheduledWorker(
                ScheduleStorageException(IllegalStateException("schedule file corrupt")),
            ),
        )
        assertFalse(shouldRetryScheduledWorker(ScheduleStorageCapacityException()))
    }

    @Test
    fun `running worker appends continuation while external scheduling replaces`() {
        assertEquals(
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            existingWorkPolicy(ScheduleWorkOrigin.RunningWorker),
        )
        assertEquals(
            ExistingWorkPolicy.REPLACE,
            existingWorkPolicy(ScheduleWorkOrigin.External),
        )
    }

    @Test
    fun `unique work name stays compatible with previously queued schedules`() {
        assertEquals("workflow-schedule-workflow/id", scheduleWorkName("workflow/id"))
    }

    @Test
    fun `exact schedule occurrence has a stable collision resistant work ID`() {
        val schedule = WorkflowSchedule(
            "workflow",
            "Workflow",
            2_000L,
            occurrenceId = "occurrence",
        )

        assertEquals(scheduleWorkRequestId(schedule), scheduleWorkRequestId(schedule.copy()))
        assertNotEquals(
            scheduleWorkRequestId(schedule),
            scheduleWorkRequestId(schedule.copy(scheduledAtMillis = 2_001L)),
        )
        assertNotEquals(
            scheduleWorkRequestId(schedule),
            scheduleWorkRequestId(schedule.copy(occurrenceId = "replacement")),
        )
        assertNotEquals(
            scheduleWorkRequestId(schedule),
            scheduleWorkRequestId(schedule.copy(workflowId = "other")),
        )
    }

    @Test
    fun `workflow ID input is bounded by UTF-8 bytes`() {
        assertEquals("short", workflowIdForWorkInput("short"))
        assertEquals(null, workflowIdForWorkInput("a".repeat(4 * 1024 + 1)))
        assertEquals(null, workflowIdForWorkInput("界".repeat(1_400)))
    }

    @Test
    fun `maximum retained identifiers fit WorkManager Data`() {
        val workflowId = "w".repeat(4 * 1024)
        val occurrenceId = "o".repeat(128)

        val input = scheduleWorkInput(
            WorkflowSchedule(
                workflowId,
                "Workflow",
                2_000L,
                occurrenceId = occurrenceId,
            ),
        )

        assertEquals(workflowId, input.getString(ScheduleNotificationWorker.KEY_WORKFLOW_ID))
        assertEquals(occurrenceId, input.getString(ScheduleNotificationWorker.KEY_OCCURRENCE_ID))
        assertEquals(2_000L, input.getLong(ScheduleNotificationWorker.KEY_SCHEDULED_AT_MILLIS, -1))
    }

    @Test
    fun `work request identity resolves current and predecessor occurrences`() {
        val previous = WorkflowSchedule(
            "workflow",
            "Workflow",
            2_000L,
            occurrenceId = "previous",
        )
        val current = previous.copy(
            scheduledAtMillis = 3_000L,
            occurrenceId = "current",
            previousOccurrenceId = previous.occurrenceId,
            previousScheduledAtMillis = previous.scheduledAtMillis,
        )

        assertEquals(
            ScheduleWorkTarget("workflow", 3_000L, "current"),
            resolveScheduleWorkTarget(listOf(current), scheduleWorkRequestId(current)),
        )
        assertEquals(
            ScheduleWorkTarget("workflow", 2_000L, "previous"),
            resolveScheduleWorkTarget(listOf(current), scheduleWorkRequestId(previous)),
        )
        assertEquals(
            null,
            resolveScheduleWorkTarget(
                listOf(current),
                scheduleWorkRequestId(current.copy(occurrenceId = "replacement")),
            ),
        )
    }

    @Test
    fun `only active or successful exact work suppresses enqueue`() {
        val append = ExistingWorkPolicy.APPEND_OR_REPLACE
        val replace = ExistingWorkPolicy.REPLACE

        assertTrue(scheduledWorkNeedsNoEnqueue(WorkInfo.State.ENQUEUED, append))
        assertTrue(scheduledWorkNeedsNoEnqueue(WorkInfo.State.RUNNING, append))
        assertTrue(scheduledWorkNeedsNoEnqueue(WorkInfo.State.BLOCKED, append))
        assertTrue(scheduledWorkNeedsNoEnqueue(WorkInfo.State.SUCCEEDED, append))
        assertFalse(scheduledWorkNeedsNoEnqueue(WorkInfo.State.SUCCEEDED, replace))
        assertFalse(scheduledWorkNeedsNoEnqueue(WorkInfo.State.FAILED, append))
        assertFalse(scheduledWorkNeedsNoEnqueue(WorkInfo.State.CANCELLED, append))
        assertFalse(scheduledWorkNeedsNoEnqueue(null, append))
    }

    @Test
    fun `notification requires permission app toggle and enabled channel`() {
        assertTrue(canPostScheduleNotification(true, true, NotificationManager.IMPORTANCE_HIGH))
        assertFalse(canPostScheduleNotification(false, true, NotificationManager.IMPORTANCE_HIGH))
        assertFalse(canPostScheduleNotification(true, false, NotificationManager.IMPORTANCE_HIGH))
        assertFalse(canPostScheduleNotification(true, true, NotificationManager.IMPORTANCE_NONE))
    }

    @Test
    fun `notification readiness identifies the blocking setting`() {
        assertEquals(
            ScheduleNotificationReadiness.RuntimePermissionRequired,
            scheduleNotificationReadiness(false, true, NotificationManager.IMPORTANCE_HIGH),
        )
        assertEquals(
            ScheduleNotificationReadiness.AppNotificationsDisabled,
            scheduleNotificationReadiness(true, false, NotificationManager.IMPORTANCE_HIGH),
        )
        assertEquals(
            ScheduleNotificationReadiness.ChannelDisabled,
            scheduleNotificationReadiness(true, true, NotificationManager.IMPORTANCE_NONE),
        )
        assertEquals(
            ScheduleNotificationReadiness.Ready,
            scheduleNotificationReadiness(true, true, NotificationManager.IMPORTANCE_HIGH),
        )
    }

    @Test
    fun `scheduling only persists when notification delivery is ready`() {
        assertEquals(
            ScheduleNotificationAction.Schedule,
            scheduleNotificationAction(ScheduleNotificationReadiness.Ready),
        )
        assertEquals(
            ScheduleNotificationAction.RequestPermission,
            scheduleNotificationAction(ScheduleNotificationReadiness.RuntimePermissionRequired),
        )
        assertEquals(
            ScheduleNotificationAction.OpenSettings,
            scheduleNotificationAction(ScheduleNotificationReadiness.AppNotificationsDisabled),
        )
        assertEquals(
            ScheduleNotificationAction.OpenSettings,
            scheduleNotificationAction(ScheduleNotificationReadiness.ChannelDisabled),
        )
    }

    @Test
    fun `known hash collision workflow IDs retain distinct intent identities`() {
        check("FB".hashCode() == "Ea".hashCode())

        assertNotEquals(scheduleIntentData("FB"), scheduleIntentData("Ea"))
    }

    @Test
    fun `workflow ID is safely encoded in intent identity`() {
        val identity = scheduleIntentData("folder/item with spaces")

        assertTrue(identity.startsWith("aiindexfinger://schedule/"))
        assertTrue(identity.substringAfterLast('/').none { it == '/' || it == ' ' })
    }

    @Test
    fun `scheduled reminders use only runnable workflows from authoritative storage`() {
        val ready = Workflow(
            id = "ready",
            name = "Ready",
            steps = listOf(Step.Delay("delay", 1)),
            state = WorkflowState.Ready,
        )
        val draft = ready.copy(id = "draft", name = "Draft", state = WorkflowState.Draft)

        assertEquals(
            listOf(ready),
            runnableWorkflowsForScheduling(
                WorkflowLoadResult.Loaded(WorkflowLibrary(workflows = listOf(ready, draft))),
            ),
        )
        assertEquals(
            listOf(ready),
            runnableWorkflowsForScheduling(
                WorkflowLoadResult.RecoveredFromBackup(WorkflowLibrary(workflows = listOf(draft, ready))),
            ),
        )
        assertEquals(emptyList<Workflow>(), runnableWorkflowsForScheduling(WorkflowLoadResult.Missing))
        assertEquals(
            null,
            runnableWorkflowsForScheduling(WorkflowLoadResult.UnsupportedVersion(999)),
        )
        assertEquals(
            null,
            runnableWorkflowsForScheduling(WorkflowLoadResult.Corrupt(null, null)),
        )
        assertEquals(ready, workflowForScheduledNotification("ready", listOf(ready)))
        assertEquals(null, workflowForScheduledNotification("missing", listOf(ready)))
        assertEquals(null, workflowForScheduledNotification("ready", null))
    }
}