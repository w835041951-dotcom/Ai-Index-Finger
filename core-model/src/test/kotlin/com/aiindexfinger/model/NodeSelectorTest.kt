package com.aiindexfinger.model

import kotlin.test.Test
import kotlin.test.assertFailsWith

class NodeSelectorTest {
    @Test
    fun `rejects a match index outside the supported range`() {
        assertFailsWith<IllegalArgumentException> {
            NodeSelector("com.example", text = "Item", matchIndex = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            NodeSelector(
                "com.example",
                text = "Item",
                matchIndex = NodeSelector.MAX_MATCH_COUNT,
            )
        }
    }
}