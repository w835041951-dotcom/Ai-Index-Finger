package com.aiindexfinger

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectorEditorValidationTest {
    @Test
    fun selectorCanBeSavedWithoutAPackageWhenItHasAnAttribute() {
        assertTrue(selectorHasAttribute("", "Continue", "", ""))
    }

    @Test
    fun selectorStillRequiresANodeAttribute() {
        assertFalse(selectorHasAttribute("", "", "", ""))
    }
}