package com.aiindexfinger.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordingTargetResolverTest {
    @Test
    fun disappearingChildResolvesToUniqueClickableAncestor() {
        val resolver = RecordingTargetResolver()
        val button = node(viewId = "com.example:id/save", text = null, bounds = "0 0 200 100")
        val label = node(
            viewId = null,
            text = "Save",
            className = "android.widget.TextView",
            bounds = "20 20 180 80",
            clickable = false,
        )
        resolver.update(snapshot(900, listOf(
            RecordingHierarchyNode(button, null),
            RecordingHierarchyNode(label, 0),
        )))

        val resolved = resolver.resolve(label, "com.example", 7, 1_000)!!

        assertEquals(button, resolved.node)
        assertEquals("com.example:id/save", resolved.selector?.viewId)
    }

    @Test
    fun longClickChildResolvesToLongClickableAncestor() {
        val resolver = RecordingTargetResolver()
        val parent = node(
            viewId = "com.example:id/menu",
            text = null,
            bounds = "0 0 200 100",
            clickable = false,
            longClickable = true,
        )
        val child = node(
            viewId = null,
            text = "Menu",
            className = "android.widget.TextView",
            bounds = "20 20 180 80",
            clickable = false,
        )
        resolver.update(snapshot(900, listOf(
            RecordingHierarchyNode(parent, null),
            RecordingHierarchyNode(child, 0),
        )))

        val resolved = resolver.resolve(
            child,
            "com.example",
            7,
            1_000,
            RecordingNodeCapability.LongClick,
        )!!

        assertEquals(parent, resolved.node)
        assertEquals("com.example:id/menu", resolved.selector?.viewId)
    }

    @Test
    fun duplicateControlsAreNotGuessed() {
        val resolver = RecordingTargetResolver()
        val duplicate = node(viewId = null, text = "Delete", bounds = "0 0 100 100")
        resolver.update(snapshot(900, listOf(
            RecordingHierarchyNode(duplicate, null),
            RecordingHierarchyNode(duplicate.copy(bounds = "100 0 200 100"), null),
        )))

        val resolved = resolver.resolve(
            duplicate.copy(bounds = "invalid"),
            "com.example",
            7,
            1_000,
        )

        assertEquals(duplicate.copy(bounds = "invalid"), resolved?.node)
        assertNull(resolved?.selector)
    }

    @Test
    fun postClickSnapshotIsNeverUsedToRecoverEarlierClick() {
        val resolver = RecordingTargetResolver()
        val oldNode = node(viewId = "com.example:id/old", text = "Open")
        val newNode = node(viewId = "com.example:id/new", text = "Next")
        resolver.update(snapshot(900, listOf(RecordingHierarchyNode(oldNode, null))))
        resolver.update(snapshot(1_100, listOf(RecordingHierarchyNode(newNode, null))))

        val resolved = resolver.resolve(oldNode, "com.example", 7, 1_000)!!

        assertEquals(oldNode, resolved.node)
        assertEquals("com.example:id/old", resolved.selector?.viewId)
    }

    @Test
    fun postClickOnlySnapshotLeavesSourceAsCoordinateOnly() {
        val resolver = RecordingTargetResolver()
        val oldSource = node(viewId = "com.example:id/old", text = "Open")
        resolver.update(snapshot(1_100, listOf(
            RecordingHierarchyNode(node(viewId = "com.example:id/new", text = "Next"), null),
        )))

        val resolved = resolver.resolve(oldSource, "com.example", 7, 1_000)!!

        assertEquals(oldSource, resolved.node)
        assertNull(resolved.selector)
    }

    @Test
    fun hierarchyMutationBeforeDelayedClickPreventsStaleRecovery() {
        val resolver = RecordingTargetResolver()
        val oldSource = node(viewId = "com.example:id/old", text = "Open")
        resolver.update(snapshot(900, listOf(RecordingHierarchyNode(oldSource, null))))
        resolver.markHierarchyMutation("com.example", 7, 950)

        val resolved = resolver.resolve(oldSource, "com.example", 7, 1_000)!!

        assertEquals(oldSource, resolved.node)
        assertNull(resolved.selector)
    }

    @Test
    fun duplicateAncestorSelectorIsRejectedEvenWhenChildIsUnique() {
        val resolver = RecordingTargetResolver()
        val firstButton = node(viewId = "com.example:id/action", text = null, bounds = "0 0 100 100")
        val secondButton = firstButton.copy(bounds = "100 0 200 100")
        val uniqueChild = node(
            viewId = null,
            text = "Unique label",
            className = "android.widget.TextView",
            bounds = "10 10 90 90",
            clickable = false,
        )
        resolver.update(snapshot(900, listOf(
            RecordingHierarchyNode(firstButton, null),
            RecordingHierarchyNode(uniqueChild, 0),
            RecordingHierarchyNode(secondButton, null),
        )))

        val resolved = resolver.resolve(uniqueChild, "com.example", 7, 1_000)!!

        assertEquals(firstButton, resolved.node)
        assertNull(resolved.selector)
    }

    @Test
    fun truncatedSnapshotNeverClaimsSelectorUniqueness() {
        val resolver = RecordingTargetResolver()
        val source = node(viewId = "com.example:id/action")
        resolver.update(
            snapshot(900, listOf(RecordingHierarchyNode(source, null))).copy(complete = false),
        )

        val resolved = resolver.resolve(source, "com.example", 7, 1_000)!!

        assertEquals(source, resolved.node)
        assertNull(resolved.selector)
    }

    @Test
    fun expiredOrWrongWindowSnapshotIsIgnored() {
        val resolver = RecordingTargetResolver(snapshotTtlMillis = 100)
        val source = node(viewId = "com.example:id/action")
        resolver.update(snapshot(800, listOf(RecordingHierarchyNode(source, null))))

        assertNull(resolver.resolve(source, "com.example", 8, 850)?.selector)
        assertNull(resolver.resolve(source, "com.example", 7, 1_000)?.selector)
    }

    @Test
    fun cacheIsBoundedAndClearable() {
        val resolver = RecordingTargetResolver(maxSnapshots = 2)
        repeat(3) { index ->
            resolver.update(snapshot(index.toLong(), listOf(RecordingHierarchyNode(node(text = "$index"), null))))
        }

        assertEquals(2, resolver.snapshotCount())
        resolver.clear()
        assertEquals(0, resolver.snapshotCount())
    }

    private fun snapshot(
        eventTimeMillis: Long,
        nodes: List<RecordingHierarchyNode>,
    ) = RecordingHierarchySnapshot(
        packageName = "com.example",
        windowId = 7,
        eventTimeMillis = eventTimeMillis,
        nodes = nodes,
    )

    private fun node(
        viewId: String? = "com.example:id/action",
        text: String? = "Action",
        className: String? = "android.widget.Button",
        bounds: String = "0 0 100 100",
        clickable: Boolean = true,
        longClickable: Boolean = false,
        enabled: Boolean = true,
    ) = ObservedNode(
        packageName = "com.example",
        viewId = viewId,
        text = text,
        contentDescription = null,
        className = className,
        bounds = bounds,
        clickable = clickable,
        enabled = enabled,
        longClickable = longClickable,
    )
}
