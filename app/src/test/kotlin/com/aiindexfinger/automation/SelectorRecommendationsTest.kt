package com.aiindexfinger.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelectorRecommendationsTest {
    @Test
    fun resourceIdCandidateIsPreferredAndMinimal() {
        val node = ObservedNode(
            packageName = "com.example",
            viewId = "com.example:id/submit",
            text = "Submit",
            contentDescription = "Send form",
            className = "android.widget.Button",
            bounds = "0 0 100 100",
            clickable = true,
            enabled = true,
        )

        val first = SelectorRecommendations.candidates(node).first()

        assertEquals("com.example:id/submit", first.viewId)
        assertNull(first.text)
        assertNull(first.contentDescription)
        assertNull(first.className)
    }
}