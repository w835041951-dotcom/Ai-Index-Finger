package com.aiindexfinger.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenCaptureTargetingTest {
    @Test
    fun `maps taps through fit center letterboxing`() {
        assertEquals(
            ScreenPoint(50, 25),
            mapFitCenterTapToScreen(100f, 100f, 200, 200, 100, 50),
        )
        assertNull(mapFitCenterTapToScreen(100f, 25f, 200, 200, 100, 50))
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