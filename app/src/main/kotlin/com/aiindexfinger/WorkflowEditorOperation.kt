package com.aiindexfinger

internal enum class WorkflowEditorOperation {
    LaunchApp,
    Click,
    ImageClick,
    RecordedClick,
    LongClick,
    Tap,
    Scroll,
    ScrollUntil,
    InputText,
    Swipe,
    Delay,
    GlobalBack,
    GlobalHome,
    GlobalRecents,
    GlobalNotifications,
    GlobalQuickSettings,
    GlobalPowerDialog,
    GlobalLockScreen,
    WaitForNode,
    SetVariable,
    ReadNodeText,
    Repeat,
    Label,
    JumpIf,
    VariableCondition,
    NodeCondition,
}

internal val ALL_WORKFLOW_EDITOR_OPERATIONS: Set<WorkflowEditorOperation> =
    WorkflowEditorOperation.entries.toSet()

internal enum class WorkflowOperationUnavailableReason {
    AutomationServiceRequired,
    ExistingStepRequired,
    LabelRequired,
}

internal fun WorkflowEditorOperation.isAvailable(
    hasSteps: Boolean,
    serviceConnected: Boolean,
    hasLabels: Boolean = true,
): Boolean = unavailableReason(hasSteps, serviceConnected, hasLabels) == null

internal fun WorkflowEditorOperation.unavailableReason(
    hasSteps: Boolean,
    serviceConnected: Boolean,
    hasLabels: Boolean = true,
): WorkflowOperationUnavailableReason? = when (this) {
    WorkflowEditorOperation.RecordedClick -> if (serviceConnected) null else {
        WorkflowOperationUnavailableReason.AutomationServiceRequired
    }
    WorkflowEditorOperation.JumpIf -> if (hasLabels) null else {
        WorkflowOperationUnavailableReason.LabelRequired
    }
    WorkflowEditorOperation.Repeat,
    WorkflowEditorOperation.VariableCondition,
    WorkflowEditorOperation.NodeCondition -> if (hasSteps) null else {
        WorkflowOperationUnavailableReason.ExistingStepRequired
    }
    else -> null
}