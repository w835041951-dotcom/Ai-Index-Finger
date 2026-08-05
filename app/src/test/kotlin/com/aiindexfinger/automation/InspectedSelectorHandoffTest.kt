package com.aiindexfinger.automation

import com.aiindexfinger.model.AncestorSelector
import com.aiindexfinger.model.NodeSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InspectedSelectorHandoffTest {
    @Test
    fun selectorCanBeConsumedOnlyOnceWithoutLosingAncestorFields() {
        val selector = NodeSelector(
            packageName = "com.example",
            text = "Delete",
            ancestor = AncestorSelector(text = "Alice"),
        )
        val handoff = InspectedSelectorHandoff()

        handoff.publish(selector)

        assertEquals(selector, handoff.consume())
        assertNull(handoff.consume())
        assertNull(handoff.selector.value)
    }

    @Test
    fun clearDiscardsPendingSelector() {
        val handoff = InspectedSelectorHandoff()
        handoff.publish(NodeSelector("com.example", text = "Delete"))

        handoff.clear()

        assertNull(handoff.consume())
    }
}