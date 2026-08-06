package com.aiindexfinger.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenCaptureTargetingTest {
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