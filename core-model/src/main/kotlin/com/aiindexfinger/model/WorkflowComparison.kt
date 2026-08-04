package com.aiindexfinger.model

enum class WorkflowMetadataField {
    Name,
    State,
    DefaultStepTimeout,
}

enum class StepComparisonField {
    Type,
    Configuration,
}

enum class StepComparisonBranch {
    Repeat,
    WhenTrue,
    WhenFalse,
}

data class StepComparisonPath(
    val index: Int,
    val parent: StepComparisonPath? = null,
    val branch: StepComparisonBranch? = null,
)

sealed interface WorkflowDifference {
    data class MetadataChanged(val field: WorkflowMetadataField) : WorkflowDifference

    data class StepAdded(
        val path: StepComparisonPath,
        val stepType: String,
    ) : WorkflowDifference

    data class StepRemoved(
        val path: StepComparisonPath,
        val stepType: String,
    ) : WorkflowDifference

    data class StepChanged(
        val path: StepComparisonPath,
        val field: StepComparisonField,
        val beforeStepType: String,
        val afterStepType: String,
    ) : WorkflowDifference
}

data class WorkflowComparison(
    val differences: List<WorkflowDifference>,
) {
    val isIdentical: Boolean get() = differences.isEmpty()
}

fun compareWorkflows(before: Workflow, after: Workflow): WorkflowComparison = WorkflowComparison(
    differences = buildList {
        if (before.name != after.name) add(WorkflowDifference.MetadataChanged(WorkflowMetadataField.Name))
        if (before.effectiveState() != after.effectiveState()) {
            add(WorkflowDifference.MetadataChanged(WorkflowMetadataField.State))
        }
        if (before.defaultStepTimeoutMillis != after.defaultStepTimeoutMillis) {
            add(WorkflowDifference.MetadataChanged(WorkflowMetadataField.DefaultStepTimeout))
        }
        compareStepLists(before.steps, after.steps, parent = null, branch = null)
    },
)

private fun MutableList<WorkflowDifference>.compareStepLists(
    before: List<Step>,
    after: List<Step>,
    parent: StepComparisonPath?,
    branch: StepComparisonBranch?,
) {
    repeat(maxOf(before.size, after.size)) { index ->
        val path = StepComparisonPath(index = index, parent = parent, branch = branch)
        val beforeStep = before.getOrNull(index)
        val afterStep = after.getOrNull(index)
        when {
            beforeStep == null && afterStep != null -> add(
                WorkflowDifference.StepAdded(path, afterStep.comparisonType()),
            )
            beforeStep != null && afterStep == null -> add(
                WorkflowDifference.StepRemoved(path, beforeStep.comparisonType()),
            )
            beforeStep != null && afterStep != null -> compareSteps(beforeStep, afterStep, path)
        }
    }
}

private fun MutableList<WorkflowDifference>.compareSteps(
    before: Step,
    after: Step,
    path: StepComparisonPath,
) {
    val beforeType = before.comparisonType()
    val afterType = after.comparisonType()
    if (beforeType != afterType) {
        add(WorkflowDifference.StepChanged(path, StepComparisonField.Type, beforeType, afterType))
        return
    }
    if (before.comparisonConfiguration() != after.comparisonConfiguration()) {
        add(WorkflowDifference.StepChanged(path, StepComparisonField.Configuration, beforeType, afterType))
    }
    when {
        before is Step.Repeat && after is Step.Repeat -> compareStepLists(
            before.steps,
            after.steps,
            parent = path,
            branch = StepComparisonBranch.Repeat,
        )
        before is Step.IfElse && after is Step.IfElse -> {
            compareStepLists(
                before.whenTrue,
                after.whenTrue,
                parent = path,
                branch = StepComparisonBranch.WhenTrue,
            )
            compareStepLists(
                before.whenFalse,
                after.whenFalse,
                parent = path,
                branch = StepComparisonBranch.WhenFalse,
            )
        }
    }
}

private fun Step.comparisonType(): String = when (this) {
    is Step.LaunchApp -> "launch_app"
    is Step.Click -> "click"
    is Step.RecordedClick -> "recorded_click"
    is Step.ImageClick -> "image_click"
    is Step.LongClick -> "long_click"
    is Step.InputText -> "input_text"
    is Step.ReadNodeText -> "read_node_text"
    is Step.Swipe -> "swipe"
    is Step.Scroll -> "scroll"
    is Step.Tap -> "tap"
    is Step.GlobalAction -> "global_action"
    is Step.WaitForNode -> "wait_for_node"
    is Step.Delay -> "delay"
    is Step.SetVariable -> "set_variable"
    is Step.IfElse -> "if_else"
    is Step.Repeat -> "repeat"
}

private fun Step.comparisonConfiguration(): Any = when (this) {
    is Step.LaunchApp -> listOf(packageName, intentAction, timeoutMillis, failurePolicy)
    is Step.Click -> listOf(selector, timeoutMillis, failurePolicy)
    is Step.RecordedClick -> listOf(
        x,
        y,
        selector,
        control,
        targetMode,
        fallbackCause,
        timeoutMillis,
        failurePolicy,
    )
    is Step.ImageClick -> listOf(
        packageName,
        templatePngBase64,
        templateWidth,
        templateHeight,
        minimumScorePermille,
        ambiguityMarginPermille,
        timeoutMillis,
        failurePolicy,
    )
    is Step.LongClick -> listOf(selector, timeoutMillis, failurePolicy)
    is Step.InputText -> listOf(selector, text, variableName, inputMethod, timeoutMillis, failurePolicy)
    is Step.ReadNodeText -> listOf(selector, variableName, attribute, timeoutMillis, failurePolicy)
    is Step.Swipe -> listOf(startX, startY, endX, endY, durationMillis, timeoutMillis, failurePolicy)
    is Step.Scroll -> listOf(selector, direction, timeoutMillis, failurePolicy)
    is Step.Tap -> listOf(x, y, timeoutMillis, failurePolicy)
    is Step.GlobalAction -> listOf(action, timeoutMillis, failurePolicy)
    is Step.WaitForNode -> listOf(selector, mustExist, timeoutMillis, failurePolicy)
    is Step.Delay -> listOf(durationMillis, timeoutMillis, failurePolicy)
    is Step.SetVariable -> listOf(name, value, timeoutMillis, failurePolicy)
    is Step.IfElse -> listOf(condition, timeoutMillis, failurePolicy)
    is Step.Repeat -> listOf(times, timeoutMillis, failurePolicy)
}