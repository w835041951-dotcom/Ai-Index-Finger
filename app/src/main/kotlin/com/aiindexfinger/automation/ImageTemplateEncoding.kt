package com.aiindexfinger.automation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.aiindexfinger.model.Step
import java.io.ByteArrayOutputStream
import java.util.Base64

internal data class ImageCropBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

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
    if (width < Step.ImageClick.MIN_TEMPLATE_SIZE || height < Step.ImageClick.MIN_TEMPLATE_SIZE) return null
    return Bitmap.createBitmap(bitmap, left, top, width, height)
}

internal fun encodeTemplatePng(source: Bitmap): EncodedTemplate? {
    if (!templateDimensionsAreSupported(source.width, source.height)) return null
    val output = ByteArrayOutputStream()
    if (!source.compress(Bitmap.CompressFormat.PNG, 100, output)) return null
    val bytes = output.toByteArray()
    return if (bytes.size > IMAGE_TEMPLATE_MAX_PNG_BYTES) null else EncodedTemplate(
        Base64.getEncoder().encodeToString(bytes),
        source.width,
        source.height,
    )
}

internal fun templateDimensionsAreSupported(width: Int, height: Int): Boolean =
    width in Step.ImageClick.MIN_TEMPLATE_SIZE..Step.ImageClick.MAX_TEMPLATE_SIZE &&
        height in Step.ImageClick.MIN_TEMPLATE_SIZE..Step.ImageClick.MAX_TEMPLATE_SIZE

internal fun decodeImageTemplate(step: Step.ImageClick): Bitmap? = runCatching {
    val bytes = Base64.getDecoder().decode(step.templatePngBase64)
    if (bytes.size > IMAGE_TEMPLATE_MAX_PNG_BYTES) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth != step.templateWidth || bounds.outHeight != step.templateHeight) return null
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?.takeIf { it.width == step.templateWidth && it.height == step.templateHeight }
}.getOrNull()

internal data class EncodedTemplate(val base64: String, val width: Int, val height: Int)

private const val IMAGE_TEMPLATE_MAX_PNG_BYTES = 96 * 1024