package com.aiindexfinger.automation

import com.aiindexfinger.model.NodeSelector
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
        assertEquals(true, inspection.canUseSelector)
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
        assertEquals(false, inspection.canUseSelector)
    }

    @Test
    fun `recommends unique ancestor candidates for duplicate controls`() {
        val aliceRow = node(text = "Alice", bounds = "0 0 200 100")
        val aliceDelete = node(text = "Delete", bounds = "100 0 200 100", clickable = true)
        val bobRow = node(text = "Bob", bounds = "0 100 200 200")
        val bobDelete = node(text = "Delete", bounds = "100 100 200 200", clickable = true)
        val hierarchy = RecordingHierarchyCapture(
            nodes = listOf(
                RecordingHierarchyNode(aliceRow, null),
                RecordingHierarchyNode(aliceDelete, 0),
                RecordingHierarchyNode(bobRow, null),
                RecordingHierarchyNode(bobDelete, 2),
            ),
            complete = true,
        )

        val inspection = inspectElementAt(hierarchy, 150, 50)!!

        assertEquals(ElementSelectorReliability.Unique, inspection.selectorReliability)
        assertEquals("Delete", inspection.selector?.text)
        assertEquals("Alice", inspection.selector?.ancestor?.text)
        assertEquals(1, inspection.selectorMatchCount)
        assertEquals("Alice", inspection.ancestorCandidates.first().ancestor?.text)
        assertEquals(true, inspection.canUseSelector)
    }

    @Test
    fun `does not generate ancestor candidates for incomplete hierarchy`() {
        val row = node(text = "Alice", bounds = "0 0 200 100")
        val button = node(text = "Delete", bounds = "100 0 200 100", clickable = true)

        val inspection = inspectElementAt(
            RecordingHierarchyCapture(
                listOf(RecordingHierarchyNode(row, null), RecordingHierarchyNode(button, 0)),
                complete = false,
            ),
            150,
            50,
        )!!

        assertEquals(emptyList<NodeSelector>(), inspection.ancestorCandidates)
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
        assertEquals(false, inspection.canUseSelector)
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

    @Test
    fun `merged hierarchy finds controls in a second window`() {
        val page = node(viewId = "com.example:id/page", bounds = "0 0 300 300", clickable = true)
        val dialog = node(viewId = "com.example:id/confirm", bounds = "100 100 200 200", clickable = true)
        val hierarchy = mergeRecordingHierarchyCaptures(
            captures = listOf(
                RecordingHierarchyCapture(listOf(RecordingHierarchyNode(dialog, null)), complete = true),
                RecordingHierarchyCapture(listOf(RecordingHierarchyNode(page, null)), complete = true),
            ),
            limit = 10,
        )

        val inspection = inspectElementAt(hierarchy, 150, 150)!!

        assertEquals(dialog, inspection.node)
        assertEquals("com.example:id/confirm", inspection.selector?.viewId)
    }

    @Test
    fun `merged hierarchy preserves parent links and global ambiguity`() {
        val firstParent = node(text = "First", bounds = "0 0 100 100")
        val firstButton = node(text = "Delete", bounds = "0 0 100 100", clickable = true)
        val secondButton = firstButton.copy(bounds = "100 0 200 100")
        val hierarchy = mergeRecordingHierarchyCaptures(
            captures = listOf(
                RecordingHierarchyCapture(
                    listOf(
                        RecordingHierarchyNode(firstParent, null),
                        RecordingHierarchyNode(firstButton, 0),
                    ),
                    complete = true,
                ),
                RecordingHierarchyCapture(
                    listOf(RecordingHierarchyNode(secondButton, null)),
                    complete = true,
                ),
            ),
            limit = 10,
        )

        val inspection = inspectElementAt(hierarchy, 50, 50)!!

        assertEquals("First", inspection.selector?.ancestor?.text)
        assertEquals(ElementSelectorReliability.Unique, inspection.selectorReliability)
    }

    @Test
    fun `truncated merged hierarchy never claims uniqueness`() {
        val first = node(viewId = "com.example:id/action", bounds = "0 0 100 100", clickable = true)
        val second = first.copy(bounds = "100 0 200 100")
        val hierarchy = mergeRecordingHierarchyCaptures(
            captures = listOf(
                RecordingHierarchyCapture(listOf(RecordingHierarchyNode(first, null)), complete = true),
                RecordingHierarchyCapture(listOf(RecordingHierarchyNode(second, null)), complete = true),
            ),
            limit = 1,
        )

        val inspection = inspectElementAt(hierarchy, 50, 50)!!

        assertEquals(ElementSelectorReliability.HierarchyIncomplete, inspection.selectorReliability)
        assertEquals(false, inspection.canUseSelector)
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
