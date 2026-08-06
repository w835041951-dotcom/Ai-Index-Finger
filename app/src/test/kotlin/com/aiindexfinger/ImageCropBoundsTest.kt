package com.aiindexfinger

import com.aiindexfinger.automation.ImageCropBounds
import com.aiindexfinger.automation.cropBoundsOrNull
import com.aiindexfinger.automation.templateDimensionsAreSupported
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageCropBoundsTest {
    @Test
    fun acceptsEditableCropBounds() {
        assertEquals(ImageCropBounds(10, 20, 110, 220), cropBoundsOrNull("10", "20", "110", "220"))
    }

    @Test
    fun rejectsIncompleteOrInvertedCropBounds() {
        assertNull(cropBoundsOrNull("", "20", "110", "220"))
        assertNull(cropBoundsOrNull("110", "20", "10", "220"))
        assertNull(cropBoundsOrNull("10", "220", "110", "20"))
    }

    @Test
    fun templateDimensionsMustRemainAtRuntimeMatchScale() {
        assertTrue(templateDimensionsAreSupported(12, 256))
        assertFalse(templateDimensionsAreSupported(11, 256))
        assertFalse(templateDimensionsAreSupported(256, 257))
    }
}