package com.aiindexfinger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
                WorkflowEditorOperation.ScrollUntil,
                WorkflowEditorOperation.InputText,
                WorkflowEditorOperation.Swipe,
                WorkflowEditorOperation.Delay,
                WorkflowEditorOperation.GlobalBack,
                WorkflowEditorOperation.GlobalHome,
                WorkflowEditorOperation.GlobalRecents,
                WorkflowEditorOperation.GlobalNotifications,
                WorkflowEditorOperation.GlobalQuickSettings,
                WorkflowEditorOperation.GlobalPowerDialog,
                WorkflowEditorOperation.GlobalLockScreen,
                WorkflowEditorOperation.WaitForNode,
                WorkflowEditorOperation.SetVariable,
                WorkflowEditorOperation.ReadNodeText,
                WorkflowEditorOperation.Repeat,
                WorkflowEditorOperation.Label,
                WorkflowEditorOperation.JumpIf,
                WorkflowEditorOperation.VariableCondition,
                WorkflowEditorOperation.NodeCondition,
            ),
            ALL_WORKFLOW_EDITOR_OPERATIONS,
        )
    }

    @Test
    fun chooserPreservesOperationPrerequisites() {
        assertFalse(WorkflowEditorOperation.RecordedClick.isAvailable(false, false))
        assertTrue(WorkflowEditorOperation.RecordedClick.isAvailable(false, true))
        assertTrue(WorkflowEditorOperation.ReadNodeText.isAvailable(false, false))
        assertFalse(WorkflowEditorOperation.Repeat.isAvailable(false, true))
        assertFalse(WorkflowEditorOperation.VariableCondition.isAvailable(false, true))
        assertFalse(WorkflowEditorOperation.NodeCondition.isAvailable(false, true))
        assertTrue(WorkflowEditorOperation.NodeCondition.isAvailable(true, true))
        assertTrue(WorkflowEditorOperation.ImageClick.isAvailable(false, false))
        assertFalse(WorkflowEditorOperation.JumpIf.isAvailable(false, false, hasLabels = false))
        assertTrue(WorkflowEditorOperation.JumpIf.isAvailable(false, false, hasLabels = true))
    }

    @Test
    fun chooserExplainsUnavailableOperations() {
        assertEquals(
            WorkflowOperationUnavailableReason.AutomationServiceRequired,
            WorkflowEditorOperation.RecordedClick.unavailableReason(false, false),
        )
        listOf(
            WorkflowEditorOperation.Repeat,
            WorkflowEditorOperation.VariableCondition,
            WorkflowEditorOperation.NodeCondition,
        ).forEach { operation ->
            assertEquals(
                WorkflowOperationUnavailableReason.ExistingStepRequired,
                operation.unavailableReason(false, true),
            )
            assertEquals(null, operation.unavailableReason(true, true))
        }
        assertEquals(null, WorkflowEditorOperation.Tap.unavailableReason(false, false))
            assertEquals(
                WorkflowOperationUnavailableReason.LabelRequired,
                WorkflowEditorOperation.JumpIf.unavailableReason(false, true, hasLabels = false),
            )
    }
}