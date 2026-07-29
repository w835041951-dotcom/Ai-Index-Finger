package com.aiindexfinger.data

import com.aiindexfinger.model.Condition
import com.aiindexfinger.model.Step
import com.aiindexfinger.model.StepBranch
import com.aiindexfinger.model.StepListPath
import com.aiindexfinger.model.StepPath
import com.aiindexfinger.model.Value
import com.aiindexfinger.model.Workflow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunHistoryNavigationTest {
    @Test
    fun `resolves top level and nested failed steps`() {
        val workflow = workflowWithNestedStep()

        assertEquals(
            StepPath(StepListPath(), 0),
            resolveRunHistoryDestination(record("top"), listOf(workflow))?.stepPath,
        )
        assertEquals(
            StepPath(
                StepListPath()
                    .child("repeat", StepBranch.RepeatBody)
                    .child("if", StepBranch.IfFalse),
                0,
            ),
            resolveRunHistoryDestination(record("nested"), listOf(workflow))?.stepPath,
        )
    }

    @Test
    fun `opens workflow root for missing ambiguous or absent step IDs`() {
        val workflow = Workflow(
            id = "workflow",
            name = "Workflow",
            steps = listOf(Step.Delay("same", 1), Step.Delay("same", 1)),
        )

        assertNull(resolveRunHistoryDestination(record("missing"), listOf(workflow))?.stepPath)
        assertNull(resolveRunHistoryDestination(record("same"), listOf(workflow))?.stepPath)
        assertNull(resolveRunHistoryDestination(record(null), listOf(workflow))?.stepPath)
    }

    @Test
    fun `returns no destination when workflow was deleted`() {
        assertNull(resolveRunHistoryDestination(record("top"), emptyList()))
    }

    private fun workflowWithNestedStep() = Workflow(
        id = "workflow",
        name = "Workflow",
        steps = listOf(
            Step.Delay("top", 1),
            Step.Repeat(
                "repeat",
                1,
                listOf(
                    Step.IfElse(
                        "if",
                        Condition.Equals(Value.Literal("a"), Value.Literal("b")),
                        whenTrue = emptyList(),
                        whenFalse = listOf(Step.Delay("nested", 1)),
                    ),
                ),
            ),
        ),
    )

    private fun record(failedStepId: String?) = RunRecord(
        id = "record",
        workflowId = "workflow",
        workflowName = "Workflow",
        startedAtMillis = 1,
        durationMillis = 1,
        status = RunStatus.Failed,
        failedStepId = failedStepId,
        failureMessage = "Failed",
    )
}
