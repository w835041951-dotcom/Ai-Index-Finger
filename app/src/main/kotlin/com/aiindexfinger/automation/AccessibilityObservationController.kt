package com.aiindexfinger.automation

internal class AccessibilityObservationController(
    private val onSourceUnavailable: () -> Unit = {},
    private val onObservationEnded: () -> Unit,
) {
    private var activeLeaseCount = 0

    val isObservationRequested: Boolean
        get() = activeLeaseCount > 0

    fun acquire(): AutoCloseable {
        activeLeaseCount += 1
        var released = false
        return AutoCloseable {
            if (!released) {
                released = true
                activeLeaseCount -= 1
                if (activeLeaseCount == 0) onObservationEnded()
            }
        }
    }

    fun sourceDisconnected() {
        onObservationEnded()
    }

    fun sourceUnavailable() {
        onSourceUnavailable()
    }
}
