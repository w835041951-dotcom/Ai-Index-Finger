package com.aiindexfinger.automation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiindexfinger.model.ImageTemplateConstraints
import com.aiindexfinger.model.Step
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageTemplateEncodingInstrumentedTest {
    @Test
    fun validCropEncodesAtOriginalScaleWithoutRecyclingSource() {
        val source = patternedBitmap(64, 48)
        val crop = requireNotNull(cropTemplate(source, ImageCropBounds(8, 6, 40, 30)))

        val encoded = requireNotNull(encodeTemplatePng(crop))
        crop.recycle()

        assertFalse(source.isRecycled)
        assertEquals(32, encoded.width)
        assertEquals(24, encoded.height)
        val bytes = Base64.getDecoder().decode(encoded.base64)
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertNotNull(decoded)
        assertEquals(32, decoded.width)
        assertEquals(24, decoded.height)
        decoded.recycle()
        source.recycle()
    }

    @Test
    fun oversizedTemplateIsLimitedToSupportedEdgeWithoutRecyclingSource() {
        val source = patternedBitmap(1_200, 600)

        val encoded = requireNotNull(encodeTemplatePng(source))

        assertEquals(1_024, encoded.width)
        assertEquals(512, encoded.height)
        assertEquals(1_200, encoded.sourceWidth)
        assertEquals(600, encoded.sourceHeight)
        assertEquals(true, encoded.scaledDown)
        assertFalse(source.isRecycled)

        source.recycle()
    }

    @Test
    fun highDetailTemplateFallsBackToGrayscaleAndBudgetedSize() {
        val source = noisyBitmap(512, 512)

        val encoded = requireNotNull(encodeTemplatePng(source))

        assertEquals(true, encoded.convertedToGrayscale)
        assertTrue(encoded.pngByteCount <= ImageTemplateConstraints.MAX_PNG_BYTES)
        assertTrue(encoded.width <= 512)
        assertTrue(encoded.height <= 512)
        source.recycle()
    }

    @Test
    fun rescalingRemapsClickPointWithEndpointPreservation() {
        val source = patternedBitmap(2_048, 1_024)

        val encoded = requireNotNull(encodeTemplatePng(source, ScreenPoint(2_047, 1_023)))

        assertEquals(1_024, encoded.width)
        assertEquals(512, encoded.height)
        assertEquals(ScreenPoint(1_023, 511), encoded.templateClickPoint)
        source.recycle()
    }

    @Test
    fun closingScreenshotPickerRecyclesReadyCapture() {
        val bitmap = patternedBitmap(32, 24)
        AutomationAccessibilityService.screenCaptureState.value = ScreenCaptureState.Ready(
            bitmap = bitmap,
            nodes = emptyList(),
            screenBounds = ScreenBounds(0, 0, 32, 24),
            targetPackage = "com.example",
            targetBounds = listOf(ScreenBounds(0, 0, 32, 24)),
        )

        AutomationAccessibilityService.cancelPendingScreenCapture()

        assertEquals(ScreenCaptureState.Idle, AutomationAccessibilityService.screenCaptureState.value)
        assertEquals(true, bitmap.isRecycled)
    }

    @Test
    fun encodedTemplateDecodesWithDeclaredDimensions() {
        val source = patternedBitmap(32, 24)
        val encoded = requireNotNull(encodeTemplatePng(source))
        val step = Step.ImageClick(
            id = "image",
            packageName = "com.example",
            templatePngBase64 = encoded.base64,
            templateWidth = encoded.width,
            templateHeight = encoded.height,
        )

        val decoded = requireNotNull(decodeImageTemplate(step))

        assertEquals(32, decoded.width)
        assertEquals(24, decoded.height)
        decoded.recycle()
        source.recycle()
    }

    @Test
    fun invalidOrDimensionMismatchedTemplateDoesNotDecode() {
        val source = patternedBitmap(32, 24)
        val encoded = requireNotNull(encodeTemplatePng(source))

        assertNull(
            decodeImageTemplate(
                Step.ImageClick("invalid", "com.example", "not-png", 32, 24),
            ),
        )
        assertNull(
            decodeImageTemplate(
                Step.ImageClick("mismatch", "com.example", encoded.base64, 31, 24),
            ),
        )
        source.recycle()
    }

    private fun patternedBitmap(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            val pixels = IntArray(width * height) { index ->
                val x = index % width
                val y = index / width
                0xff000000.toInt() or (x * 37 and 0xff shl 16) or
                    (y * 53 and 0xff shl 8) or (x * 17 + y * 29 and 0xff)
            }
            setPixels(pixels, 0, width, 0, 0, width, height)
        }

    private fun noisyBitmap(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            var value = 0x12345678
            val pixels = IntArray(width * height) {
                value = value * 1_103_515_245 + 12_345
                0xff000000.toInt() or (value and 0x00ffffff)
            }
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
}