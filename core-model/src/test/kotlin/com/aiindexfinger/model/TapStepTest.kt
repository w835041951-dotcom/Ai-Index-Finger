package com.aiindexfinger.model

import kotlin.test.Test
import kotlin.test.assertFailsWith

class TapStepTest {
    @Test
    fun `rejects negative coordinates`() {
        assertFailsWith<IllegalArgumentException> {
            Step.Tap("tap", x = -1, y = 100)
        }
    }
}