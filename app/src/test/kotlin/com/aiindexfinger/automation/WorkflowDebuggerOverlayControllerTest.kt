package com.aiindexfinger.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkflowDebuggerOverlayControllerTest {
    @Test
    fun `overlay snaps to nearest horizontal edge`() {
        assertEquals(DebuggerOverlayEdge.Start, debuggerOverlayEdgeFor(rawX = 99, screenWidth = 200))
        assertEquals(DebuggerOverlayEdge.End, debuggerOverlayEdgeFor(rawX = 100, screenWidth = 200))
        assertEquals(DebuggerOverlayEdge.End, debuggerOverlayEdgeFor(rawX = 199, screenWidth = 200))
    }

    @Test
    fun `overlay vertical position stays inside screen margins`() {
        assertEquals(
            8,
            clampDebuggerOverlayY(candidate = -20, screenHeight = 800, panelHeight = 200, margin = 8),
        )
        assertEquals(
            300,
            clampDebuggerOverlayY(candidate = 300, screenHeight = 800, panelHeight = 200, margin = 8),
        )
        assertEquals(
            592,
            clampDebuggerOverlayY(candidate = 900, screenHeight = 800, panelHeight = 200, margin = 8),
        )
    }

    @Test
    fun `oversized overlay remains anchored to top margin`() {
        assertEquals(
            12,
            clampDebuggerOverlayY(candidate = 300, screenHeight = 200, panelHeight = 300, margin = 12),
        )
    }
}