package com.aiindexfinger

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiindexfinger.model.Step
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.WorkflowState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkflowConcurrentRunUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun activeRunDisablesRunAndDebugForOtherWorkflow() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val running = readyWorkflow(RUNNING_ID, "Running workflow")
        val waiting = readyWorkflow(WAITING_ID, "Waiting workflow")

        composeRule.setContent {
            MaterialTheme {
                WorkflowHome(
                    workflows = listOf(running, waiting),
                    folders = emptyList(),
                    workflowFolderIds = emptyMap(),
                    onSaveFolder = {},
                    onDeleteFolder = {},
                    onMoveWorkflow = { _, _ -> },
                    onInstallSettingsPack = {},
                    runRecords = emptyList(),
                    runHistoryCorrupt = false,
                    schedules = emptyList(),
                    onCreate = {},
                    onEdit = {},
                    onImport = {},
                    importInProgress = false,
                    onExportAll = {},
                    onExport = {},
                    onDuplicate = {},
                    onCompare = { _, _ -> },
                    onViewVersions = {},
                    onDelete = {},
                    onSchedule = { _, _, _ -> },
                    onCancelSchedule = {},
                    onClearRunHistory = {},
                    onViewRunHistory = {},
                    onOpenSettings = {},
                    runningWorkflowId = RUNNING_ID,
                    runMessage = null,
                    onRun = {},
                    onDebug = {},
                    onPreflight = {},
                    onStop = {},
                    onOpenAccessibilitySettings = {},
                    onReviewAccessibilityDisclosure = {},
                )
            }
        }

        composeRule.onNodeWithTag(workflowRunTag(RUNNING_ID))
            .performScrollTo()
            .assertIsEnabled()
            .assertTextContains(context.getString(R.string.stop))
        composeRule.onNodeWithTag(workflowRunTag(WAITING_ID))
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(workflowDebugTag(WAITING_ID)).assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.another_workflow_running))
            .performScrollTo()
            .assertExists()
    }

    private fun readyWorkflow(id: String, name: String) = Workflow(
        id = id,
        name = name,
        steps = listOf(Step.Delay("$id-step", 100)),
        state = WorkflowState.Ready,
    )

    private companion object {
        const val RUNNING_ID = "running-workflow"
        const val WAITING_ID = "waiting-workflow"
    }
}