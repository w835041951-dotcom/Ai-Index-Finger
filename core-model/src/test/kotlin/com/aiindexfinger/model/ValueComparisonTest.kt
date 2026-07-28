package com.aiindexfinger.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValueComparisonTest {
    @Test
    fun `evaluates equality operators`() {
        assertTrue(ComparisonOperator.Equals.evaluate("ready", "ready"))
        assertFalse(ComparisonOperator.Equals.evaluate("ready", "Ready"))
        assertTrue(ComparisonOperator.NotEquals.evaluate("ready", "waiting"))
    }

    @Test
    fun `evaluates contains operators`() {
        assertTrue(ComparisonOperator.Contains.evaluate("Order 123 ready", "123"))
        assertFalse(ComparisonOperator.Contains.evaluate("Order 123 ready", "order"))
        assertTrue(ComparisonOperator.NotContains.evaluate("Order 123 ready", "failed"))
    }
}