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
    fun `rejects negative match indexes and accepts large indexes`() {
        assertFailsWith<IllegalArgumentException> {
            NodeSelector("com.example", text = "Item", matchIndex = -1)
        }
        assertEquals(Int.MAX_VALUE, NodeSelector("com.example", text = "Item", matchIndex = Int.MAX_VALUE).matchIndex)
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