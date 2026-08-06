package com.aiindexfinger.automation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun oversizedTemplateIsRejectedWithoutRecyclingSource() {
        val source = patternedBitmap(257, 32)

        assertNull(encodeTemplatePng(source))
        assertFalse(source.isRecycled)

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
}