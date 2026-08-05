package com.aiindexfinger.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NodeSelectorTest {
    @Test
    fun `allows an active-window selector without a package`() {
        assertEquals("", NodeSelector("", text = "Item").packageName)
    }

    @Test
    fun `still requires at least one node attribute`() {
        assertFailsWith<IllegalArgumentException> { NodeSelector("") }
    }

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

    @Test
    fun `allows an optional ancestor constraint`() {
        val selector = NodeSelector(
            packageName = "com.example",
            text = "Delete",
            ancestor = AncestorSelector(text = "Alice"),
        )

        assertEquals("Alice", selector.ancestor?.text)
    }

    @Test
    fun `ancestor constraint requires a node attribute`() {
        assertFailsWith<IllegalArgumentException> { AncestorSelector() }
    }
}