package com.aiindexfinger

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkflowEditorOperationTest {
    @Test
    fun floatingAndRegularEditorShareEveryOperation() {
        assertEquals(
            setOf(
                WorkflowEditorOperation.LaunchApp,
                WorkflowEditorOperation.Click,
                WorkflowEditorOperation.ImageClick,
                WorkflowEditorOperation.RecordedClick,
                WorkflowEditorOperation.LongClick,
                WorkflowEditorOperation.Tap,
                WorkflowEditorOperation.Scroll,
                WorkflowEditorOperation.InputText,
                WorkflowEditorOperation.Swipe,
                WorkflowEditorOperation.Delay,
                WorkflowEditorOperation.GlobalBack,
                WorkflowEditorOperation.GlobalHome,
                WorkflowEditorOperation.GlobalRecents,
                WorkflowEditorOperation.WaitForNode,
                WorkflowEditorOperation.SetVariable,
                WorkflowEditorOperation.ReadNodeText,
                WorkflowEditorOperation.Repeat,
                WorkflowEditorOperation.VariableCondition,
                WorkflowEditorOperation.NodeCondition,
            ),
            ALL_WORKFLOW_EDITOR_OPERATIONS,
        )
    }
}