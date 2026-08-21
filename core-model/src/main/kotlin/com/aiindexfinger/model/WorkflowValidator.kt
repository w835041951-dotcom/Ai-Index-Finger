package com.aiindexfinger.model

data class ValidationIssue(
    val stepId: String?,
    val code: ValidationIssueCode,
    val arguments: Map<String, String> = emptyMap(),
)

enum class ValidationIssueCode {
    EmptyWorkflow,
    ExecutionLimitExceeded,
    NestingLimitExceeded,
    DefinedStepLimitExceeded,
    BlankStepId,
    DuplicateStepId,
    DuplicateLabel,
    MissingJumpLabel,
    NonPositiveTimeout,
    BlankVariableName,
    UndefinedVariable,
    NegativeDelay,
    DraftWorkflow,
}

data class WorkflowValidationSummary(
    val issues: List<ValidationIssue>,
    val definedVariables: Set<String>,
    val referencedVariables: Set<String>,
    val definedStepCount: Int,
    val maximumNestingDepth: Int,
    val maximumStepExecutions: Long,
)

object WorkflowValidator {
    fun validate(workflow: Workflow): List<ValidationIssue> = inspect(workflow).issues

    fun structuralIssues(workflow: Workflow): List<ValidationIssue> =
        validate(workflow).filter { it.code in STRUCTURAL_ISSUE_CODES }

