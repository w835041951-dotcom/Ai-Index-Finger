package com.aiindexfinger.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkflowValidatorTest {
    @Test
    fun `rejects an empty workflow`() {
        val workflow = Workflow(id = "empty", name = "Empty", steps = emptyList())

        assertEquals("Workflow has no steps", WorkflowValidator.validate(workflow).single().message)
    }

    @Test
    fun `reports duplicate IDs and undefined variables`() {
        val workflow = Workflow(
            id = "invalid",
            name = "Invalid",
            steps = listOf(
                Step.Delay("same", 100),
                Step.IfElse(
                    id = "same",
                    condition = Condition.Equals(Value.Variable("missing"), Value.Literal("yes")),
                    whenTrue = listOf(Step.Delay("nested", 100)),
                ),
            ),
        )

        val messages = WorkflowValidator.validate(workflow).map { it.message }

        assertTrue("Step ID is duplicated" in messages)
        assertTrue("Variable 'missing' is not defined" in messages)
    }

    @Test
    fun `accepts a defined variable before a condition`() {
        val workflow = Workflow(
            id = "valid",
            name = "Valid",
            steps = listOf(
                Step.SetVariable("set", "mode", Value.Literal("ready")),
                Step.IfElse(
                    id = "if",
                    condition = Condition.Equals(Value.Variable("mode"), Value.Literal("ready")),
                    whenTrue = listOf(Step.Delay("wait", 100)),
                ),
            ),
        )

        assertTrue(WorkflowValidator.validate(workflow).isEmpty())
    }

    @Test
    fun `rejects input from an undefined variable`() {
        val selector = NodeSelector("com.example", viewId = "com.example:id/input")
        val workflow = Workflow(
            id = "undefined-input",
            name = "Undefined input",
            steps = listOf(Step.InputText("input", selector, text = "", variableName = "missing")),
        )

        assertEquals(
            "Variable 'missing' is not defined",
            WorkflowValidator.validate(workflow).single().message,
        )
    }

    @Test
    fun `rejects an undefined variable in a template`() {
        val workflow = Workflow(
            id = "undefined-template",
            name = "Undefined template",
            steps = listOf(
                Step.SetVariable("set", "message", Value.Template("Hello ${'$'}{missing}")),
            ),
        )

        assertEquals(
            "Variable 'missing' is not defined",
            WorkflowValidator.validate(workflow).single().message,
        )
    }

    @Test
    fun `rejects a repeat tree above the execution budget`() {
        val workflow = Workflow(
            id = "too-many",
            name = "Too many",
            steps = listOf(
                Step.Repeat(
                    id = "outer",
                    times = 10_000,
                    steps = listOf(
                        Step.Repeat(
                            id = "inner",
                            times = 11,
                            steps = listOf(Step.Delay("delay", 1)),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(
            WorkflowValidator.validate(workflow)
                .any { it.message.contains("more than ${WorkflowLimits.MAX_EXECUTED_STEPS}") },
        )
    }

    @Test
    fun `rejects excessive nesting`() {
        var nested: Step = Step.Delay("leaf", 1)
        repeat(WorkflowLimits.MAX_NESTING_DEPTH) { index ->
            nested = Step.Repeat("repeat-$index", 1, listOf(nested))
        }
        val workflow = Workflow(id = "deep", name = "Deep", steps = listOf(nested))

        assertTrue(
            WorkflowValidator.validate(workflow)
                .any { it.message.contains("nesting exceeds") },
        )
    }

    @Test
    fun `rejects too many defined steps`() {
        val workflow = Workflow(
            id = "wide",
            name = "Wide",
            steps = List(WorkflowLimits.MAX_DEFINED_STEPS + 1) { index ->
                Step.Delay("delay-$index", 1)
            },
        )

        assertTrue(
            WorkflowValidator.validate(workflow)
                .any { it.message.contains("defines more than ${WorkflowLimits.MAX_DEFINED_STEPS}") },
        )
    }
}