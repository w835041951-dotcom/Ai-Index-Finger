package com.aiindexfinger.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkflowComparisonTest {
    @Test
    fun `ignores workflow and step ids`() {
        val before = workflow(
            id = "before",
            steps = listOf(Step.Delay("old-step", 1_000)),
        )
        val after = workflow(
            id = "after",
            steps = listOf(Step.Delay("new-step", 1_000)),
        )

        assertTrue(compareWorkflows(before, after).isIdentical)
    }

    @Test
    fun `reports metadata and nested changes in deterministic depth first order`() {
        val before = workflow(
            name = "Before",
            timeout = 10_000,
            state = WorkflowState.Draft,
            steps = listOf(
                Step.Repeat(
                    id = "repeat",
                    times = 2,
                    steps = listOf(Step.Delay("delay", 100)),
                ),
                Step.IfElse(
                    id = "if",
                    condition = Condition.Equals(Value.Literal("a"), Value.Literal("a")),
                    whenTrue = listOf(Step.Tap("tap", 1, 2)),
                    whenFalse = listOf(Step.Delay("removed", 200)),
                ),
            ),
        )
        val after = workflow(
            name = "After",
            timeout = 20_000,
            state = WorkflowState.Ready,
            steps = listOf(
                Step.Repeat(
                    id = "repeat-new-id",
                    times = 3,
                    steps = listOf(
                        Step.Delay("delay-new-id", 150),
                        Step.GlobalAction("added", SystemAction.Home),
                    ),
                ),
                Step.IfElse(
                    id = "if-new-id",
                    condition = Condition.Equals(Value.Literal("a"), Value.Literal("b")),
                    whenTrue = listOf(Step.Swipe("swipe", 1, 2, 3, 4)),
                    whenFalse = emptyList(),
                ),
            ),
        )

        val differences = compareWorkflows(before, after).differences

        assertEquals(
            listOf(
                WorkflowDifference.MetadataChanged(WorkflowMetadataField.Name),
                WorkflowDifference.MetadataChanged(WorkflowMetadataField.State),
                WorkflowDifference.MetadataChanged(WorkflowMetadataField.DefaultStepTimeout),
                WorkflowDifference.StepChanged(
                    rootPath(0), StepComparisonField.Configuration, "repeat", "repeat",
                ),
                WorkflowDifference.StepChanged(
                    childPath(0, StepComparisonBranch.Repeat, 0),
                    StepComparisonField.Configuration,
                    "delay",
                    "delay",
                ),
                WorkflowDifference.StepAdded(
                    childPath(0, StepComparisonBranch.Repeat, 1),
                    "global_action",
                ),
                WorkflowDifference.StepChanged(
                    rootPath(1), StepComparisonField.Configuration, "if_else", "if_else",
                ),
                WorkflowDifference.StepChanged(
                    childPath(1, StepComparisonBranch.WhenTrue, 0),
                    StepComparisonField.Type,
                    "tap",
                    "swipe",
                ),
                WorkflowDifference.StepRemoved(
                    childPath(1, StepComparisonBranch.WhenFalse, 0),
                    "delay",
                ),
            ),
            differences,
        )
    }

    private fun workflow(
        id: String = "workflow",
        name: String = "Workflow",
        timeout: Long = 10_000,
        state: WorkflowState = WorkflowState.Draft,
        steps: List<Step>,
    ) = Workflow(
        id = id,
        name = name,
        defaultStepTimeoutMillis = timeout,
        state = state,
        steps = steps,
    )

    private fun rootPath(index: Int) = StepComparisonPath(index)

    private fun childPath(
        parentIndex: Int,
        branch: StepComparisonBranch,
        index: Int,
    ) = StepComparisonPath(index, rootPath(parentIndex), branch)
}