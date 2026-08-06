package com.aiindexfinger

import com.aiindexfinger.automation.ObservedNode
import org.junit.Assert.assertEquals
import org.junit.Test

class ObservedControlCandidatesTest {
    @Test
    fun actionableIdentifiableControlsArePrioritizedWithinLimit() {
        val layout = node(className = "android.view.ViewGroup")
        val label = node(text = "Settings")
        val button = node(viewId = "com.example:id/save", clickable = true)

        val result = listOf(layout, label, button).observedControlCandidates(limit = 2)

        assertEquals(listOf(button, label), result)
    }

    @Test
    fun originalOrderIsPreservedWithinPriorityGroups() {
        val first = node(text = "First", clickable = true)
        val second = node(text = "Second", clickable = true)

        assertEquals(
            listOf(first, second),
            listOf(first, second).observedControlCandidates(limit = 2),
        )
    }

    private fun node(
        viewId: String? = null,
        text: String? = null,
        className: String = "android.widget.TextView",
        clickable: Boolean = false,
    ) = ObservedNode(
        packageName = "com.example",
        viewId = viewId,
        text = text,
        contentDescription = null,
        className = className,
        bounds = "0 0 100 100",
        clickable = clickable,
        enabled = true,
    )
}