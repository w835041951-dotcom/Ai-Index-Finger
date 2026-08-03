package com.aiindexfinger.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayActionFactoryTest {
    @Test
    fun clickableNodeCreatesClickUsingPreferredSelector() {
        val action = createOverlayClickAction(node(viewId = "com.example:id/save"))

        assertEquals("com.example:id/save", (action as PendingOverlayAction.Click).selector.viewId)
        assertEquals("com.example", action.selector.packageName)
    }

    @Test
    fun nodeWithoutStableAttributesCannotCreateAction() {
        assertNull(createOverlayClickAction(node(viewId = null, text = null)))
    }

    @Test
    fun disabledOrNonClickableNodeCannotCreateAction() {
        assertNull(createOverlayClickAction(node(enabled = false)))
        assertNull(createOverlayClickAction(node(clickable = false)))
    }

    private fun node(
        viewId: String? = "com.example:id/action",
        text: String? = "Action",
        enabled: Boolean = true,
        clickable: Boolean = true,
    ) = ObservedNode(
        packageName = "com.example",
        viewId = viewId,
        text = text,
        contentDescription = null,
        className = "android.widget.Button",
        bounds = "0 0 100 100",
        clickable = clickable,
        enabled = enabled,
    )
}