package com.aiindexfinger.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityObservationControllerTest {
    @Test
    fun observationIsInactiveUntilLeaseIsAcquired() {
        val controller = AccessibilityObservationController {}

        assertFalse(controller.isObservationRequested)

        controller.acquire()

        assertTrue(controller.isObservationRequested)
    }

    @Test
    fun finalReleaseEndsObservationAndClearsData() {
        var clearCount = 0
        val controller = AccessibilityObservationController { clearCount += 1 }
        val firstLease = controller.acquire()
        val secondLease = controller.acquire()

        firstLease.close()

        assertTrue(controller.isObservationRequested)
        assertEquals(0, clearCount)

        secondLease.close()

        assertFalse(controller.isObservationRequested)
        assertEquals(1, clearCount)
    }

    @Test
    fun releasingLeaseTwiceDoesNotAffectAnotherConsumer() {
        var clearCount = 0
        val controller = AccessibilityObservationController { clearCount += 1 }
        val firstLease = controller.acquire()
        val secondLease = controller.acquire()

        firstLease.close()
        firstLease.close()

        assertTrue(controller.isObservationRequested)
        assertEquals(0, clearCount)

        secondLease.close()

        assertFalse(controller.isObservationRequested)
        assertEquals(1, clearCount)
    }

    @Test
    fun disconnectClearsDataWithoutDroppingActiveDemand() {
        var clearCount = 0
        val controller = AccessibilityObservationController { clearCount += 1 }
        val lease = controller.acquire()

        controller.sourceDisconnected()

        assertTrue(controller.isObservationRequested)
        assertEquals(1, clearCount)

        lease.close()

        assertFalse(controller.isObservationRequested)
        assertEquals(2, clearCount)
    }

    @Test
    fun unavailableSourcePausesCaptureWithoutClearingObservedData() {
        var clearCount = 0
        var pauseCount = 0
        val controller = AccessibilityObservationController(
            onObservationEnded = { clearCount += 1 },
            onSourceUnavailable = { pauseCount += 1 },
        )
        val lease = controller.acquire()

        controller.sourceUnavailable()

        assertTrue(controller.isObservationRequested)
        assertEquals(1, pauseCount)
        assertEquals(0, clearCount)

        lease.close()

        assertFalse(controller.isObservationRequested)
        assertEquals(1, clearCount)
    }
}
