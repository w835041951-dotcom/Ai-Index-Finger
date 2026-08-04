package com.aiindexfinger.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ElementInspectorTest {
    @Test
    fun `selects actionable ancestor of deepest node`() {
        val button = node(viewId = "com.example:id/save", bounds = "0 0 200 100", clickable = true)
        val label = node(text = "Save", bounds = "20 20 180 80", className = "android.widget.TextView")
        val hierarchy = RecordingHierarchyCapture(
            nodes = listOf(
                RecordingHierarchyNode(button, null),
                RecordingHierarchyNode(label, 0),
            ),
            complete = true,
        )

        val inspection = inspectElementAt(hierarchy, 50, 50)!!

        assertEquals(button, inspection.node)
        assertEquals("com.example:id/save", inspection.selector?.viewId)
        assertEquals(ElementSelectorReliability.Unique, inspection.selectorReliability)
    }

    @Test
    fun `reports ambiguous selector for duplicate controls`() {
        val first = node(text = "Delete", bounds = "0 0 100 100", clickable = true)
        val second = first.copy(bounds = "100 0 200 100")
        val hierarchy = RecordingHierarchyCapture(
            listOf(RecordingHierarchyNode(first, null), RecordingHierarchyNode(second, null)),
            complete = true,
        )

        val inspection = inspectElementAt(hierarchy, 50, 50)!!

        assertEquals(ElementSelectorReliability.Ambiguous, inspection.selectorReliability)
        assertEquals(2, inspection.selectorMatchCount)
    }

    @Test
    fun `never claims uniqueness for incomplete hierarchy`() {
        val target = node(viewId = "com.example:id/save", bounds = "0 0 100 100", clickable = true)

        val inspection = inspectElementAt(
            RecordingHierarchyCapture(listOf(RecordingHierarchyNode(target, null)), complete = false),
            50,
            50,
        )!!

        assertNull(inspection.selector)
        assertEquals(ElementSelectorReliability.HierarchyIncomplete, inspection.selectorReliability)
    }

    @Test
    fun `returns null outside accessible nodes`() {
        val target = node(viewId = "com.example:id/save", bounds = "0 0 100 100", clickable = true)

        assertNull(inspectElementAt(
            RecordingHierarchyCapture(listOf(RecordingHierarchyNode(target, null)), complete = true),
            150,
            150,
        ))
    }

    private fun node(
        viewId: String? = null,
        text: String? = null,
        bounds: String,
        className: String = "android.widget.Button",
        clickable: Boolean = false,
    ) = ObservedNode(
        packageName = "com.example",
        viewId = viewId,
        text = text,
        contentDescription = null,
        className = className,
        bounds = bounds,
        clickable = clickable,
        enabled = true,
    )
}
