package com.aiindexfinger.automation

import com.aiindexfinger.model.AncestorSelector
import com.aiindexfinger.model.TextMatchMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AncestorSelectorMatchingTest {
    @Test
    fun matchingAncestorRemainsStableWhenAnotherRowIsInsertedFirst() {
        val selector = AncestorSelector(text = "Alice")
        val insertedRow = NodeMatchSnapshot(text = "Bob", className = "android.widget.LinearLayout")
        val targetRow = NodeMatchSnapshot(text = "Alice", className = "android.widget.LinearLayout")

        assertFalse(selector.matches(insertedRow))
        assertTrue(selector.matches(targetRow))
    }

    @Test
    fun ancestorSupportsCombinedAttributesAndContainsMatching() {
        val selector = AncestorSelector(
            viewId = "com.example:id/row",
            text = "Ali",
            textMatchMode = TextMatchMode.Contains,
            className = "android.widget.LinearLayout",
        )

        assertTrue(
            selector.matches(
                NodeMatchSnapshot(
                    viewId = "com.example:id/row",
                    text = "Alice",
                    className = "android.widget.LinearLayout",
                ),
            ),
        )
        assertFalse(
            selector.matches(
                NodeMatchSnapshot(
                    viewId = "com.example:id/other",
                    text = "Alice",
                    className = "android.widget.LinearLayout",
                ),
            ),
        )
    }
}