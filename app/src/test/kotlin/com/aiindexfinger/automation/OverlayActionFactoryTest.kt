package com.aiindexfinger.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayActionFactoryTest {
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

        assertEquals(listOf(first, repeated, repeated), session.finish())
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
        assertEquals(emptyList<RecordedClickTarget>(), session.finish())
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