package com.aiindexfinger.scheduler

import android.app.NotificationManager
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