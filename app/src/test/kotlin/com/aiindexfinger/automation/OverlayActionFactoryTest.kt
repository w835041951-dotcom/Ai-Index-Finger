package com.aiindexfinger.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.aiindexfinger.model.NodeSelector
import com.aiindexfinger.model.Step
import com.aiindexfinger.model.ScrollDirection
import com.aiindexfinger.model.SystemAction

class OverlayActionFactoryTest {
    @Test
    fun passwordNodeTextAndDescriptionAreRedacted() {
        assertEquals(
            null to null,
            sanitizedRecordedText(true, "secret", "Password: secret"),
        )
        assertEquals(
            "visible" to "Description",
            sanitizedRecordedText(false, "visible", "Description"),
        )
    }

    @Test
    fun nodeCreatesTargetWithPreferredSelectorCenterAndFullSnapshot() {
        val target = createRecordedClickTarget(
            node(
                viewId = "com.example:id/save",
                text = "Save",
                contentDescription = "Save changes",
                bounds = "10 20 110 220",
                longClickable = true,
                scrollable = true,
            ),
        )!!

        assertEquals("com.example:id/save", target.selector?.viewId)
        assertEquals(60, target.x)
        assertEquals(120, target.y)
        assertEquals("Save", target.control.text)
        assertEquals("Save changes", target.control.contentDescription)
        assertEquals(true, target.control.longClickable)
        assertEquals(true, target.control.scrollable)
    }

    @Test
    fun nodeWithoutStableAttributesCreatesCoordinateOnlyTarget() {
        val target = createRecordedClickTarget(node(viewId = null, text = null, className = null))!!

        assertNull(target.selector)
        assertEquals(50, target.x)
        assertEquals(50, target.y)
    }

    @Test
    fun disabledOrInvalidBoundsNodeCannotCreateTarget() {
        assertNull(createRecordedClickTarget(node(enabled = false)))
        assertNull(createRecordedClickTarget(node(bounds = "0 0 0 100")))
        assertNull(createRecordedClickTarget(node(bounds = "invalid")))
        assertNull(
            createRecordedClickTarget(
                node(bounds = "0 0 0 0"),
                recoveredSelector = com.aiindexfinger.model.NodeSelector(
                    "com.example",
                    viewId = "com.example:id/action",
                ),
                allowRecommendedSelector = false,
            ),
        )
    }

    @Test
    fun recordingSessionPreservesOrderAndRepeatedTargetsUntilFinish() {
        val first = createRecordedClickTarget(node(text = "First"))!!
        val repeated = createRecordedClickTarget(node(text = "Repeated"))!!
        val session = RecordedClickSession(capacity = 3)

        session.start()
        assertEquals(true, session.record(first))
        assertEquals(true, session.record(repeated))
        assertEquals(true, session.record(repeated))
        assertEquals(false, session.record(first))

        assertEquals(
            listOf(first, repeated, repeated).map(RecordedAction::Click),
            session.finish().actions,
        )
        assertEquals(0, session.count)
        assertEquals(false, session.record(first))
    }

    @Test
    fun cancellingRecordingDiscardsEveryTarget() {
        val target = createRecordedClickTarget(node())!!
        val session = RecordedClickSession(capacity = 2)

        session.start()
        session.record(target)
        session.cancel()

        assertEquals(0, session.count)
        assertEquals(emptyList<RecordedAction>(), session.finish().actions)
        assertEquals(emptyList<RecordingIssue>(), session.finish().issues)
        assertEquals(false, session.isActive())
    }

    @Test
    fun recordingSessionDeliversIssuesAndClearsThemAfterFinish() {
        val session = RecordedClickSession(capacity = 2)
        val issue = RecordingIssue(100, "com.example", RecordingIssueReason.SourceUnavailable)

        session.start()
        assertEquals(true, session.recordIssue(issue))

        assertEquals(listOf(issue), session.finish().issues)
        assertEquals(0, session.issueCount)
        assertEquals(false, session.isActive())
    }

