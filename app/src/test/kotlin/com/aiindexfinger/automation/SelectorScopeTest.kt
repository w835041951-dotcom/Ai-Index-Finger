package com.aiindexfinger.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectorScopeTest {
    @Test
    fun blankPackageUsesOnlyTheActiveWindow() {
        assertTrue(selectorUsesActiveWindow(""))
        assertTrue(selectorUsesActiveWindow("   "))
    }

    @Test
    fun explicitPackageUsesPackageFilteredWindows() {
        assertFalse(selectorUsesActiveWindow("com.example"))
    }
}