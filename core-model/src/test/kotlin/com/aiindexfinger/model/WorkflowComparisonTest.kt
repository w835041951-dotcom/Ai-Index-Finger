package com.aiindexfinger.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkflowComparisonTest {
    @Test
    fun `reports image click point changes`() {
        val before = workflow(steps = listOf(
            Step.ImageClick("image", "com.example", "aGVsbG8=", 24, 24),
        ))
        val after = workflow(steps = listOf(
            Step.ImageClick(
                "image",
                "com.example",
                "aGVsbG8=",
                24,
                24,
                templateClickX = 4,
                templateClickY = 8,
            ),
        ))

        assertEquals(
            listOf(WorkflowDifference.StepChanged(
                rootPath(0), StepComparisonField.Configuration, "image_click", "image_click",
            )),
            compareWorkflows(before, after).differences,
        )
    }

    @Test
    fun `reports image click selection configuration changes`() {
        val before = workflow(steps = listOf(
            Step.ImageClick("image", "com.example", "aGVsbG8=", 24, 24),
        ))
        val after = workflow(steps = listOf(
            Step.ImageClick(
                "image",
                "com.example",
                "aGVsbG8=",
                24,
                24,
                selectionMode = ImageClickSelectionMode.AllMatches,
                maxClicks = 40,
                clickIntervalMillis = 500,
            ),
        ))

        assertEquals(
            listOf(WorkflowDifference.StepChanged(
                rootPath(0), StepComparisonField.Configuration, "image_click", "image_click",
            )),
            compareWorkflows(before, after).differences,
        )
    }

    @Test
    fun `ignores legacy image click ambiguity margin`() {
        val before = workflow(steps = listOf(
            Step.ImageClick("image", "com.example", "aGVsbG8=", 24, 24, ambiguityMarginPermille = 25),
        ))
        val after = workflow(steps = listOf(
            Step.ImageClick("image", "com.example", "aGVsbG8=", 24, 24, ambiguityMarginPermille = 400),
        ))

        assertTrue(compareWorkflows(before, after).isIdentical)
    }

    @Test
    fun `reports jump target and condition changes`() {
        val before = workflow(steps = listOf(Step.JumpIf("jump", "first")))
        val after = workflow(
            steps = listOf(
                Step.JumpIf(
                    "jump",
                    "second",
                    Condition.Equals(Value.Literal("yes"), Value.Literal("yes")),
                ),
            ),
        )

        assertEquals(
            listOf(WorkflowDifference.StepChanged(
                rootPath(0), StepComparisonField.Configuration, "jump_if", "jump_if",
            )),
            compareWorkflows(before, after).differences,
        )
    }

    @Test
    fun `reports scroll until stop configuration changes`() {
        val selector = NodeSelector("com.example", text = "List")
        val before = workflow(
            steps = listOf(
                Step.ScrollUntil(
                    "scroll-until",
                    selector,
                    ScrollDirection.Forward,
                    ScrollUntilStopCondition.NodeAppears(selector),
                ),
            ),
        )
        val after = workflow(
            steps = listOf(
                Step.ScrollUntil(
                    "scroll-until",
                    selector,
                    ScrollDirection.Backward,
                    ScrollUntilStopCondition.NoProgress,
                    maxScrolls = 5,
                ),
            ),
        )

        assertEquals(
            listOf(WorkflowDifference.StepChanged(
                rootPath(0), StepComparisonField.Configuration, "scroll_until", "scroll_until",
            )),
            compareWorkflows(before, after).differences,
        )
    }

    @Test
    fun `reports recorded click fallback cause changes`() {
        val control = RecordedControl(
            packageName = "com.example",
            bounds = RecordedBounds(0, 0, 20, 20),
            clickable = true,
            enabled = true,
            longClickable = false,
            scrollable = false,
        )
        val before = workflow(steps = listOf(Step.RecordedClick("click", 10, 10, control = control)))
        val after = workflow(steps = listOf(
            Step.RecordedClick(
                "click",
                10,
                10,
                control = control,
                fallbackCause = RecordedClickFallbackCause.HierarchyUnavailable,
            ),
        ))

        assertEquals(
            listOf(WorkflowDifference.StepChanged(
                rootPath(0), StepComparisonField.Configuration, "recorded_click", "recorded_click",
            )),
            compareWorkflows(before, after).differences,
        )
    }

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