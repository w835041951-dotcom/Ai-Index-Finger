package com.aiindexfinger

import com.aiindexfinger.model.AncestorSelector
import com.aiindexfinger.model.NodeSelector
import com.aiindexfinger.model.TextMatchMode

internal data class NodeSelectorDraft(
    val packageName: String = "",
    val viewId: String = "",
    val text: String = "",
    val textMatchMode: TextMatchMode = TextMatchMode.Exact,
    val contentDescription: String = "",
    val contentDescriptionMatchMode: TextMatchMode = TextMatchMode.Exact,
    val className: String = "",
    val matchIndex: Int = 0,
    val useAncestor: Boolean = false,
    val ancestorViewId: String = "",
    val ancestorText: String = "",
    val ancestorTextMatchMode: TextMatchMode = TextMatchMode.Exact,
    val ancestorContentDescription: String = "",
    val ancestorContentDescriptionMatchMode: TextMatchMode = TextMatchMode.Exact,
    val ancestorClassName: String = "",
    val originalSelector: NodeSelector? = null,
) {
    val hasTargetAttribute: Boolean
        get() = selectorHasAttribute(viewId, text, contentDescription, className)

    val hasAncestorAttribute: Boolean
        get() = selectorHasAttribute(
            ancestorViewId,
            ancestorText,
            ancestorContentDescription,
            ancestorClassName,
        )

    val isValid: Boolean
        get() = hasTargetAttribute && matchIndex in 0 until NodeSelector.MAX_MATCH_COUNT &&
            (!useAncestor || hasAncestorAttribute)

    fun toSelectorOrNull(): NodeSelector? {
        if (!isValid) return null
        originalSelector?.let { original ->
            if (withoutOriginal() == original.toDraft().withoutOriginal()) return original
        }
        val original = originalSelector
        val originalAncestor = original?.ancestor
        val ancestor = if (useAncestor) {
            AncestorSelector(
                viewId = ancestorViewId.preserveOrNormalize(originalAncestor?.viewId, originalAncestor != null),
                text = ancestorText.preserveOrNormalize(originalAncestor?.text, originalAncestor != null),
                textMatchMode = ancestorTextMatchMode,
                contentDescription = ancestorContentDescription.preserveOrNormalize(
                    originalAncestor?.contentDescription,
                    originalAncestor != null,
                ),
                contentDescriptionMatchMode = ancestorContentDescriptionMatchMode,
                className = ancestorClassName.preserveOrNormalize(
                    originalAncestor?.className,
                    originalAncestor != null,
                ),
            )
        } else {
            null
        }
        return NodeSelector(
            packageName = if (original != null && packageName == original.packageName) {
                original.packageName
            } else {
                packageName.trim()
            },
            viewId = viewId.preserveOrNormalize(original?.viewId, original != null),
            text = text.preserveOrNormalize(original?.text, original != null),
            textMatchMode = textMatchMode,
            contentDescription = contentDescription.preserveOrNormalize(
                original?.contentDescription,
                original != null,
            ),
            contentDescriptionMatchMode = contentDescriptionMatchMode,
            className = className.preserveOrNormalize(original?.className, original != null),
            matchIndex = matchIndex,
            ancestor = ancestor,
        )
    }

    private fun withoutOriginal(): NodeSelectorDraft = copy(originalSelector = null)
}

internal fun NodeSelector.toDraft(): NodeSelectorDraft = NodeSelectorDraft(
    packageName = packageName,
    viewId = viewId.orEmpty(),
    text = text.orEmpty(),
    textMatchMode = textMatchMode,
    contentDescription = contentDescription.orEmpty(),
    contentDescriptionMatchMode = contentDescriptionMatchMode,
    className = className.orEmpty(),
    matchIndex = matchIndex,
    useAncestor = ancestor != null,
    ancestorViewId = ancestor?.viewId.orEmpty(),
    ancestorText = ancestor?.text.orEmpty(),
    ancestorTextMatchMode = ancestor?.textMatchMode ?: TextMatchMode.Exact,
    ancestorContentDescription = ancestor?.contentDescription.orEmpty(),
    ancestorContentDescriptionMatchMode = ancestor?.contentDescriptionMatchMode ?: TextMatchMode.Exact,
    ancestorClassName = ancestor?.className.orEmpty(),
    originalSelector = this,
)

private fun String.normalizedOptionalText(): String? = trim().ifBlank { null }

private fun String.preserveOrNormalize(original: String?, hasOriginal: Boolean): String? =
    if (hasOriginal && this == original.orEmpty()) original else normalizedOptionalText()