    @Test
    fun textChangesReplaceOneOrderedActionUntilBurstCloses() {
        val session = RecordedClickSession(capacity = 4)
        val selector = NodeSelector("com.example", viewId = "com.example:id/input")
        session.start()

        session.recordOrReplaceText("field", Step.InputText("input", selector, "h"))
        session.recordOrReplaceText("field", Step.InputText("input", selector, "hello"))
        session.closeTextBurst("field")
        session.recordOrReplaceText("field", Step.InputText("input-2", selector, "again"))

        val actions = session.finish().actions
        assertEquals(2, actions.size)
        assertEquals("hello", ((actions[0] as RecordedAction.ExistingStep).step as Step.InputText).text)
        assertEquals("again", ((actions[1] as RecordedAction.ExistingStep).step as Step.InputText).text)
    }

    @Test
    fun alternatingFieldsCreateNewOrderedBurstsAndEmptyTextClears() {
        val session = RecordedClickSession(capacity = 5)
        val first = NodeSelector("com.example", viewId = "com.example:id/first")
        val second = NodeSelector("com.example", viewId = "com.example:id/second")
        session.start()

        session.recordOrReplaceText("first", Step.InputText("first-1", first, "one"))
        session.recordOrReplaceText("second", Step.InputText("second", second, "two"))
        session.recordOrReplaceText("first", Step.InputText("first-2", first, ""))

        val actions = session.finish().actions.map { (it as RecordedAction.ExistingStep).step as Step.InputText }
        assertEquals(listOf("one", "two", ""), actions.map(Step.InputText::text))
    }

    @Test
    fun lateSensitiveClassificationDiscardsOnlyActiveTextBurst() {
        val session = RecordedClickSession(capacity = 4)
        val selector = NodeSelector("com.example", viewId = "com.example:id/input")
        session.start()
        session.recordStep(Step.Delay("before", 10))
        session.recordOrReplaceText("input", Step.InputText("secret", selector, "transitional"))

        session.discardActiveTextBurst()
        session.recordStep(Step.Delay("after", 10))

        val actions = session.finish().actions.map { (it as RecordedAction.ExistingStep).step }
        assertEquals(listOf("before", "after"), actions.map(Step::id))
    }

    @Test
    fun mixedRecordedActionsPreserveTheirExactOrder() {
        val session = RecordedClickSession(capacity = 8)
        val selector = NodeSelector("com.example", viewId = "com.example:id/action")
        val click = createRecordedClickTarget(node())!!
        session.start()

        session.record(click)
        session.recordStep(Step.LongClick("long", selector))
        session.recordOrReplaceText("input", Step.InputText("input", selector, "hello"))
        session.closeAllTextBursts()
        session.recordStep(Step.Scroll("scroll", selector, ScrollDirection.Forward))
        session.recordStep(Step.GlobalAction("back", SystemAction.Back))
        session.recordStep(Step.Swipe("swipe", 0, 0, 100, 100, 300))
        session.recordStep(Step.LaunchApp("launch", "com.example.other"))

        val actions = session.finish().actions
        assertEquals(7, actions.size)
        assertEquals(click, (actions[0] as RecordedAction.Click).target)
        assertEquals(Step.LongClick::class, (actions[1] as RecordedAction.ExistingStep).step::class)
        assertEquals(Step.InputText::class, (actions[2] as RecordedAction.ExistingStep).step::class)
        assertEquals(Step.Scroll::class, (actions[3] as RecordedAction.ExistingStep).step::class)
        assertEquals(Step.GlobalAction::class, (actions[4] as RecordedAction.ExistingStep).step::class)
        assertEquals(Step.Swipe::class, (actions[5] as RecordedAction.ExistingStep).step::class)
        assertEquals(Step.LaunchApp::class, (actions[6] as RecordedAction.ExistingStep).step::class)
    }

    private fun node(
        viewId: String? = "com.example:id/action",
        text: String? = "Action",
        contentDescription: String? = null,
        className: String? = "android.widget.Button",
        bounds: String = "0 0 100 100",
        enabled: Boolean = true,
        clickable: Boolean = true,
        longClickable: Boolean = false,
        scrollable: Boolean = false,
    ) = ObservedNode(
        packageName = "com.example",
        viewId = viewId,
        text = text,
        contentDescription = contentDescription,
        className = className,
        bounds = bounds,
        clickable = clickable,
        enabled = enabled,
        longClickable = longClickable,
        scrollable = scrollable,
    )
}