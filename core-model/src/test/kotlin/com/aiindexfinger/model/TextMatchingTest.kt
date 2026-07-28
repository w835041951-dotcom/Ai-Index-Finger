package com.aiindexfinger.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextMatchingTest {
    @Test
    fun `contains matches a substring`() {
        assertTrue(TextMatchMode.Contains.matches("Order", "Order 123 ready"))
        assertFalse(TextMatchMode.Contains.matches("order", "Order 123 ready"))
    }

    @Test
    fun `exact preserves existing behavior`() {
        assertTrue(TextMatchMode.Exact.matches("Ready", "Ready"))
        assertFalse(TextMatchMode.Exact.matches("Ready", "Ready now"))
        assertTrue(TextMatchMode.Exact.matches(null, "anything"))
    }
}