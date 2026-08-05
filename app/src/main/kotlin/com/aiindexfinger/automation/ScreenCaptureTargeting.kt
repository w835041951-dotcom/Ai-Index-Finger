package com.aiindexfinger.automation

import com.aiindexfinger.model.NodeSelector

data class CaptureNode(
    val packageName: String,
    val viewId: String?,
    val text: String?,
    val contentDescription: String?,
    val className: String?,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val depth: Int,
    val traversalOrder: Int,
    val clickable: Boolean,
) {
    val hasSelectorAttributes: Boolean
        get() = viewId != null || text != null || contentDescription != null

    val area: Long
        get() = (right - left).toLong().coerceAtLeast(0) * (bottom - top).toLong().coerceAtLeast(0)

    fun toObservedNode(): ObservedNode = ObservedNode(
        packageName = packageName,
        viewId = viewId,
        text = text,
        contentDescription = contentDescription,
        className = className,
        bounds = "$left,$top,$right,$bottom",
        clickable = clickable,
        enabled = true,
    )
}

data class ScreenPoint(val x: Int, val y: Int)

fun mapFitCenterTapToScreen(
    tapX: Float,
    tapY: Float,
    containerWidth: Int,
    containerHeight: Int,
    imageWidth: Int,
    imageHeight: Int,
): ScreenPoint? {
    if (containerWidth <= 0 || containerHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) return null
    val scale = minOf(containerWidth.toFloat() / imageWidth, containerHeight.toFloat() / imageHeight)
    val displayedWidth = imageWidth * scale
    val displayedHeight = imageHeight * scale
    val offsetX = (containerWidth - displayedWidth) / 2f
    val offsetY = (containerHeight - displayedHeight) / 2f
    if (tapX < offsetX || tapX >= offsetX + displayedWidth ||
        tapY < offsetY || tapY >= offsetY + displayedHeight
    ) return null
    return ScreenPoint(
        x = ((tapX - offsetX) / scale).toInt().coerceIn(0, imageWidth - 1),
        y = ((tapY - offsetY) / scale).toInt().coerceIn(0, imageHeight - 1),
    )
}

fun selectCaptureNode(nodes: List<CaptureNode>, point: ScreenPoint): CaptureNode? = nodes
    .asSequence()
    .filter { it.hasSelectorAttributes }
    .filter { point.x in it.left until it.right && point.y in it.top until it.bottom }
    .sortedWith(
        compareByDescending<CaptureNode> { it.clickable }
            .thenByDescending { it.depth }
            .thenBy { it.area }
            .thenByDescending { it.traversalOrder },
    )
    .firstOrNull()

fun recommendedSelector(node: CaptureNode, countMatches: (NodeSelector) -> Int): NodeSelector {
    val candidates = SelectorRecommendations.candidates(node.toObservedNode())
    return candidates.firstOrNull { countMatches(it) == 1 } ?: candidates.first()
}
