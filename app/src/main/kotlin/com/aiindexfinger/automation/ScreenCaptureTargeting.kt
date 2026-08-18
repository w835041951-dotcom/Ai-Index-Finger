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

internal fun largestWindowCenter(bounds: List<ScreenBounds>): ScreenPoint? = bounds
    .filter { it.right > it.left && it.bottom > it.top }
    .maxByOrNull {
        (it.right - it.left).toLong() * (it.bottom - it.top).toLong()
    }
    ?.let {
        ScreenPoint(
            x = it.left + (it.right - it.left) / 2,
            y = it.top + (it.bottom - it.top) / 2,
        )
    }

internal fun captureGeometryIsCompatible(
    bitmapWidth: Int,
    bitmapHeight: Int,
    screenBounds: ScreenBounds,
): Boolean {
    val screenWidth = screenBounds.right - screenBounds.left
    val screenHeight = screenBounds.bottom - screenBounds.top
    if (bitmapWidth <= 0 || bitmapHeight <= 0 || screenWidth <= 0 || screenHeight <= 0) return false
    val widthScaled = bitmapWidth.toLong() * screenHeight
    val heightScaled = bitmapHeight.toLong() * screenWidth
    val difference = kotlin.math.abs(widthScaled - heightScaled)
    return difference * 1_000 <= maxOf(widthScaled, heightScaled) * 5
}

internal fun mapBitmapPointToScreen(
    point: ScreenPoint,
    bitmapWidth: Int,
    bitmapHeight: Int,
    screenBounds: ScreenBounds,
): ScreenPoint? {
    val screenWidth = screenBounds.right - screenBounds.left
    val screenHeight = screenBounds.bottom - screenBounds.top
    if (!captureGeometryIsCompatible(bitmapWidth, bitmapHeight, screenBounds)) return null
    if (point.x !in 0 until bitmapWidth || point.y !in 0 until bitmapHeight) return null

    val mappedX = screenBounds.left + (point.x.toLong() * screenWidth / bitmapWidth).toInt()
    val mappedY = screenBounds.top + (point.y.toLong() * screenHeight / bitmapHeight).toInt()
    return ScreenPoint(
        x = mappedX.coerceIn(screenBounds.left, screenBounds.right - 1),
        y = mappedY.coerceIn(screenBounds.top, screenBounds.bottom - 1),
    )
}

internal fun mapBitmapCropToScreen(
    crop: ImageCropBounds,
    bitmapWidth: Int,
    bitmapHeight: Int,
    screenBounds: ScreenBounds,
): ScreenBounds? {
    if (!captureGeometryIsCompatible(bitmapWidth, bitmapHeight, screenBounds)) return null
    if (crop.left < 0 || crop.top < 0 || crop.right > bitmapWidth || crop.bottom > bitmapHeight ||
        crop.right <= crop.left || crop.bottom <= crop.top
    ) return null
    val screenWidth = screenBounds.right - screenBounds.left
    val screenHeight = screenBounds.bottom - screenBounds.top
    return ScreenBounds(
        left = screenBounds.left + (crop.left.toLong() * screenWidth / bitmapWidth).toInt(),
        top = screenBounds.top + (crop.top.toLong() * screenHeight / bitmapHeight).toInt(),
        right = screenBounds.left +
            ((crop.right.toLong() * screenWidth + bitmapWidth - 1) / bitmapWidth).toInt(),
        bottom = screenBounds.top +
            ((crop.bottom.toLong() * screenHeight + bitmapHeight - 1) / bitmapHeight).toInt(),
    )
}

internal fun mapScreenBoundsToBitmapCrop(
    bounds: ScreenBounds,
    bitmapWidth: Int,
    bitmapHeight: Int,
    screenBounds: ScreenBounds,
): ImageCropBounds? {
    if (!captureGeometryIsCompatible(bitmapWidth, bitmapHeight, screenBounds)) return null
    val clippedLeft = maxOf(bounds.left, screenBounds.left)
    val clippedTop = maxOf(bounds.top, screenBounds.top)
    val clippedRight = minOf(bounds.right, screenBounds.right)
    val clippedBottom = minOf(bounds.bottom, screenBounds.bottom)
    if (clippedRight <= clippedLeft || clippedBottom <= clippedTop) return null
    val screenWidth = screenBounds.right - screenBounds.left
    val screenHeight = screenBounds.bottom - screenBounds.top
    val relativeLeft = (clippedLeft - screenBounds.left).toLong()
    val relativeTop = (clippedTop - screenBounds.top).toLong()
    val relativeRight = (clippedRight - screenBounds.left).toLong()
    val relativeBottom = (clippedBottom - screenBounds.top).toLong()
    val crop = ImageCropBounds(
        left = ((relativeLeft * bitmapWidth + screenWidth - 1) / screenWidth).toInt(),
        top = ((relativeTop * bitmapHeight + screenHeight - 1) / screenHeight).toInt(),
        right = (relativeRight * bitmapWidth / screenWidth).toInt(),
        bottom = (relativeBottom * bitmapHeight / screenHeight).toInt(),
    )
    return crop.takeIf { it.right > it.left && it.bottom > it.top }
}

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
