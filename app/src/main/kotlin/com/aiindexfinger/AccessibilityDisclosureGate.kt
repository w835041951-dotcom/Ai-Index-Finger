package com.aiindexfinger

import android.content.Context

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

internal class AccessibilityDisclosurePreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isAcknowledged(): Boolean = preferences.getBoolean(ACKNOWLEDGED_KEY, false)

    fun acknowledge() {
        preferences.edit().putBoolean(ACKNOWLEDGED_KEY, true).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "release_readiness"
        const val ACKNOWLEDGED_KEY = "accessibility_disclosure_acknowledged"
    }
}