    fun inspect(workflow: Workflow): WorkflowValidationSummary {
        val issues = mutableListOf<ValidationIssue>()
        if (workflow.steps.isEmpty()) {
            issues += ValidationIssue(null, ValidationIssueCode.EmptyWorkflow)
            return WorkflowValidationSummary(issues, emptySet(), emptySet(), 0, 0, 0)
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
                ValidationIssueCode.ExecutionLimitExceeded,
                mapOf("limit" to WorkflowLimits.MAX_EXECUTED_STEPS.toString()),
            )
        }
        return WorkflowValidationSummary(
            issues = issues,
            definedVariables = state.variableDefinitions,
            referencedVariables = state.variableReferences,
            definedStepCount = state.definedSteps,
            maximumNestingDepth = state.maximumNestingDepth,
            maximumStepExecutions = estimatedExecutions,
        )
    }

    private fun validateSteps(
        steps: List<Step>,
        definedVariables: MutableSet<String>,
        seenStepIds: MutableSet<String>,
        issues: MutableList<ValidationIssue>,
        state: ValidationState,
        depth: Int,
    ): Long {
        state.maximumNestingDepth = maxOf(state.maximumNestingDepth, depth)
        if (depth > WorkflowLimits.MAX_NESTING_DEPTH) {
            if (!state.reportedDepthLimit) {
                issues += ValidationIssue(
                    null,
                    ValidationIssueCode.NestingLimitExceeded,
                    mapOf("limit" to WorkflowLimits.MAX_NESTING_DEPTH.toString()),
                )
                state.reportedDepthLimit = true
            }
            return 0
        }
        val labelNames = steps.filterIsInstance<Step.Label>().map(Step.Label::name)
        val duplicateLabelNames = labelNames.groupingBy { it }.eachCount()
            .filterValues { count -> count > 1 }
            .keys
        var estimatedExecutions = 0L
        steps.forEach { step ->
            val guaranteedVariablesBeforeStep = definedVariables.toSet()
            state.definedSteps++
            if (state.definedSteps > WorkflowLimits.MAX_DEFINED_STEPS && !state.reportedStepLimit) {
                issues += ValidationIssue(
                    null,
                    ValidationIssueCode.DefinedStepLimitExceeded,
                    mapOf("limit" to WorkflowLimits.MAX_DEFINED_STEPS.toString()),
                )
                state.reportedStepLimit = true
            }
            if (step.id.isBlank()) issues += ValidationIssue(step.id, ValidationIssueCode.BlankStepId)
            if (!seenStepIds.add(step.id)) issues += ValidationIssue(step.id, ValidationIssueCode.DuplicateStepId)
            if (step is Step.Label && step.name in duplicateLabelNames) {
                issues += ValidationIssue(
                    step.id,
                    ValidationIssueCode.DuplicateLabel,
                    mapOf("label" to step.name),
                )
            }
            if (step.timeoutMillis != null && step.timeoutMillis!! <= 0) {
                issues += ValidationIssue(step.id, ValidationIssueCode.NonPositiveTimeout)
            }

            val nestedExecutions = when (step) {
                is Step.SetVariable -> {
                    if (step.name.isBlank()) {
                        issues += ValidationIssue(step.id, ValidationIssueCode.BlankVariableName)
                    }
                    val references = step.value.referencedVariables()
                    state.variableReferences += references
                    references
                        .filterNot { it in definedVariables }
                        .forEach { variableName ->
                            issues += ValidationIssue(
                                step.id,
                                ValidationIssueCode.UndefinedVariable,
                                mapOf("variableName" to variableName),
                            )
                        }
                    definedVariables += step.name
                    state.variableDefinitions += step.name
                    0L
                }
                is Step.ReadNodeText -> {
                    definedVariables += step.variableName
                    state.variableDefinitions += step.variableName
                    0L
                }
                is Step.Delay -> {
                    if (step.durationMillis < 0) {
                        issues += ValidationIssue(step.id, ValidationIssueCode.NegativeDelay)
                    }
                    0L
                }
                is Step.InputText -> {
                    val references = step.value?.referencedVariables()
                        ?: step.variableName?.let(::setOf)
                        ?: emptySet()
                    state.variableReferences += references
                    references.filterNot(definedVariables::contains).forEach { variableName ->
                        issues += ValidationIssue(
                            step.id,
                            ValidationIssueCode.UndefinedVariable,
                            mapOf("variableName" to variableName),
                        )
                    }
                    0L
                }
                is Step.IfElse -> {
                    validateCondition(step.id, step.condition, definedVariables, issues, state)
                    val trueVariables = definedVariables.toMutableSet()
                    val falseVariables = definedVariables.toMutableSet()
                    val trueExecutions = validateSteps(
                        step.whenTrue,
                        trueVariables,
                        seenStepIds,
                        issues,
                        state,
                        depth + 1,
                    )
                    val falseExecutions = validateSteps(
                        step.whenFalse,
                        falseVariables,
                        seenStepIds,
                        issues,
                        state,
                        depth + 1,
                    )
                    definedVariables += trueVariables.intersect(falseVariables)
                    maxOf(trueExecutions, falseExecutions)
                }
                is Step.JumpIf -> {
                    step.condition?.let { condition ->
                        validateCondition(step.id, condition, definedVariables, issues, state)
                    }
                    if (step.targetLabel !in labelNames) {
                        issues += ValidationIssue(
                            step.id,
                            ValidationIssueCode.MissingJumpLabel,
                            mapOf("label" to step.targetLabel),
                        )
                    }
                    0L
                }
                is Step.ScrollUntil -> {
                    when (val stopCondition = step.stopCondition) {
                        is ScrollUntilStopCondition.ConditionMet ->
                            validateCondition(step.id, stopCondition.condition, definedVariables, issues, state)
                        else -> Unit
                    }
                    step.maxScrolls?.toLong() ?: WorkflowLimits.MAX_EXECUTED_STEPS - 1
                }
                is Step.Repeat -> {
                    val repeatedVariables = definedVariables.toMutableSet()
                    val repeatedExecutions = validateSteps(
                        step.steps,
                        repeatedVariables,
                        seenStepIds,
                        issues,
                        state,
                        depth + 1,
                    )
                    definedVariables += repeatedVariables
                    saturatingMultiply(repeatedExecutions, step.times.toLong())
                }
                else -> 0L
            }
            if (step.failurePolicy is FailurePolicy.Continue) {
                definedVariables.retainAll(guaranteedVariablesBeforeStep)
            }
            val attempts = (step.failurePolicy as? FailurePolicy.Retry)?.attempts?.plus(1) ?: 1
            val maximumStepExecutions = saturatingMultiply(
                saturatingAdd(1, nestedExecutions),
                attempts.toLong(),
            )
            estimatedExecutions = saturatingAdd(estimatedExecutions, maximumStepExecutions)
        }
        return estimatedExecutions
    }

    private fun validateCondition(
        stepId: String,
        condition: Condition,
        definedVariables: Set<String>,
        issues: MutableList<ValidationIssue>,
        state: ValidationState,
    ) {
        if (condition is Condition.Equals) {
            val references = listOf(condition.left, condition.right)
                .flatMap { it.referencedVariables() }
                .toSet()
            state.variableReferences += references
            references
                .filterNot { it in definedVariables }
                .forEach { variableName ->
                    issues += ValidationIssue(
                        stepId,
                        ValidationIssueCode.UndefinedVariable,
                        mapOf("variableName" to variableName),
                    )
                }
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
        var maximumNestingDepth: Int = 0,
        var reportedDepthLimit: Boolean = false,
        var reportedStepLimit: Boolean = false,
        val variableDefinitions: MutableSet<String> = linkedSetOf(),
        val variableReferences: MutableSet<String> = linkedSetOf(),
    )

    private val STRUCTURAL_ISSUE_CODES = setOf(
        ValidationIssueCode.NestingLimitExceeded,
        ValidationIssueCode.DefinedStepLimitExceeded,
        ValidationIssueCode.BlankStepId,
        ValidationIssueCode.DuplicateStepId,
        ValidationIssueCode.DuplicateLabel,
        ValidationIssueCode.MissingJumpLabel,
    )
}

fun Workflow.effectiveState(): WorkflowState =
    state ?: if (WorkflowValidator.validate(this).isEmpty()) WorkflowState.Ready else WorkflowState.Draft

fun Workflow.readinessIssues(): List<ValidationIssue> = when (effectiveState()) {
    WorkflowState.Draft -> listOf(ValidationIssue(null, ValidationIssueCode.DraftWorkflow))
    WorkflowState.Ready -> WorkflowValidator.validate(this)
}

fun Workflow.isReadyToRun(): Boolean = readinessIssues().isEmpty()
