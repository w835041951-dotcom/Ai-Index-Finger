package com.aiindexfinger.automation

import com.aiindexfinger.model.AncestorSelector
import com.aiindexfinger.model.matches

internal data class NodeMatchSnapshot(
    val viewId: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val className: String? = null,
)

internal fun AncestorSelector.matches(snapshot: NodeMatchSnapshot): Boolean =
    (viewId == null || viewId == snapshot.viewId) &&
        textMatchMode.matches(text, snapshot.text) &&
        contentDescriptionMatchMode.matches(contentDescription, snapshot.contentDescription) &&
        (className == null || className == snapshot.className)