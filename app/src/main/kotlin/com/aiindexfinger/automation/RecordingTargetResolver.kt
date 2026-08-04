package com.aiindexfinger.automation

import com.aiindexfinger.model.NodeSelector
import com.aiindexfinger.model.RecordedClickFallbackCause

data class RecordingHierarchyNode(
    val node: ObservedNode,
    val parentIndex: Int?,
)

data class RecordingHierarchyCapture(
    val nodes: List<RecordingHierarchyNode>,
    val complete: Boolean,
)

data class RecordingHierarchySnapshot(
    val packageName: String,
    val windowId: Int,
    val eventTimeMillis: Long,
    val nodes: List<RecordingHierarchyNode>,
    val complete: Boolean = true,
)

enum class RecordingIssueReason {
    SourceUnavailable,
    SourceInvalid,
    ControlNotUnique,
    SensitiveText,
}

data class RecordingIssue(
    val eventTimeMillis: Long,
    val packageName: String,
    val reason: RecordingIssueReason,
)

data class ResolvedRecordingNode(
    val node: ObservedNode,
    val selector: NodeSelector?,
    val fallbackCause: RecordedClickFallbackCause? = null,
)

enum class RecordingNodeCapability {
    Click,
    LongClick,
    Scroll,
}

internal class RecordingTargetResolver(
    private val maxSnapshots: Int = 2,
    private val snapshotTtlMillis: Long = 2_000,
) {
    private val snapshots = ArrayDeque<RecordingHierarchySnapshot>()
    private val mutationBarriers = mutableMapOf<Pair<String, Int>, Long>()

    init {
        require(maxSnapshots > 0) { "Snapshot capacity must be positive" }
        require(snapshotTtlMillis > 0) { "Snapshot TTL must be positive" }
    }

    fun update(snapshot: RecordingHierarchySnapshot) {
        snapshots.removeAll { it.packageName != snapshot.packageName }
        snapshots.addLast(snapshot)
        while (snapshots.size > maxSnapshots) snapshots.removeFirst()
    }

    fun markHierarchyMutation(packageName: String, windowId: Int, eventTimeMillis: Long) {
        mutationBarriers[packageName to windowId] = eventTimeMillis
    }

    fun resolve(
        source: ObservedNode,
        packageName: String,
        windowId: Int,
        eventTimeMillis: Long,
        capability: RecordingNodeCapability = RecordingNodeCapability.Click,
    ): ResolvedRecordingNode? {
        val snapshot = snapshots
            .asReversed()
            .firstOrNull {
                it.packageName == packageName &&
                    it.windowId == windowId &&
                    it.eventTimeMillis <= eventTimeMillis &&
                    eventTimeMillis - it.eventTimeMillis <= snapshotTtlMillis
            } ?: return resolveWithoutSnapshot(source, RecordedClickFallbackCause.HierarchyUnavailable)
        val mutationTime = mutationBarriers[packageName to windowId]
        if (mutationTime != null && mutationTime <= eventTimeMillis && mutationTime >= snapshot.eventTimeMillis) {
            return resolveWithoutSnapshot(source, RecordedClickFallbackCause.HierarchyChanged)
        }
        val sourceIndex = uniqueSourceIndex(snapshot, source)
            ?: return resolveWithoutSnapshot(source, RecordedClickFallbackCause.SourceNotUnique)
        val targetIndex = capableAncestorIndex(snapshot, sourceIndex, capability) ?: sourceIndex
        val target = snapshot.nodes[targetIndex].node
        if (!snapshot.complete) {
            return ResolvedRecordingNode(target, null, RecordedClickFallbackCause.HierarchyIncomplete)
        }
        val selector = uniqueSelector(snapshot, target)
        return ResolvedRecordingNode(
            node = target,
            selector = selector,
            fallbackCause = RecordedClickFallbackCause.SelectorNotUnique.takeIf { selector == null },
        )
    }

    fun clear() {
        snapshots.clear()
        mutationBarriers.clear()
    }

    internal fun snapshotCount(): Int = snapshots.size

    private fun resolveWithoutSnapshot(
        source: ObservedNode,
        fallbackCause: RecordedClickFallbackCause,
    ): ResolvedRecordingNode = ResolvedRecordingNode(source, null, fallbackCause)

    private fun uniqueSourceIndex(snapshot: RecordingHierarchySnapshot, source: ObservedNode): Int? {
        val matchers = buildList<(ObservedNode) -> Boolean> {
            source.viewId?.let { viewId -> add { it.viewId == viewId } }
            source.contentDescription?.let { description ->
                add { it.contentDescription == description && it.className == source.className }
            }
            source.text?.let { text -> add { it.text == text && it.className == source.className } }
            add { it.bounds == source.bounds && it.className == source.className }
        }
        matchers.forEach { matches ->
            val indexes = snapshot.nodes.indices.filter { matches(snapshot.nodes[it].node) }
            if (indexes.size == 1) return indexes.single()
        }
        return null
    }

    private fun capableAncestorIndex(
        snapshot: RecordingHierarchySnapshot,
        sourceIndex: Int,
        capability: RecordingNodeCapability,
    ): Int? {
        var currentIndex: Int? = sourceIndex
        while (currentIndex != null) {
            val current = snapshot.nodes[currentIndex]
            val capable = when (capability) {
                RecordingNodeCapability.Click -> current.node.clickable
                RecordingNodeCapability.LongClick -> current.node.longClickable
                RecordingNodeCapability.Scroll -> current.node.scrollable
            }
            if (current.node.enabled && capable) return currentIndex
            currentIndex = current.parentIndex
        }
        return null
    }

    private fun uniqueSelector(snapshot: RecordingHierarchySnapshot, node: ObservedNode): NodeSelector? =
        SelectorRecommendations.candidates(node).firstOrNull { selector ->
            snapshot.nodes.count { selector.matches(it.node) } == 1
        }

    private fun NodeSelector.matches(node: ObservedNode): Boolean =
        packageName == node.packageName &&
            (viewId == null || viewId == node.viewId) &&
            (text == null || text == node.text) &&
            (contentDescription == null || contentDescription == node.contentDescription) &&
            (className == null || className == node.className)
}
