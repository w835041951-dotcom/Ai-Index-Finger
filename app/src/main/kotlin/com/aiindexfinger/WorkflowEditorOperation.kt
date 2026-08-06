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