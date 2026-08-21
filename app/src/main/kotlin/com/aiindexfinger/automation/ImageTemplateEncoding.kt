package com.aiindexfinger.automation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.aiindexfinger.model.ImageTemplateConstraints
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.math.roundToInt

internal data class ImageCropBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

internal fun templatePointRelativeToCrop(crop: ImageCropBounds, point: ScreenPoint): ScreenPoint? =
    point.takeIf { it.x in crop.left until crop.right && it.y in crop.top until crop.bottom }
        ?.let { ScreenPoint(it.x - crop.left, it.y - crop.top) }

internal fun templateCenterRelativeToCrop(crop: ImageCropBounds): ScreenPoint = ScreenPoint(
    x = (crop.right - crop.left) / 2,
    y = (crop.bottom - crop.top) / 2,
)

internal fun centeredSupportedTemplateCrop(bounds: ImageCropBounds): ImageCropBounds? {
    val availableWidth = bounds.right - bounds.left
    val availableHeight = bounds.bottom - bounds.top
    if (availableWidth < ImageTemplateConstraints.MIN_EDGE_PX ||
        availableHeight < ImageTemplateConstraints.MIN_EDGE_PX
    ) {
        return null
    }
    val width = minOf(availableWidth, ImageTemplateConstraints.MAX_EDGE_PX)
    val height = minOf(availableHeight, ImageTemplateConstraints.MAX_EDGE_PX)
    val left = bounds.left + (availableWidth - width) / 2
    val top = bounds.top + (availableHeight - height) / 2
    return ImageCropBounds(left, top, left + width, top + height)
}

internal fun cropBoundsOrNull(left: String, top: String, right: String, bottom: String): ImageCropBounds? {
    val bounds = ImageCropBounds(
        left.toIntOrNull() ?: return null,
        top.toIntOrNull() ?: return null,
        right.toIntOrNull() ?: return null,
        bottom.toIntOrNull() ?: return null,
    )
    return bounds.takeIf { it.right > it.left && it.bottom > it.top }
}

internal fun cropTemplate(bitmap: Bitmap, bounds: ImageCropBounds): Bitmap? {
    val left = bounds.left.coerceIn(0, bitmap.width - 1)
    val top = bounds.top.coerceIn(0, bitmap.height - 1)
    val right = bounds.right.coerceIn(left + 1, bitmap.width)
    val bottom = bounds.bottom.coerceIn(top + 1, bitmap.height)
    val width = right - left
    val height = bottom - top
    if (width < ImageTemplateConstraints.MIN_EDGE_PX || height < ImageTemplateConstraints.MIN_EDGE_PX) {
        return null
    }
    return Bitmap.createBitmap(bitmap, left, top, width, height)
}

internal fun encodeTemplatePng(
    source: Bitmap,
    templateClickPoint: ScreenPoint? = null,
): EncodedTemplate? {
    if (source.isRecycled || source.width < ImageTemplateConstraints.MIN_EDGE_PX ||
        source.height < ImageTemplateConstraints.MIN_EDGE_PX ||
        templateClickPoint?.let { point -> point.x !in 0 until source.width || point.y !in 0 until source.height } == true
    ) {
        return null
    }
    var current = source
    var grayscale = false
    var resized = false
    try {
        val boundedSize = dimensionsForLongestEdge(source.width, source.height)
        if (boundedSize.first < ImageTemplateConstraints.MIN_EDGE_PX ||
            boundedSize.second < ImageTemplateConstraints.MIN_EDGE_PX
        ) {
            return null
        }
        if (boundedSize.first != source.width || boundedSize.second != source.height) {
            current = Bitmap.createScaledBitmap(source, boundedSize.first, boundedSize.second, true)
            resized = true
        }
        while (true) {
            val bytes = current.toPngBytes() ?: return null
            if (bytes.size <= ImageTemplateConstraints.MAX_PNG_BYTES) {
                return EncodedTemplate(
                    base64 = Base64.getEncoder().encodeToString(bytes),
                    width = current.width,
                    height = current.height,
                    sourceWidth = source.width,
                    sourceHeight = source.height,
                    pngByteCount = bytes.size,
                    convertedToGrayscale = grayscale,
                    scaledDown = resized,
                    templateClickPoint = templateClickPoint?.let { point ->
                        remapTemplatePoint(point, source.width, source.height, current.width, current.height)
                    },
                )
            }
            if (!grayscale) {
                val grayscaleCopy = grayscaleBitmap(current)
                current.takeIf { it !== source }?.recycle()
                current = grayscaleCopy
                grayscale = true
                continue
            }
            val nextWidth = (current.width * DOWNSCALE_FACTOR).roundToInt()
            val nextHeight = (current.height * DOWNSCALE_FACTOR).roundToInt()
            if (nextWidth < ImageTemplateConstraints.MIN_EDGE_PX ||
                nextHeight < ImageTemplateConstraints.MIN_EDGE_PX
            ) {
                return null
            }
            val next = Bitmap.createScaledBitmap(current, nextWidth, nextHeight, true)
            current.takeIf { it !== source }?.recycle()
            current = next
            resized = true
        }
    } finally {
        current.takeIf { it !== source && !it.isRecycled }?.recycle()
    }
}

