package com.aiindexfinger.automation

import android.accessibilityservice.AccessibilityServiceInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilityServiceConfigurationTest {
    @Test
    fun monitoringRequestsAllRequiredViewHierarchyFlags() {
        val existingFlag = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS

        val configuredFlags = accessibilityServiceFlags(existingFlag)

        assertEquals(existingFlag, configuredFlags and existingFlag)
        assertEquals(
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS,
            configuredFlags and AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS,
        )
        assertEquals(
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS,
            configuredFlags and AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS,
        )
        assertEquals(
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS,
            configuredFlags and AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS,
        )
    }
}