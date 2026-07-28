package com.aiindexfinger.automation

import com.aiindexfinger.model.NodeSelector

object SelectorRecommendations {
    fun candidates(node: ObservedNode): List<NodeSelector> {
        val candidates = buildList {
            node.viewId?.let { add(NodeSelector(node.packageName, viewId = it)) }
            node.contentDescription?.let {
                add(NodeSelector(node.packageName, contentDescription = it, className = node.className))
            }
            node.text?.let { add(NodeSelector(node.packageName, text = it, className = node.className)) }
            if (node.viewId != null || node.text != null || node.contentDescription != null) {
                add(
                    NodeSelector(
                        packageName = node.packageName,
                        viewId = node.viewId,
                        text = node.text,
                        contentDescription = node.contentDescription,
                        className = node.className,
                    ),
                )
            }
        }
        return candidates.distinct()
    }
}