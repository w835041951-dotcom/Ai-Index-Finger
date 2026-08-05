package com.aiindexfinger.automation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLaunchVisibilityTest {
    @Test
    fun waitsUntilTheTargetPackageBecomesVisible() = runBlocking {
        var checks = 0

        val visible = awaitTargetPackageVisible(
            packageName = "com.example.target",
            isVisible = { packageName ->
                assertEquals("com.example.target", packageName)
                ++checks == 3
            },
            maxChecks = 5,
            pollIntervalMillis = 0,
        )

        assertTrue(visible)
        assertEquals(3, checks)
    }

    @Test
    fun returnsFalseWhenTheTargetPackageNeverBecomesVisible() = runBlocking {
        var checks = 0

        val visible = awaitTargetPackageVisible(
            packageName = "com.example.target",
            isVisible = { checks++; false },
            maxChecks = 3,
            pollIntervalMillis = 0,
        )

        assertFalse(visible)
        assertEquals(3, checks)
    }

    @Test
    fun launchVisibilityWaitIsCancellable() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                awaitTargetPackageVisible(
                    packageName = "com.example.target",
                    isVisible = { throw CancellationException("cancelled") },
                    maxChecks = 3,
                    pollIntervalMillis = 0,
                )
            }
        }
    }
}