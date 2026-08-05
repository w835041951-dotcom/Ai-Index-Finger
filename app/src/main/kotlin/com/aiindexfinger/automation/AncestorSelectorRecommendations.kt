package com.aiindexfinger.automation

import com.aiindexfinger.model.AncestorSelector

internal fun ancestorSelectorCandidates(node: ObservedNode): List<AncestorSelector> = buildList {
    node.viewId?.let { add(AncestorSelector(viewId = it)) }
    node.contentDescription?.let {
        add(AncestorSelector(contentDescription = it, className = node.className))
    }
    node.text?.let { add(AncestorSelector(text = it, className = node.className)) }
    if (node.viewId != null || node.text != null || node.contentDescription != null) {
        add(
            AncestorSelector(
                viewId = node.viewId,
                text = node.text,
                contentDescription = node.contentDescription,
                className = node.className,
            ),
        )
    }
}.distinct()