internal fun templateDimensionsAreSupported(width: Int, height: Int): Boolean =
    width in ImageTemplateConstraints.MIN_EDGE_PX..ImageTemplateConstraints.MAX_EDGE_PX &&
        height in ImageTemplateConstraints.MIN_EDGE_PX..ImageTemplateConstraints.MAX_EDGE_PX

internal fun decodeImageTemplate(step: com.aiindexfinger.model.Step.ImageClick): Bitmap? = runCatching {
    val bytes = Base64.getDecoder().decode(step.templatePngBase64)
    if (bytes.size > ImageTemplateConstraints.MAX_PNG_BYTES) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth != step.templateWidth || bounds.outHeight != step.templateHeight) return null
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?.takeIf { it.width == step.templateWidth && it.height == step.templateHeight }
}.getOrNull()

internal fun imageTemplateIsValid(step: com.aiindexfinger.model.Step.ImageClick): Boolean =
    decodeImageTemplate(step)?.let { bitmap ->
        bitmap.recycle()
        true
    } ?: false

internal data class EncodedTemplate(
    val base64: String,
    val width: Int,
    val height: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val pngByteCount: Int,
    val convertedToGrayscale: Boolean,
    val scaledDown: Boolean,
    val templateClickPoint: ScreenPoint?,
)

private fun dimensionsForLongestEdge(width: Int, height: Int): Pair<Int, Int> {
    val longestEdge = maxOf(width, height)
    if (longestEdge <= ImageTemplateConstraints.MAX_EDGE_PX) return width to height
    val scale = ImageTemplateConstraints.MAX_EDGE_PX.toFloat() / longestEdge
    return (width * scale).roundToInt() to (height * scale).roundToInt()
}

private fun Bitmap.toPngBytes(): ByteArray? = ByteArrayOutputStream().use { output ->
    if (!compress(Bitmap.CompressFormat.PNG, 100, output)) null else output.toByteArray()
}

private fun grayscaleBitmap(source: Bitmap): Bitmap {
    val pixels = IntArray(source.width * source.height)
    source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
    pixels.indices.forEach { index ->
        val color = pixels[index]
        val red = color shr 16 and 0xff
        val green = color shr 8 and 0xff
        val blue = color and 0xff
        val luma = (red * 299 + green * 587 + blue * 114) / 1_000
        pixels[index] = (color and -0x1000000) or (luma shl 16) or (luma shl 8) or luma
    }
    return Bitmap.createBitmap(pixels, source.width, source.height, Bitmap.Config.ARGB_8888)
}

internal fun remapTemplatePoint(
    point: ScreenPoint,
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int,
    targetHeight: Int,
): ScreenPoint = ScreenPoint(
    x = remapTemplateCoordinate(point.x, sourceWidth, targetWidth),
    y = remapTemplateCoordinate(point.y, sourceHeight, targetHeight),
)

private fun remapTemplateCoordinate(coordinate: Int, sourceSize: Int, targetSize: Int): Int {
    require(coordinate in 0 until sourceSize) { "Template coordinate is outside the source" }
    return if (sourceSize == 1) 0 else {
        (coordinate * (targetSize - 1).toFloat() / (sourceSize - 1)).roundToInt()
    }
}

private const val DOWNSCALE_FACTOR = 0.85f