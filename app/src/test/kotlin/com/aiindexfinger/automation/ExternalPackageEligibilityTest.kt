package com.aiindexfinger.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalPackageEligibilityTest {
    private val ownPackage = "com.aiindexfinger"
    private val homePackages = setOf("com.example.launcher")

    @Test
    fun targetApplicationIsEligible() {
        assertTrue(isEligibleExternalPackage("com.example.target", ownPackage, homePackages))
    }

    @Test
    fun ownApplicationIsExcluded() {
        assertFalse(isEligibleExternalPackage(ownPackage, ownPackage, homePackages))
    }

    @Test
    fun launcherAndSystemUiAreExcluded() {
        assertFalse(isEligibleExternalPackage("com.example.launcher", ownPackage, homePackages))
        assertFalse(isEligibleExternalPackage("com.android.systemui", ownPackage, homePackages))
    }
}