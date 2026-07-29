package com.aiindexfinger

internal enum class AccessibilityDisclosureAction {
    ShowDisclosure,
    OpenSettings,
    StayInApp,
}

internal class AccessibilityDisclosureGate(initiallyAcknowledged: Boolean) {
    var isAcknowledged: Boolean = initiallyAcknowledged
        private set

    fun requestSetup(): AccessibilityDisclosureAction =
        if (isAcknowledged) {
            AccessibilityDisclosureAction.OpenSettings
        } else {
            AccessibilityDisclosureAction.ShowDisclosure
        }

    fun reviewDisclosure(): AccessibilityDisclosureAction =
        AccessibilityDisclosureAction.ShowDisclosure

    fun acceptDisclosure(): AccessibilityDisclosureAction {
        isAcknowledged = true
        return AccessibilityDisclosureAction.OpenSettings
    }

    fun declineDisclosure(): AccessibilityDisclosureAction =
        AccessibilityDisclosureAction.StayInApp
}
