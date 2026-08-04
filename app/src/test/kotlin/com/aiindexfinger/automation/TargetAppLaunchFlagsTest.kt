package com.aiindexfinger.automation

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetAppLaunchFlagsTest {
    @Test
    fun targetAppLaunchAddsNewTaskWithoutReorderingLauncherActivity() {
        val flags = targetAppLaunchFlags(0)

        assertTrue(flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertFalse(flags and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT != 0)
    }

    @Test
    fun targetAppLaunchPreservesOtherFlagsAndRemovesExistingReorderFlag() {
        val retainedFlag = Intent.FLAG_ACTIVITY_SINGLE_TOP
        val flags = targetAppLaunchFlags(retainedFlag or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)

        assertTrue(flags and retainedFlag != 0)
        assertTrue(flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertFalse(flags and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT != 0)
        assertEquals(retainedFlag or Intent.FLAG_ACTIVITY_NEW_TASK, flags)
    }
}
