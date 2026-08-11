package com.aiindexfinger.scheduler

import android.app.NotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleNotificationIdentityTest {
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
}