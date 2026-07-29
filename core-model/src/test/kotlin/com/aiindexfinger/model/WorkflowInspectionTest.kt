package com.aiindexfinger.model

import kotlin.test.Test
import kotlin.test.assertEquals

class WorkflowInspectionTest {
    private val selector = NodeSelector("com.example", text = "Target")

    @Test
    fun `collects every selector occurrence through nested branches and repeats`() {
        val workflow = Workflow(
            id = "inspection",
            name = "Inspection",
            steps = listOf(
                Step.Click("click", selector),
                Step.IfElse(
                    id = "condition",
                    condition = Condition.NodeExists(selector),
                    whenTrue = listOf(Step.InputText("input", selector, "hello")),
                    whenFalse = listOf(
                        Step.Repeat(
                            id = "repeat",
                            times = 2,
                            steps = listOf(Step.WaitForNode("wait", selector)),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                "click" to SelectorRole.Click,
                "condition" to SelectorRole.NodeCondition,
                "input" to SelectorRole.InputText,
                "wait" to SelectorRole.WaitForNode,
            ),
            workflow.selectorUses().map { it.stepId to it.role },
        )
    }

    @Test
    fun `deduplicates target packages while retaining selector occurrences`() {
        val otherSelector = NodeSelector("com.other", viewId = "com.other:id/item")
        val workflow = Workflow(
            id = "packages",
            name = "Packages",
            steps = listOf(
                Step.LaunchApp("launch", "com.example"),
                Step.Click("first", selector),
                Step.LongClick("second", selector),
                Step.Scroll("third", otherSelector, ScrollDirection.Forward),
            ),
        )

        assertEquals(3, workflow.selectorUses().size)
        assertEquals(setOf("com.example", "com.other"), workflow.targetPackages())
    }
}