package com.aiindexfinger.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkflowValidatorTest {
    @Test
    fun `structural issues exclude ordinary incomplete draft problems`() {
        val incomplete = Workflow(
            id = "draft",
            name = "Draft",
            steps = listOf(
                Step.InputText(
                    id = "input",
                    selector = NodeSelector("com.example", text = "Input"),
                    text = "",
                    variableName = "missing",
                ),
            ),
            state = WorkflowState.Draft,
        )
        val duplicate = incomplete.copy(
            steps = listOf(Step.Delay("same", 1), Step.Delay("same", 2)),
        )

        assertTrue(WorkflowValidator.structuralIssues(incomplete).isEmpty())
        assertEquals(
            listOf(ValidationIssueCode.DuplicateStepId),
            WorkflowValidator.structuralIssues(duplicate).map(ValidationIssue::code),
        )
    }
    @Test
    fun `legacy valid workflow is ready while invalid legacy workflow becomes draft`() {
        val valid = Workflow(
            schemaVersion = 12,
            id = "valid",
            name = "Valid",
            steps = listOf(Step.Delay("delay", 1)),
        )
        val invalid = valid.copy(id = "invalid", steps = emptyList())

        assertEquals(WorkflowState.Ready, valid.effectiveState())
        assertTrue(valid.isReadyToRun())
        assertEquals(WorkflowState.Draft, invalid.effectiveState())
        assertFalse(invalid.isReadyToRun())
    }

    @Test
    fun `explicit draft remains draft even when validation is clean`() {
        val workflow = Workflow(
            id = "draft",
            name = "Draft",
            steps = listOf(Step.Delay("delay", 1)),
            state = WorkflowState.Draft,
        )

        assertEquals(WorkflowState.Draft, workflow.effectiveState())
        assertEquals(ValidationIssueCode.DraftWorkflow, workflow.readinessIssues().single().code)
    }

    @Test
    fun `explicit ready workflow still requires clean validation`() {
        val workflow = Workflow(
            id = "invalid-ready",
            name = "Invalid ready",
            steps = emptyList(),
            state = WorkflowState.Ready,
        )

        assertEquals(WorkflowState.Ready, workflow.effectiveState())
        assertEquals(ValidationIssueCode.EmptyWorkflow, workflow.readinessIssues().single().code)
    }
    @Test
    fun `rejects an empty workflow`() {
        val workflow = Workflow(id = "empty", name = "Empty", steps = emptyList())

        assertEquals(ValidationIssueCode.EmptyWorkflow, WorkflowValidator.validate(workflow).single().code)
    }

    @Test
    fun `rejects negative delays while preserving zero delay compatibility`() {
        val negative = Workflow(
            id = "negative-delay",
            name = "Negative delay",
            steps = listOf(Step.Delay("delay", -1)),
        )
        val zero = negative.copy(
            id = "zero-delay",
            name = "Zero delay",
            steps = listOf(Step.Delay("delay", 0)),
        )

        assertEquals(ValidationIssueCode.NegativeDelay, WorkflowValidator.validate(negative).single().code)
        assertTrue(WorkflowValidator.validate(zero).isEmpty())
    }

    @Test
    fun `rejects a blank variable name`() {
        val workflow = Workflow(
            id = "blank-variable",
            name = "Blank variable",
            steps = listOf(Step.SetVariable("set", " ", Value.Literal("value"))),
        )

        assertEquals(ValidationIssueCode.BlankVariableName, WorkflowValidator.validate(workflow).single().code)
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

        val issues = WorkflowValidator.validate(workflow)

        assertTrue(issues.any { it.code == ValidationIssueCode.DuplicateStepId })
        assertTrue(
            issues.any {
                it.code == ValidationIssueCode.UndefinedVariable && it.arguments["variableName"] == "missing"
            },
        )
    }

    @Test
    fun `validates labels and jumps within their current step list`() {
        val selector = NodeSelector("com.example", text = "Target")
        val valid = Workflow(
            id = "valid-jump",
            name = "Valid jump",
            steps = listOf(
                Step.Label("outer-label", "same-name"),
                Step.Repeat(
                    "repeat",
                    1,
                    listOf(
                        Step.Label("inner-label", "same-name"),
                        Step.JumpIf("inner-jump", "same-name"),
                    ),
                ),
            ),
        )
        val invalid = valid.copy(
            id = "invalid-jump",
            steps = listOf(
                Step.Label("first", "duplicate"),
                Step.Label("second", "duplicate"),
                Step.JumpIf("missing", "not-here"),
                Step.Repeat(
                    "repeat",
                    1,
                    listOf(
                        Step.JumpIf("cross-scope", "duplicate", Condition.NodeExists(selector)),
                    ),
                ),
            ),
        )

        assertTrue(WorkflowValidator.validate(valid).isEmpty())
        assertEquals(
            setOf(ValidationIssueCode.DuplicateLabel, ValidationIssueCode.MissingJumpLabel),
            WorkflowValidator.validate(invalid).map(ValidationIssue::code).toSet(),
        )
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
    fun `accepts variables defined by every branch and rejects one branch definitions`() {
        val bothBranches = branchVariableWorkflow(
            whenFalse = listOf(Step.SetVariable("set-false", "result", Value.Literal("false"))),
        )
        val oneBranch = branchVariableWorkflow(whenFalse = emptyList())

        assertTrue(WorkflowValidator.validate(bothBranches).isEmpty())
        assertEquals(
            ValidationIssue(
                "consume",
                ValidationIssueCode.UndefinedVariable,
                mapOf("variableName" to "result"),
            ),
            WorkflowValidator.validate(oneBranch).single(),
        )
    }

    @Test
    fun `accepts variables defined in a guaranteed repeat`() {
        val workflow = Workflow(
            id = "repeat-variable",
            name = "Repeat variable",
            steps = listOf(
                Step.Repeat(
                    id = "repeat",
                    times = 1,
                    steps = listOf(Step.SetVariable("set", "result", Value.Literal("ready"))),
                ),
                Step.SetVariable("consume", "copy", Value.Variable("result")),
            ),
        )

        assertTrue(WorkflowValidator.validate(workflow).isEmpty())
    }

    @Test
    fun `continue policy does not guarantee variables from a failed producer`() {
        val selector = NodeSelector("com.example", text = "Result")
        val direct = Workflow(
            id = "continued-read",
            name = "Continued read",
            steps = listOf(
                Step.ReadNodeText(
                    "read",
                    selector,
                    "result",
                    NodeAttribute.Text,
                    failurePolicy = FailurePolicy.Continue,
                ),
                Step.InputText("consume", selector, text = "", variableName = "result"),
            ),
        )
        val nested = Workflow(
            id = "continued-repeat",
            name = "Continued repeat",
            steps = listOf(
                Step.Repeat(
                    "repeat",
                    times = 1,
                    steps = listOf(Step.SetVariable("set", "result", Value.Literal("ready"))),
                    failurePolicy = FailurePolicy.Continue,
                ),
                Step.InputText("consume", selector, text = "", variableName = "result"),
            ),
        )

        assertEquals(ValidationIssueCode.UndefinedVariable, WorkflowValidator.validate(direct).single().code)
        assertEquals(ValidationIssueCode.UndefinedVariable, WorkflowValidator.validate(nested).single().code)
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
            "missing",
            WorkflowValidator.validate(workflow).single().arguments["variableName"],
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
            ValidationIssueCode.UndefinedVariable,
            WorkflowValidator.validate(workflow).single().code,
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
                .any {
                    it.code == ValidationIssueCode.ExecutionLimitExceeded &&
                        it.arguments["limit"] == WorkflowLimits.MAX_EXECUTED_STEPS.toString()
                },
        )
    }

    @Test
    fun `unbounded scroll until reports the full runtime execution budget`() {
        val selector = NodeSelector("com.example", text = "List")
        val workflow = Workflow(
            id = "scroll-until-budget",
            name = "Scroll until budget",
            steps = listOf(
                Step.ScrollUntil(
                    id = "scroll-until",
                    selector = selector,
                    direction = ScrollDirection.Forward,
                    stopCondition = ScrollUntilStopCondition.NoProgress,
                ),
            ),
        )

        val summary = WorkflowValidator.inspect(workflow)

        assertEquals(WorkflowLimits.MAX_EXECUTED_STEPS, summary.maximumStepExecutions)
        assertTrue(summary.issues.isEmpty())
    }

    @Test
    fun `retry attempts count toward the execution budget`() {
        val workflow = Workflow(
            id = "retry-budget",
            name = "Retry budget",
            steps = listOf(
                Step.Repeat(
                    id = "repeat",
                    times = 10_000,
                    steps = listOf(
                        Step.Delay(
                            id = "delay",
                            durationMillis = 1,
                            failurePolicy = FailurePolicy.Retry(attempts = 10, delayMillis = 0),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(
            WorkflowValidator.validate(workflow)
                .any { it.code == ValidationIssueCode.ExecutionLimitExceeded },
        )
    }

    @Test
    fun `inspection reports variables and structural limits from validation traversal`() {
        val workflow = Workflow(
            id = "inspection",
            name = "Inspection",
            steps = listOf(
                Step.SetVariable("set", "source", Value.Literal("ready")),
                Step.Repeat(
                    id = "repeat",
                    times = 3,
                    steps = listOf(
                        Step.SetVariable("copy", "result", Value.Variable("source")),
                    ),
                ),
            ),
        )

        val summary = WorkflowValidator.inspect(workflow)

        assertEquals(setOf("source", "result"), summary.definedVariables)
        assertEquals(setOf("source"), summary.referencedVariables)
        assertEquals(3, summary.definedStepCount)
        assertEquals(2, summary.maximumNestingDepth)
        assertEquals(5, summary.maximumStepExecutions)
        assertTrue(summary.issues.isEmpty())
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
                .any { it.code == ValidationIssueCode.NestingLimitExceeded },
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
                .any { it.code == ValidationIssueCode.DefinedStepLimitExceeded },
        )
    }


    private fun branchVariableWorkflow(whenFalse: List<Step>): Workflow = Workflow(
        id = "branch-variable",
        name = "Branch variable",
        steps = listOf(
            Step.IfElse(
                id = "if",
                condition = Condition.Equals(Value.Literal("yes"), Value.Literal("yes")),
                whenTrue = listOf(Step.SetVariable("set-true", "result", Value.Literal("true"))),
                whenFalse = whenFalse,
            ),
            Step.SetVariable("consume", "copy", Value.Variable("result")),
        ),
    )
}