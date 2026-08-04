package com.aiindexfinger.automation

import com.aiindexfinger.model.NodeSelector

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
)

fun inspectElementAt(
    hierarchy: RecordingHierarchyCapture,
    x: Int,
    y: Int,
): ElementInspection? {
    val hitIndex = hierarchy.nodes.indices
        .filter { hierarchy.nodes[it].node.contains(x, y) }
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
        selector to hierarchy.nodes.count { selector.matches(it.node) }
    }
    val unique = assessed.firstOrNull { it.second == 1 }
    if (unique != null) {
        return ElementInspection(target, unique.first, ElementSelectorReliability.Unique, 1)
    }
    val best = assessed.minByOrNull { it.second }
    return ElementInspection(
        node = target,
        selector = best?.first,
        selectorReliability = if (best == null) {
            ElementSelectorReliability.Unavailable
        } else {
            ElementSelectorReliability.Ambiguous
        },
        selectorMatchCount = best?.second ?: 0,
    )
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

private fun NodeSelector.matches(node: ObservedNode): Boolean =
    packageName == node.packageName &&
        (viewId == null || viewId == node.viewId) &&
        (text == null || text == node.text) &&
        (contentDescription == null || contentDescription == node.contentDescription) &&
        (className == null || className == node.className)
