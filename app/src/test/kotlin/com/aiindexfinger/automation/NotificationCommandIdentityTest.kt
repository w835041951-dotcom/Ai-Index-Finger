package com.aiindexfinger.automation

import com.aiindexfinger.data.RunStepBranch
import com.aiindexfinger.scheduler.ScheduleNotificationReadiness
import com.aiindexfinger.model.Condition
import com.aiindexfinger.model.Step
import com.aiindexfinger.model.Value
import com.aiindexfinger.model.Workflow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class NotificationCommandIdentityTest {
    @Test
    fun notificationOperationsConvertOrdinaryFailuresButPreserveFatalErrors() {
        assertTrue(androidOperationSucceeded {})
        assertFalse(androidOperationSucceeded { error("notification service unavailable") })
        assertThrows(AssertionError::class.java) {
            androidOperationSucceeded { throw AssertionError("fatal") }
        }
    }

    @Test
    fun runningNotificationUsesStructuralLocationInsteadOfRawStepId() {
        val workflow = Workflow(
            id = "workflow",
            name = "Workflow",
            steps = listOf(
                Step.Delay("first-sensitive-id", 1),
                Step.IfElse(
                    "branch-sensitive-id",
                    Condition.Equals(Value.Literal("a"), Value.Literal("a")),
                    whenTrue = listOf(Step.Delay("nested-sensitive-id", 1)),
                    whenFalse = listOf(Step.Delay("false-sensitive-id", 1)),
                ),
            ),
        )

        val first = requireNotNull(runningStepLocation(workflow, "first-sensitive-id"))
        val whenTrue = requireNotNull(runningStepLocation(workflow, "nested-sensitive-id"))
        val whenFalse = requireNotNull(runningStepLocation(workflow, "false-sensitive-id"))

        assertEquals(listOf(0), first.segments.map { it.index })
        assertEquals(listOf(null), first.segments.map { it.branch })
        assertEquals(listOf(1, 0), whenTrue.segments.map { it.index })
        assertEquals(listOf(RunStepBranch.IfTrue, null), whenTrue.segments.map { it.branch })
        assertEquals(listOf(1, 0), whenFalse.segments.map { it.index })
        assertEquals(listOf(RunStepBranch.IfFalse, null), whenFalse.segments.map { it.branch })
        assertEquals(null, runningStepLocation(workflow, "missing-sensitive-id"))
    }

    @Test
    fun everyNotificationCommandHasAUniqueIdentity() {
        val identities = NotificationCommand.entries.map(::notificationCommandIdentity)

        assertEquals(NotificationCommand.entries.size, identities.map { it.action }.distinct().size)
        assertEquals(NotificationCommand.entries.size, identities.map { it.requestCode }.distinct().size)
        assertEquals(NotificationCommand.entries.size, identities.distinct().size)
    }

    @Test
    fun workflowContinuesOnlyWhileReadyControlNotificationIsActive() {
        assertTrue(runningControlsAvailable(ScheduleNotificationReadiness.Ready, true))
        assertFalse(runningControlsAvailable(ScheduleNotificationReadiness.Ready, false))
        assertFalse(
            runningControlsAvailable(
                ScheduleNotificationReadiness.RuntimePermissionRequired,
                true,
            ),
        )
        assertFalse(
            runningControlsAvailable(ScheduleNotificationReadiness.AppNotificationsDisabled, true),
        )
        assertFalse(
            runningControlsAvailable(ScheduleNotificationReadiness.ChannelDisabled, true),
        )
    }
}