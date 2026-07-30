package com.aiindexfinger.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StepExecutionSettingsTest {
    @Test
    fun `updates timeout and policy without changing action fields`() {
        val selector = NodeSelector("com.example", viewId = "com.example:id/button")
        val original = Step.Click("click", selector)

        val updated = original.withExecutionSettings(2_500, FailurePolicy.Continue)

        assertEquals(
            original.copy(timeoutMillis = 2_500, failurePolicy = FailurePolicy.Continue),
            updated,
        )
    }

    @Test
    fun `null timeout restores workflow default`() {
        val original = Step.Delay("delay", 100, timeoutMillis = 500)

        assertEquals(null, original.withExecutionSettings(null, FailurePolicy.Stop).timeoutMillis)
    }

    @Test
    fun `rejects non-positive timeout`() {
        assertFailsWith<IllegalArgumentException> {
            Step.Delay("delay", 100).withExecutionSettings(0, FailurePolicy.Stop)
        }
    }

    @Test
    fun `updates image click settings without changing template`() {
        val original = Step.ImageClick("image", "com.example", "aGVsbG8=", 24, 24)

        assertEquals(
            original.copy(timeoutMillis = 3_000, failurePolicy = FailurePolicy.Continue),
            original.withExecutionSettings(3_000, FailurePolicy.Continue),
        )
    }
}