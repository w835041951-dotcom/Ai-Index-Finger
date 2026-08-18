package com.aiindexfinger.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenCaptureTargetingTest {
    @Test
    fun `accessibility coordinate fallback uses largest target window center`() {
        assertEquals(
            ScreenPoint(700, 500),
            largestWindowCenter(
                listOf(
                    ScreenBounds(0, 0, 200, 300),
                    ScreenBounds(400, 100, 1_000, 900),
                    ScreenBounds(10, 10, 10, 20),
                ),
            ),
        )
        assertNull(largestWindowCenter(listOf(ScreenBounds(10, 10, 10, 20))))
    }

    @Test
    fun `legacy image click keeps matched center`() {
        assertEquals(
            ScreenPoint(110, 220),
            mapTemplateClickToMatch(
                match = TemplateMatchResult.Unique(110, 220, 980, width = 20, height = 10),
                templateWidth = 20,
                templateHeight = 10,
                templateClickX = null,
                templateClickY = null,
            ),
        )
    }

    @Test
    fun `maps native template click point through matched scale`() {
        val match = TemplateMatchResult.Unique(110, 220, 980, width = 22, height = 11)

        assertEquals(
            ScreenPoint(99, 215),
            mapTemplateClickToMatch(match, 20, 10, templateClickX = 0, templateClickY = 0),
        )
        assertEquals(
            ScreenPoint(120, 225),
            mapTemplateClickToMatch(match, 20, 10, templateClickX = 19, templateClickY = 9),
        )
        assertNull(mapTemplateClickToMatch(match, 20, 10, templateClickX = 20, templateClickY = 9))
    }

    @Test
    fun `maps off center click through template and screenshot scaling`() {
        assertEquals(
            ScreenPoint(240, 450),
            mapMatchToTargetScreen(
                match = TemplateMatchResult.Unique(110, 220, 980, width = 22, height = 11),
                bitmapWidth = 540,
                bitmapHeight = 1_200,
                screenBounds = ScreenBounds(0, 0, 1_080, 2_400),
                targetBounds = listOf(ScreenBounds(0, 0, 1_080, 2_400)),
                templateWidth = 20,
                templateHeight = 10,
                templateClickX = 19,
                templateClickY = 9,
            ),
        )
    }

    @Test
    fun `keeps downscaled edge click inside target window`() {
        assertEquals(
            ScreenPoint(5, 5),
            mapMatchToTargetScreen(
                match = TemplateMatchResult.Unique(6, 10, 980, width = 12, height = 12),
                bitmapWidth = 100,
                bitmapHeight = 200,
                screenBounds = ScreenBounds(0, 0, 50, 100),
                targetBounds = listOf(ScreenBounds(0, 0, 6, 100)),
                templateWidth = 12,
                templateHeight = 12,
                templateClickX = 11,
                templateClickY = 6,
            ),
        )
    }

    @Test
    fun `maps scaled screenshot pixels to display coordinates`() {
        assertEquals(
            ScreenPoint(540, 1_200),
            mapBitmapPointToScreen(
                point = ScreenPoint(270, 600),
                bitmapWidth = 540,
                bitmapHeight = 1_200,
                screenBounds = ScreenBounds(0, 0, 1_080, 2_400),
            ),
        )
    }

    @Test
    fun `keeps screenshot pixels unchanged at display resolution`() {
        assertEquals(
            ScreenPoint(270, 600),
            mapBitmapPointToScreen(
                point = ScreenPoint(270, 600),
                bitmapWidth = 1_080,
                bitmapHeight = 2_400,
                screenBounds = ScreenBounds(0, 0, 1_080, 2_400),
            ),
        )
    }

    @Test
    fun `maps screenshot pixels into an offset display bounds`() {
        assertEquals(
            ScreenPoint(640, 1_400),
            mapBitmapPointToScreen(
                point = ScreenPoint(270, 600),
                bitmapWidth = 540,
                bitmapHeight = 1_200,
                screenBounds = ScreenBounds(100, 200, 1_180, 2_600),
            ),
        )
    }

    @Test
    fun `bitmap points remain inside their mapped crop at every small scale`() {
        for (bitmapSize in 1..20) {
            for (screenSize in 1..20) {
                val screenBounds = ScreenBounds(0, 0, screenSize, screenSize)
                for (left in 0 until bitmapSize) {
                    for (right in left + 1..bitmapSize) {
                        val mappedCrop = requireNotNull(
                            mapBitmapCropToScreen(
                                ImageCropBounds(left, 0, right, bitmapSize),
                                bitmapSize,
                                bitmapSize,
                                screenBounds,
                            ),
                        )
                        for (pointX in left until right) {
                            val mappedPoint = requireNotNull(
                                mapBitmapPointToScreen(
                                    ScreenPoint(pointX, 0),
                                    bitmapSize,
                                    bitmapSize,
                                    screenBounds,
                                ),
                            )
                            assertTrue(
                                "bitmap=$bitmapSize screen=$screenSize crop=[$left,$right) point=$pointX",
                                mappedPoint.x in mappedCrop.left until mappedCrop.right,
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `maps target screen bounds into scaled bitmap search region`() {
        assertEquals(
            ImageCropBounds(270, 0, 540, 1_200),
            mapScreenBoundsToBitmapCrop(
                bounds = ScreenBounds(540, 0, 1_080, 2_400),
                bitmapWidth = 540,
                bitmapHeight = 1_200,
                screenBounds = ScreenBounds(0, 0, 1_080, 2_400),
            ),
        )
    }

    @Test
    fun `screen to bitmap mapping rounds target edges inward`() {
        assertEquals(
            ImageCropBounds(1, 1, 2, 2),
            mapScreenBoundsToBitmapCrop(
                bounds = ScreenBounds(3, 3, 7, 7),
                bitmapWidth = 3,
                bitmapHeight = 3,
                screenBounds = ScreenBounds(0, 0, 10, 10),
            ),
        )
    }

    @Test
    fun `scaled image match is validated in display coordinates`() {
        val match = TemplateMatchResult.Unique(270, 600, 980, width = 20, height = 20)
        val targetBounds = listOf(ScreenBounds(500, 1_100, 580, 1_300))

        assertEquals(false, matchIsInsideTargetWindow(match, targetBounds))
        assertEquals(
            ScreenPoint(540, 1_200),
            mapMatchToTargetScreen(
                match = match,
                bitmapWidth = 540,
                bitmapHeight = 1_200,
                screenBounds = ScreenBounds(0, 0, 1_080, 2_400),
                targetBounds = targetBounds,
            ),
        )
    }

    @Test
    fun `rejects image match whose footprint crosses the target window`() {
        assertNull(
            mapMatchToTargetScreen(
                match = TemplateMatchResult.Unique(
                    centerX = 270,
                    centerY = 600,
                    scorePermille = 980,
                    width = 40,
                    height = 40,
                ),
                bitmapWidth = 540,
                bitmapHeight = 1_200,
                screenBounds = ScreenBounds(0, 0, 1_080, 2_400),
                targetBounds = listOf(ScreenBounds(540, 0, 1_080, 2_400)),
            ),
        )
    }

    @Test
    fun `maps scaled bitmap crop before validating split screen target`() {
        val screenBounds = ScreenBounds(0, 0, 1_080, 2_400)
        val leftWindow = listOf(ScreenBounds(0, 0, 540, 2_400))
        val rightWindow = listOf(ScreenBounds(540, 0, 1_080, 2_400))

        val mapped = mapBitmapCropToScreen(
            crop = ImageCropBounds(300, 100, 500, 300),
            bitmapWidth = 540,
            bitmapHeight = 1_200,
            screenBounds = screenBounds,
        )

        assertEquals(ScreenBounds(600, 200, 1_000, 600), mapped)
        assertEquals(true, mapped?.let { cropIsInsideTargetWindow(it, rightWindow) })
        assertEquals(false, mapped?.let { cropIsInsideTargetWindow(it, leftWindow) })
    }

    @Test
    fun `rejects bitmap crop crossing the captured target window`() {
        assertNull(
            mapBitmapCropToTargetScreen(
                crop = ImageCropBounds(250, 100, 300, 300),
                bitmapWidth = 540,
                bitmapHeight = 1_200,
                screenBounds = ScreenBounds(0, 0, 1_080, 2_400),
                targetBounds = listOf(ScreenBounds(0, 0, 540, 2_400)),
            ),
        )
    }

    @Test
    fun `bitmap crop mapping rounds exclusive edges outward`() {
        assertEquals(
            ScreenBounds(3, 3, 7, 7),
            mapBitmapCropToScreen(
                crop = ImageCropBounds(1, 1, 2, 2),
                bitmapWidth = 3,
                bitmapHeight = 3,
                screenBounds = ScreenBounds(0, 0, 10, 10),
            ),
        )
    }

    @Test
    fun `rejects capture geometry after orientation changes`() {
        val portrait = ScreenBounds(0, 0, 1_080, 2_400)

        assertEquals(true, captureGeometryIsCompatible(540, 1_200, portrait))
        assertEquals(true, captureGeometryIsCompatible(1_080, 2_400, portrait))
        assertEquals(false, captureGeometryIsCompatible(1_200, 540, portrait))
        assertNull(
            mapBitmapPointToScreen(
                point = ScreenPoint(600, 270),
                bitmapWidth = 1_200,
                bitmapHeight = 540,
                screenBounds = portrait,
            ),
        )
    }

    @Test
    fun `image match must remain inside target app window`() {
        val targetBounds = listOf(ScreenBounds(0, 0, 500, 1_000))

        assertEquals(
            true,
            matchIsInsideTargetWindow(TemplateMatchResult.Unique(250, 500, 980), targetBounds),
        )
        assertEquals(
            false,
            matchIsInsideTargetWindow(TemplateMatchResult.Unique(750, 500, 980), targetBounds),
        )
    }

    @Test
    fun `image crop must remain entirely inside one target app window`() {
        val targetBounds = listOf(
            ScreenBounds(0, 100, 500, 1_000),
            ScreenBounds(500, 100, 1_000, 1_000),
        )

        assertEquals(
            true,
            cropIsInsideTargetWindow(ImageCropBounds(10, 110, 490, 990), targetBounds),
        )
        assertEquals(
            false,
            cropIsInsideTargetWindow(ImageCropBounds(490, 110, 510, 200), targetBounds),
        )
        assertEquals(
            false,
            cropIsInsideTargetWindow(ImageCropBounds(10, 90, 100, 200), targetBounds),
        )
    }

    @Test
    fun `accepts target package from an interactive window when active root is stale`() {
        assertEquals(
            true,
            targetPackageIsVisible(
                targetPackage = "com.example.target",
                activePackage = "com.android.systemui",
                windowPackages = listOf("com.android.systemui", "com.example.target"),
            ),
        )
    }

    @Test
    fun `rejects target package absent from active root and interactive windows`() {
        assertEquals(
            false,
            targetPackageIsVisible(
                targetPackage = "com.example.target",
                activePackage = null,
                windowPackages = listOf("com.android.systemui"),
            ),
        )
    }

    @Test
    fun `maps taps through fit center letterboxing`() {
        assertEquals(
            ScreenPoint(50, 25),
            mapFitCenterTapToScreen(100f, 100f, 200, 200, 100, 50),
        )
        assertNull(mapFitCenterTapToScreen(100f, 25f, 200, 200, 100, 50))
    }

    @Test
    fun `maps a landscape screenshot inside a portrait editor`() {
        assertEquals(
            ScreenPoint(0, 0),
            mapFitCenterTapToScreen(0f, 957f, 1080, 2400, 2400, 1080),
        )
        assertEquals(
            ScreenPoint(1200, 540),
            mapFitCenterTapToScreen(540f, 1200f, 1080, 2400, 2400, 1080),
        )
        assertEquals(
            ScreenPoint(2399, 1079),
            mapFitCenterTapToScreen(1079.9f, 1442.9f, 1080, 2400, 2400, 1080),
        )
        assertNull(mapFitCenterTapToScreen(540f, 956f, 1080, 2400, 2400, 1080))
        assertNull(mapFitCenterTapToScreen(540f, 1443f, 1080, 2400, 2400, 1080))
    }

    @Test
    fun `maps a portrait screenshot inside a landscape editor`() {
        assertEquals(
            ScreenPoint(0, 0),
            mapFitCenterTapToScreen(957f, 0f, 2400, 1080, 1080, 2400),
        )
        assertEquals(
            ScreenPoint(540, 1200),
            mapFitCenterTapToScreen(1200f, 540f, 2400, 1080, 1080, 2400),
        )
        assertEquals(
            ScreenPoint(1079, 2399),
            mapFitCenterTapToScreen(1442.9f, 1079.9f, 2400, 1080, 1080, 2400),
        )
        assertNull(mapFitCenterTapToScreen(956f, 540f, 2400, 1080, 1080, 2400))
        assertNull(mapFitCenterTapToScreen(1443f, 540f, 2400, 1080, 1080, 2400))
    }

    @Test
    fun `selects clickable deepest target under repeated controls`() {
        val first = node("first", 0, 0, 100, 50, depth = 2, order = 1, clickable = true)
        val second = node("second", 0, 50, 100, 100, depth = 2, order = 2, clickable = true)
        val child = node("child", 10, 60, 90, 90, depth = 3, order = 3, clickable = false)

        assertEquals(second, selectCaptureNode(listOf(first, second, child), ScreenPoint(20, 70)))
    }

    @Test
    fun `falls back to selectable ancestor when child has no attributes`() {
        val parent = node("parent", 0, 0, 100, 100, depth = 1, order = 1, clickable = true)
        val child = CaptureNode(
            packageName = "com.example",
            viewId = null,
            text = null,
            contentDescription = null,
            className = "android.view.View",
            left = 20,
            top = 20,
            right = 80,
            bottom = 80,
            depth = 2,
            traversalOrder = 2,
            clickable = false,
        )

        assertEquals(parent, selectCaptureNode(listOf(parent, child), ScreenPoint(50, 50)))
    }

    private fun node(
        id: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        depth: Int,
        order: Int,
        clickable: Boolean,
    ) = CaptureNode(
        packageName = "com.example",
        viewId = "com.example:id/$id",
        text = id,
        contentDescription = null,
        className = "android.widget.Button",
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        depth = depth,
        traversalOrder = order,
        clickable = clickable,
    )
}