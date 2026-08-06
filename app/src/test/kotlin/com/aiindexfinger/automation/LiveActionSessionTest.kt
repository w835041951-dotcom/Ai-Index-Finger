package com.aiindexfinger.automation

import com.aiindexfinger.model.StepListPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveActionSessionTest {
    private val listPath = StepListPath()

    @Test
    fun confirmationPublishesSelectedCoordinateOnce() {
        val session = LiveActionSession()

        session.start("workflow-a", listPath)
        assertTrue(session.select(LiveActionCandidate.Coordinate(120, 340)))

        assertEquals(
            ConfirmedLiveAction(
                LiveActionDestination("workflow-a", listPath),
                LiveActionCandidate.Coordinate(120, 340),
            ),
            session.confirm(),
        )
        assertFalse(session.isActive)
        assertNull(session.confirm())
    }

    @Test
    fun selectionRequiresAnActiveSessionAndExplicitConfirmation() {
        val session = LiveActionSession()

        assertFalse(session.select(LiveActionCandidate.Coordinate(1, 2)))
        assertNull(session.confirm())

        session.start("workflow-a", listPath)
        assertNull(session.confirm())
        assertTrue(session.isActive)
    }

    @Test
    fun cancellationAndRestartDiscardPreviousSelection() {
        val session = LiveActionSession()
        session.start("workflow-a", listPath)
        session.select(LiveActionCandidate.Coordinate(10, 20))

        session.cancel()
        assertNull(session.confirm())

        session.start("workflow-b", listPath)
        assertNull(session.confirm())
    }

    @Test
    fun imageSelectionIsAlsoHeldUntilConfirmation() {
        val session = LiveActionSession()
        val candidate = LiveActionCandidate.Image(
            packageName = "com.example.target",
            templatePngBase64 = "template",
            templateWidth = 24,
            templateHeight = 18,
        )

        session.start("workflow-a", listPath)
        assertTrue(session.select(candidate))

        assertEquals(candidate, session.confirm()?.candidate)
    }
}