package com.aiindexfinger.automation

import com.aiindexfinger.model.AncestorSelector
import com.aiindexfinger.model.NodeSelector
import com.aiindexfinger.model.matches

enum class ElementSelectorReliability {
    Unique,
    Ambiguous,
    Unavailable,
    HierarchyIncomplete,
}

data class ElementInspection(
    val node: ObservedNode,
    val selector: NodeSelector?,
    val selectorReliability: ElementSelectorReliability,
    val selectorMatchCount: Int,
    val ancestorCandidates: List<NodeSelector> = emptyList(),
) {
    val canUseSelector: Boolean
        get() = selector != null && selectorReliability == ElementSelectorReliability.Unique
}

fun inspectElementAt(
    hierarchy: RecordingHierarchyCapture,
    x: Int,
    y: Int,
): ElementInspection? {
    val containingIndexes = hierarchy.nodes.indices
        .filter { hierarchy.nodes[it].node.contains(x, y) }
    val topWindowIndex = containingIndexes.minOfOrNull { hierarchy.nodes[it].windowIndex } ?: return null
    val hitIndex = containingIndexes
        .filter { hierarchy.nodes[it].windowIndex == topWindowIndex }
        .maxWithOrNull(
            compareBy<Int> { hierarchy.nodes.depthOf(it) }
                .thenByDescending { hierarchy.nodes[it].node.area() },
        ) ?: return null
    val targetIndex = hierarchy.nodes.actionableAncestorIndex(hitIndex) ?: hitIndex
    val target = hierarchy.nodes[targetIndex].node
    val candidates = SelectorRecommendations.candidates(target)
    if (!hierarchy.complete) {
        return ElementInspection(target, null, ElementSelectorReliability.HierarchyIncomplete, 0)
    }
    val assessed = candidates.map { selector ->
        selector to hierarchy.nodes.indices.count { hierarchy.nodes.matches(it, selector) }
    }
    val unique = assessed.firstOrNull { it.second == 1 }
    if (unique != null) {
        return ElementInspection(target, unique.first, ElementSelectorReliability.Unique, 1)
    }
    val best = assessed.minByOrNull { it.second }
    val ancestorCandidates = candidates.flatMap { selector ->
        hierarchy.nodes.ancestorCandidates(targetIndex, selector)
    }.distinct()
    val uniqueAncestorCandidates = ancestorCandidates.filter { selector ->
        hierarchy.nodes.indices.count { hierarchy.nodes.matches(it, selector) } == 1
    }
    if (uniqueAncestorCandidates.isNotEmpty()) {
        return ElementInspection(
            node = target,
            selector = uniqueAncestorCandidates.first(),
            selectorReliability = ElementSelectorReliability.Unique,
            selectorMatchCount = 1,
            ancestorCandidates = uniqueAncestorCandidates,
        )
    }
    return ElementInspection(
        node = target,
        selector = best?.first,
        selectorReliability = if (best == null) {
            ElementSelectorReliability.Unavailable
        } else {
            ElementSelectorReliability.Ambiguous
        },
        selectorMatchCount = best?.second ?: 0,
        ancestorCandidates = emptyList(),
    )
}

internal fun mergeRecordingHierarchyCaptures(
    captures: List<RecordingHierarchyCapture>,
    limit: Int,
): RecordingHierarchyCapture {
    require(limit >= 0) { "Node limit cannot be negative" }
    val nextIndexes = IntArray(captures.size)
    val remappedIndexes = captures.map { mutableMapOf<Int, Int>() }
    val result = mutableListOf<RecordingHierarchyNode>()
    var hierarchyValid = true
    while (result.size < limit) {
        var foundNode = false
        captures.forEachIndexed { captureIndex, capture ->
            if (result.size >= limit) return@forEachIndexed
            val sourceIndex = nextIndexes[captureIndex]
            val sourceNode = capture.nodes.getOrNull(sourceIndex) ?: return@forEachIndexed
            foundNode = true
            val parentIndex = sourceNode.parentIndex?.let { parent ->
                remappedIndexes[captureIndex][parent].also {
                    if (it == null) hierarchyValid = false
                }
            }
            val resultIndex = result.size
            result += sourceNode.copy(parentIndex = parentIndex, windowIndex = captureIndex)
            remappedIndexes[captureIndex][sourceIndex] = resultIndex
            nextIndexes[captureIndex] = sourceIndex + 1
        }
        if (!foundNode) break
    }
    val complete = hierarchyValid && captures.indices.all { index ->
        captures[index].complete && nextIndexes[index] == captures[index].nodes.size
    }
    return RecordingHierarchyCapture(result, complete)
}

private fun List<RecordingHierarchyNode>.ancestorCandidates(
    targetIndex: Int,
    targetSelector: NodeSelector,
): List<NodeSelector> {
    val hierarchy = this
    return buildList {
        var ancestorIndex: Int? = hierarchy.getOrNull(targetIndex)?.parentIndex
        while (ancestorIndex != null) {
            val ancestorNode = hierarchy.getOrNull(ancestorIndex) ?: break
            ancestorSelectorCandidates(ancestorNode.node).forEach { ancestor ->
                add(targetSelector.copy(ancestor = ancestor))
            }
            ancestorIndex = ancestorNode.parentIndex
        }
    }
}

private fun List<RecordingHierarchyNode>.depthOf(index: Int): Int {
    var depth = 0
    var current = this[index].parentIndex
    while (current != null) {
        depth++
        current = getOrNull(current)?.parentIndex
    }
    return depth
}

private fun List<RecordingHierarchyNode>.actionableAncestorIndex(index: Int): Int? {
    var current: Int? = index
    while (current != null) {
        val node = this[current].node
        if (node.enabled && (node.clickable || node.longClickable || node.scrollable)) return current
        current = this[current].parentIndex
    }
    return null
}

private fun ObservedNode.contains(x: Int, y: Int): Boolean {
    val values = bounds.split(' ').mapNotNull(String::toIntOrNull)
    return values.size == 4 && x >= values[0] && x < values[2] && y >= values[1] && y < values[3]
}

private fun ObservedNode.area(): Long {
    val values = bounds.split(' ').mapNotNull(String::toIntOrNull)
    if (values.size != 4) return Long.MAX_VALUE
    return (values[2] - values[0]).toLong().coerceAtLeast(0) *
        (values[3] - values[1]).toLong().coerceAtLeast(0)
}

private fun List<RecordingHierarchyNode>.matches(index: Int, selector: NodeSelector): Boolean {
    val hierarchyNode = getOrNull(index) ?: return false
    if (!selector.matches(hierarchyNode.node)) return false
    val ancestor = selector.ancestor ?: return true
    var ancestorIndex = hierarchyNode.parentIndex
    while (ancestorIndex != null) {
        val candidate = getOrNull(ancestorIndex) ?: return false
        if (ancestor.matches(candidate.node)) return true
        ancestorIndex = candidate.parentIndex
    }
    return false
}

private fun NodeSelector.matches(node: ObservedNode): Boolean =
    packageName == node.packageName &&
        (viewId == null || viewId == node.viewId) &&
        textMatchMode.matches(text, node.text) &&
        contentDescriptionMatchMode.matches(contentDescription, node.contentDescription) &&
        (className == null || className == node.className)

private fun AncestorSelector.matches(node: ObservedNode): Boolean =
    (viewId == null || viewId == node.viewId) &&
        textMatchMode.matches(text, node.text) &&
        contentDescriptionMatchMode.matches(contentDescription, node.contentDescription) &&
        (className == null || className == node.className)
