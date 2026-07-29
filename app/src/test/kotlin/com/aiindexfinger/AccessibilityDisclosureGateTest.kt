package com.aiindexfinger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityDisclosureGateTest {
    @Test
    fun firstSetupRequestShowsDisclosure() {
        val gate = AccessibilityDisclosureGate(initiallyAcknowledged = false)

        assertEquals(AccessibilityDisclosureAction.ShowDisclosure, gate.requestSetup())
        assertFalse(gate.isAcknowledged)
    }

    @Test
    fun acceptingDisclosureAcknowledgesAndOpensSettings() {
        val gate = AccessibilityDisclosureGate(initiallyAcknowledged = false)

        assertEquals(AccessibilityDisclosureAction.OpenSettings, gate.acceptDisclosure())
        assertTrue(gate.isAcknowledged)
        assertEquals(AccessibilityDisclosureAction.OpenSettings, gate.requestSetup())
    }

    @Test
    fun decliningDisclosureStaysInAppAndDoesNotAcknowledge() {
        val gate = AccessibilityDisclosureGate(initiallyAcknowledged = false)

        assertEquals(AccessibilityDisclosureAction.StayInApp, gate.declineDisclosure())
        assertFalse(gate.isAcknowledged)
        assertEquals(AccessibilityDisclosureAction.ShowDisclosure, gate.requestSetup())
    }

    @Test
    fun acknowledgedUserCanStillReviewDisclosure() {
        val gate = AccessibilityDisclosureGate(initiallyAcknowledged = true)

        assertEquals(AccessibilityDisclosureAction.ShowDisclosure, gate.reviewDisclosure())
    }
}
