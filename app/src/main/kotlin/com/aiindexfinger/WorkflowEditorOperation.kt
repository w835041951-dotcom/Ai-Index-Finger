package com.aiindexfinger

internal enum class WorkflowEditorOperation {
    LaunchApp,
    Click,
    ImageClick,
    RecordedClick,
    LongClick,
    Tap,
    Scroll,
    InputText,
    Swipe,
    Delay,
    GlobalBack,
    GlobalHome,
    GlobalRecents,
    WaitForNode,
    SetVariable,
    ReadNodeText,
    Repeat,
    VariableCondition,
    NodeCondition,
}

internal val ALL_WORKFLOW_EDITOR_OPERATIONS: Set<WorkflowEditorOperation> =
    WorkflowEditorOperation.entries.toSet()

internal enum class WorkflowOperationUnavailableReason {
    AutomationServiceRequired,
    ExistingStepRequired,
}

internal fun WorkflowEditorOperation.isAvailable(
    hasSteps: Boolean,
    serviceConnected: Boolean,
): Boolean = unavailableReason(hasSteps, serviceConnected) == null

internal fun WorkflowEditorOperation.unavailableReason(
    hasSteps: Boolean,
    serviceConnected: Boolean,
): WorkflowOperationUnavailableReason? = when (this) {
    WorkflowEditorOperation.RecordedClick -> if (serviceConnected) null else {
        WorkflowOperationUnavailableReason.AutomationServiceRequired
    }
    WorkflowEditorOperation.Repeat,
    WorkflowEditorOperation.VariableCondition,
    WorkflowEditorOperation.NodeCondition -> if (hasSteps) null else {
        WorkflowOperationUnavailableReason.ExistingStepRequired
    }
    else -> null
}