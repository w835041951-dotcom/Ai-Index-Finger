package com.aiindexfinger.automation

import com.aiindexfinger.model.Step
import com.aiindexfinger.model.StepBranch
import com.aiindexfinger.model.StepListPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveActionHandoffTest {
    @Test
    fun matchingWorkflowAppendsExactlyOnceAndConsumes() {
        val action = action("workflow-a", StepListPath(), LiveActionCandidate.Coordinate(12, 34))

        val result = applyLiveActionHandoff(emptyList(), "workflow-a", action) { "new-step" }

        assertEquals(listOf(Step.Tap("new-step", 12, 34)), result.steps)
        assertTrue(result.consume)
        assertTrue(result.appended)
    }

    @Test
    fun anotherWorkflowNeitherAppendsNorConsumes() {
        val original = listOf<Step>(Step.Tap("existing", 1, 2))
        val action = action("workflow-a", StepListPath(), LiveActionCandidate.Coordinate(12, 34))

        val result = applyLiveActionHandoff(original, "workflow-b", action) { "unused" }

        assertEquals(original, result.steps)
        assertFalse(result.consume)
        assertFalse(result.appended)
    }

    @Test
    fun matchingWorkflowAppendsToCapturedNestedPath() {
        val repeat = Step.Repeat("repeat", 2, listOf(Step.Tap("existing", 1, 2)))
        val path = StepListPath().child("repeat", StepBranch.RepeatBody)
        val action = action("workflow-a", path, LiveActionCandidate.Coordinate(12, 34))

        val result = applyLiveActionHandoff(listOf(repeat), "workflow-a", action) { "nested" }

        assertEquals(
            listOf(
                repeat.copy(steps = repeat.steps + Step.Tap("nested", 12, 34)),
            ),
            result.steps,
        )
        assertTrue(result.consume)
        assertTrue(result.appended)
    }

    @Test
    fun stalePathIsConsumedWithoutAppending() {
        val original = listOf<Step>(Step.Tap("existing", 1, 2))
        val stalePath = StepListPath().child("missing", StepBranch.RepeatBody)
        val action = action("workflow-a", stalePath, LiveActionCandidate.Coordinate(12, 34))

        val result = applyLiveActionHandoff(original, "workflow-a", action) { "unused" }

        assertEquals(original, result.steps)
        assertTrue(result.consume)
        assertFalse(result.appended)
    }

    private fun action(
        workflowId: String,
        listPath: StepListPath,
        candidate: LiveActionCandidate,
    ) = PendingOverlayAction.LiveAction(workflowId, listPath, candidate)
}