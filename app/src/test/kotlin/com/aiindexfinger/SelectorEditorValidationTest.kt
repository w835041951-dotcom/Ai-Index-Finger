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

        assertEquals(
            selector.copy(packageName = "com.example"),
            selector.toDraft().toSelectorOrNull(),
        )
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
            selector.copy(packageName = "com.example", matchIndex = 3),
            selector.toDraft().copy(matchIndex = 3).toSelectorOrNull(),
        )
    }

    @Test
    fun clickDialogMappingPreservesUntouchedImportedSelectorFields() {
        val selector = NodeSelector(
            packageName = " com.example ",
            viewId = " com.example:id/save ",
            text = " Save ",
            contentDescription = " Primary action ",
            className = " android.widget.Button ",
            ancestor = AncestorSelector(
                viewId = " com.example:id/footer ",
                text = " Actions ",
                contentDescription = " Footer actions ",
                className = " android.view.ViewGroup ",
            ),
        )

        assertEquals(
            selector.copy(packageName = "com.example", matchIndex = 2),
            nodeSelectorOrNull(
                packageName = selector.packageName,
                viewId = selector.viewId.orEmpty(),
                text = selector.text.orEmpty(),
                textContains = false,
                description = selector.contentDescription.orEmpty(),
                descriptionContains = false,
                className = selector.className.orEmpty(),
                matchIndex = 2,
                useAncestor = true,
                ancestorViewId = selector.ancestor?.viewId.orEmpty(),
                ancestorText = selector.ancestor?.text.orEmpty(),
                ancestorTextContains = false,
                ancestorDescription = selector.ancestor?.contentDescription.orEmpty(),
                ancestorDescriptionContains = false,
                ancestorClassName = selector.ancestor?.className.orEmpty(),
                originalSelector = selector,
            ),
        )
    }

    @Test
    fun packageNormalizationDoesNotAlterExactSelectorText() {
        val selector = NodeSelector(
            packageName = " com.example ",
            text = " Save ",
            contentDescription = " Primary action ",
        )

        val normalized = selector.toDraft().toSelectorOrNull()

        assertEquals("com.example", normalized?.packageName)
        assertEquals(" Save ", normalized?.text)
        assertEquals(" Primary action ", normalized?.contentDescription)
    }

    @Test
    fun replacementSelectorDoesNotRetainPreviousAncestor() {
        val previous = NodeSelector(
            packageName = "com.example",
            text = "Old",
            ancestor = AncestorSelector(text = "Old section"),
        ).toDraft()
        val replacement = NodeSelector(
            packageName = "com.example",
            text = "New",
            textMatchMode = TextMatchMode.Contains,
            ancestor = AncestorSelector(
                contentDescription = "New section",
                contentDescriptionMatchMode = TextMatchMode.Contains,
            ),
        )

        val updated = previous.withReplacementSelector(replacement)

        assertEquals(replacement, updated.toSelectorOrNull())
        assertEquals("New section", updated.ancestorContentDescription)
        assertEquals("", updated.ancestorText)
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
    fun draftRejectsNegativeMatchIndexesAndAllowsLargeIndexes() {
        assertNull(NodeSelectorDraft(text = "Continue", matchIndex = -1).toSelectorOrNull())
        assertEquals(
            Int.MAX_VALUE,
            NodeSelectorDraft(text = "Continue", matchIndex = Int.MAX_VALUE).toSelectorOrNull()?.matchIndex,
        )
    }
}