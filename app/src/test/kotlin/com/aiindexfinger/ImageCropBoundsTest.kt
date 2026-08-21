package com.aiindexfinger

import com.aiindexfinger.automation.ImageCropBounds
import com.aiindexfinger.automation.ScreenPoint
import com.aiindexfinger.automation.cropBoundsOrNull
import com.aiindexfinger.automation.centeredSupportedTemplateCrop
import com.aiindexfinger.automation.templateDimensionsAreSupported
import com.aiindexfinger.automation.templateCenterRelativeToCrop
import com.aiindexfinger.automation.templatePointRelativeToCrop
import com.aiindexfinger.model.ImageTemplateConstraints
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
        assertTrue(
            templateDimensionsAreSupported(
                ImageTemplateConstraints.MIN_EDGE_PX,
                ImageTemplateConstraints.MAX_EDGE_PX,
            ),
        )
        assertFalse(
            templateDimensionsAreSupported(
                ImageTemplateConstraints.MIN_EDGE_PX - 1,
                ImageTemplateConstraints.MAX_EDGE_PX,
            ),
        )
        assertFalse(
            templateDimensionsAreSupported(
                ImageTemplateConstraints.MAX_EDGE_PX,
                ImageTemplateConstraints.MAX_EDGE_PX + 1,
            ),
        )
    }

    @Test
    fun mapsScreenshotPointIntoNativeTemplateCoordinates() {
        val crop = ImageCropBounds(10, 20, 34, 38)

        assertEquals(ScreenPoint(0, 0), templatePointRelativeToCrop(crop, ScreenPoint(10, 20)))
        assertEquals(ScreenPoint(23, 17), templatePointRelativeToCrop(crop, ScreenPoint(33, 37)))
        assertNull(templatePointRelativeToCrop(crop, ScreenPoint(34, 37)))
        assertNull(templatePointRelativeToCrop(crop, ScreenPoint(33, 38)))
    }

    @Test
    fun quickCaptureUsesNativeTemplateCenter() {
        assertEquals(
            ScreenPoint(12, 9),
            templateCenterRelativeToCrop(ImageCropBounds(10, 20, 34, 38)),
        )
    }

    @Test
    fun accessibilityCropCentersAndLimitsLargeTargetAtNativeScale() {
        assertEquals(
            ImageCropBounds(488, 188, 1512, 1212),
            centeredSupportedTemplateCrop(ImageCropBounds(0, 0, 2000, 1400)),
        )
    }

    @Test
    fun accessibilityCropKeepsSupportedTargetAndRejectsTinyTarget() {
        assertEquals(
            ImageCropBounds(10, 20, 110, 220),
            centeredSupportedTemplateCrop(ImageCropBounds(10, 20, 110, 220)),
        )
        assertNull(centeredSupportedTemplateCrop(ImageCropBounds(0, 0, 11, 12)))
    }

    @Test
    fun incompleteReplacementCannotSaveOverExistingTemplate() {
        assertTrue(imageClickTemplateSelectionCanSave(hasInitialTemplate = true, captureReady = false, replacementComplete = false))
        assertFalse(imageClickTemplateSelectionCanSave(hasInitialTemplate = true, captureReady = true, replacementComplete = false))
        assertTrue(imageClickTemplateSelectionCanSave(hasInitialTemplate = true, captureReady = true, replacementComplete = true))
        assertFalse(imageClickTemplateSelectionCanSave(hasInitialTemplate = false, captureReady = false, replacementComplete = false))
    }
}