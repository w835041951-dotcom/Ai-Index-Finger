package com.aiindexfinger.model

data class ValidationIssue(
    val stepId: String?,
    val message: String,
)

object WorkflowValidator {
    fun validate(workflow: Workflow): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        if (workflow.steps.isEmpty()) {
            issues += ValidationIssue(null, "Workflow has no steps")
            return issues
        }

        val seenStepIds = mutableSetOf<String>()
        val state = ValidationState()
        val estimatedExecutions = validateSteps(
            workflow.steps,
            mutableSetOf(),
            seenStepIds,
            issues,
            state,
            depth = 1,
        )
        if (estimatedExecutions > WorkflowLimits.MAX_EXECUTED_STEPS) {
            issues += ValidationIssue(
                null,
                "Workflow can execute more than ${WorkflowLimits.MAX_EXECUTED_STEPS} steps",
            )
        }
        return issues
    }

    private fun validateSteps(
        steps: List<Step>,
        definedVariables: MutableSet<String>,
        seenStepIds: MutableSet<String>,
        issues: MutableList<ValidationIssue>,
        state: ValidationState,
        depth: Int,
    ): Long {
        if (depth > WorkflowLimits.MAX_NESTING_DEPTH) {
            if (!state.reportedDepthLimit) {
                issues += ValidationIssue(null, "Workflow nesting exceeds ${WorkflowLimits.MAX_NESTING_DEPTH} levels")
                state.reportedDepthLimit = true
            }
            return 0
        }
        var estimatedExecutions = 0L
        steps.forEach { step ->
            state.definedSteps++
            if (state.definedSteps > WorkflowLimits.MAX_DEFINED_STEPS && !state.reportedStepLimit) {
                issues += ValidationIssue(null, "Workflow defines more than ${WorkflowLimits.MAX_DEFINED_STEPS} steps")
                state.reportedStepLimit = true
            }
            if (step.id.isBlank()) issues += ValidationIssue(step.id, "Step ID is blank")
            if (!seenStepIds.add(step.id)) issues += ValidationIssue(step.id, "Step ID is duplicated")
            if (step.timeoutMillis != null && step.timeoutMillis!! <= 0) {
                issues += ValidationIssue(step.id, "Step timeout must be positive")
            }

            val nestedExecutions = when (step) {
                is Step.SetVariable -> {
                    step.value.referencedVariables()
                        .filterNot { it in definedVariables }
                        .forEach { variableName ->
                            issues += ValidationIssue(step.id, "Variable '$variableName' is not defined")
                        }
                    definedVariables += step.name
                    0L
                }
                is Step.ReadNodeText -> {
                    definedVariables += step.variableName
                    0L
                }
                is Step.InputText -> {
                    val variableName = step.variableName
                    if (variableName != null && variableName !in definedVariables) {
                        issues += ValidationIssue(step.id, "Variable '$variableName' is not defined")
                    }
                    0L
                }
                is Step.IfElse -> {
                    validateCondition(step.id, step.condition, definedVariables, issues)
                    maxOf(
                        validateSteps(
                            step.whenTrue,
                            definedVariables.toMutableSet(),
                            seenStepIds,
                            issues,
                            state,
                            depth + 1,
                        ),
                        validateSteps(
                            step.whenFalse,
                            definedVariables.toMutableSet(),
                            seenStepIds,
                            issues,
                            state,
                            depth + 1,
                        ),
                    )
                }
                is Step.Repeat -> saturatingMultiply(
                    validateSteps(
                        step.steps,
                        definedVariables.toMutableSet(),
                        seenStepIds,
                        issues,
                        state,
                        depth + 1,
                    ),
                    step.times.toLong(),
                )
                else -> 0L
            }
            estimatedExecutions = saturatingAdd(estimatedExecutions, saturatingAdd(1, nestedExecutions))
        }
        return estimatedExecutions
    }

    private fun validateCondition(
        stepId: String,
        condition: Condition,
        definedVariables: Set<String>,
        issues: MutableList<ValidationIssue>,
    ) {
        if (condition is Condition.Equals) {
            listOf(condition.left, condition.right)
                .filterIsInstance<Value.Variable>()
                .filterNot { it.name in definedVariables }
                .forEach { issues += ValidationIssue(stepId, "Variable '${it.name}' is not defined") }
        }
    }

    private fun Value.referencedVariables(): Set<String> = when (this) {
        is Value.Literal -> emptySet()
        is Value.Variable -> setOf(name)
        is Value.Template -> template.templateVariables()
    }

    private fun saturatingAdd(left: Long, right: Long): Long =
        (left + right).coerceAtMost(WorkflowLimits.MAX_EXECUTED_STEPS + 1)

    private fun saturatingMultiply(left: Long, right: Long): Long =
        if (left == 0L || right == 0L) {
            0
        } else {
            val limit = WorkflowLimits.MAX_EXECUTED_STEPS + 1
            if (left > limit / right) limit else left * right
        }

    private data class ValidationState(
        var definedSteps: Int = 0,
        var reportedDepthLimit: Boolean = false,
        var reportedStepLimit: Boolean = false,
    )
}
