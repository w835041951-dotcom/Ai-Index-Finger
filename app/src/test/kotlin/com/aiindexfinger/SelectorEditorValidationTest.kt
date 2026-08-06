package com.aiindexfinger

import com.aiindexfinger.model.AncestorSelector
import com.aiindexfinger.model.NodeSelector
import com.aiindexfinger.model.TextMatchMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectorEditorValidationTest {
    @Test
    fun selectorCanBeSavedWithoutAPackageWhenItHasAnAttribute() {
        assertTrue(selectorHasAttribute("", "Continue", "", ""))
    }

    @Test
    fun selectorStillRequiresANodeAttribute() {
        assertFalse(selectorHasAttribute("", "", "", ""))
    }

    @Test
    fun fullSelectorRoundTripsThroughDraftWithoutLosingFields() {
        val selector = NodeSelector(
            packageName = " com.example ",
            viewId = "com.example:id/title",
            text = " Order ",
            textMatchMode = TextMatchMode.Contains,
            contentDescription = "Order status",
            contentDescriptionMatchMode = TextMatchMode.Contains,
            className = "android.widget.TextView",
            matchIndex = 7,
            ancestor = AncestorSelector(
                viewId = "com.example:id/card",
                text = " Account ",
                textMatchMode = TextMatchMode.Contains,
                contentDescription = "Primary account",
                contentDescriptionMatchMode = TextMatchMode.Contains,
                className = "android.view.ViewGroup",
            ),
        )

        assertEquals(selector, selector.toDraft().toSelectorOrNull())
    }

    @Test
    fun editingOneFieldPreservesUntouchedImportedWhitespace() {
        val selector = NodeSelector(
            packageName = " com.example ",
            text = " Order ",
            contentDescription = " Status ",
            ancestor = AncestorSelector(text = " Account "),
        )

        assertEquals(
            selector.copy(matchIndex = 3),
            selector.toDraft().copy(matchIndex = 3).toSelectorOrNull(),
        )
    }

    @Test
    fun draftAllowsBlankPackageButRequiresATargetAttribute() {
        assertEquals(
            NodeSelector(packageName = "", text = "Continue"),
            NodeSelectorDraft(text = " Continue ").toSelectorOrNull(),
        )
        assertNull(NodeSelectorDraft(packageName = "com.example").toSelectorOrNull())
    }

    @Test
    fun enabledAncestorRequiresAnAttributeAndDisabledAncestorIsCleared() {
        assertNull(
            NodeSelectorDraft(text = "Continue", useAncestor = true).toSelectorOrNull(),
        )
        assertEquals(
            null,
            NodeSelectorDraft(
                text = "Continue",
                useAncestor = false,
                ancestorText = "Stale ancestor",
            ).toSelectorOrNull()?.ancestor,
        )
    }

    @Test
    fun draftRejectsMatchIndexesOutsideTheModelRange() {
        assertNull(NodeSelectorDraft(text = "Continue", matchIndex = -1).toSelectorOrNull())
        assertNull(
            NodeSelectorDraft(
                text = "Continue",
                matchIndex = NodeSelector.MAX_MATCH_COUNT,
            ).toSelectorOrNull(),
        )
    }
}