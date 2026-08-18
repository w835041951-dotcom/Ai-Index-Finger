package com.aiindexfinger.automation

import com.aiindexfinger.scheduler.ScheduleNotificationReadiness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCommandIdentityTest {
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