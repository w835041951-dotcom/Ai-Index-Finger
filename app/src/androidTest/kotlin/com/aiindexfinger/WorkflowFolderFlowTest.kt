package com.aiindexfinger

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiindexfinger.data.WorkflowLibrary
import com.aiindexfinger.model.Step
import com.aiindexfinger.model.Workflow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkflowFolderFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun createRenameMoveAndDeleteFolderFlow() {
        val workflow = Workflow(
            id = WORKFLOW_ID,
            name = WORKFLOW_NAME,
            steps = listOf(Step.Delay("step", 100)),
        )
        var latestLibrary = WorkflowLibrary(workflows = listOf(workflow))
        var folderId: String? = null
        composeRule.setContent {
            var library by remember { mutableStateOf(latestLibrary) }
            latestLibrary = library
            MaterialTheme {
                WorkflowHome(
                    workflows = library.workflows,
                    folders = library.folders,
                    workflowFolderIds = library.workflowFolderIds,
                    onSaveFolder = { folder ->
                        folderId = folder.id
                        library = library.withFolder(folder)
                    },
                    onDeleteFolder = { library = library.withoutFolder(it) },
                    onMoveWorkflow = { workflowId, destinationId ->
                        library = library.moveWorkflow(workflowId, destinationId)
                    },
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
                    onOpenTutorial = {},
                    runningWorkflowId = null,
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

        composeRule.onNodeWithTag(FOLDER_MANAGE_TAG).performScrollTo().performClick()
        composeRule.onNodeWithTag(FOLDER_CREATE_TAG).performClick()
        composeRule.onNodeWithTag(FOLDER_NAME_INPUT_TAG).performTextInput("Alpha")
        composeRule.onNodeWithTag(FOLDER_NAME_SAVE_TAG).performClick()

        composeRule.waitUntil { folderId != null }
        val createdFolderId = requireNotNull(folderId)
        composeRule.onNodeWithTag(folderFilterTag(createdFolderId))
            .assertIsDisplayed()
            .assertTextContains("Alpha", substring = true)

        composeRule.onNodeWithTag(FOLDER_MANAGE_TAG).performClick()
        composeRule.onNodeWithTag(folderRenameTag(createdFolderId)).performClick()
        val nameInput = composeRule.onNodeWithTag(FOLDER_NAME_INPUT_TAG)
        nameInput.assertTextContains("Alpha")
        nameInput.performTextClearance()
        nameInput.performTextInput("Beta")
        composeRule.onNodeWithTag(FOLDER_NAME_SAVE_TAG).performClick()

        composeRule.onNodeWithTag(folderFilterTag(createdFolderId)).assertTextContains("Beta", substring = true)
        composeRule.onNodeWithTag(folderMoveWorkflowTag(WORKFLOW_ID)).performScrollTo().performClick()
        composeRule.onNodeWithTag(FOLDER_DESTINATION_UNFILED_TAG).assertIsSelected()
        composeRule.onNodeWithTag(folderDestinationTag(createdFolderId)).performClick()

        composeRule.onNodeWithTag(folderFilterTag(createdFolderId))
            .assertTextContains("Beta", substring = true)
            .performClick()
            .assertIsSelected()
        composeRule.onNodeWithText(WORKFLOW_NAME).assertIsDisplayed()

        composeRule.onNodeWithTag(FOLDER_MANAGE_TAG).performScrollTo().performClick()
        composeRule.onNodeWithTag(folderDeleteTag(createdFolderId)).performClick()
        composeRule.onNodeWithTag(FOLDER_DELETE_CONFIRM_TAG).performClick()

        composeRule.onNodeWithTag(folderFilterTag(createdFolderId)).assertDoesNotExist()
        composeRule.onNodeWithTag(FOLDER_FILTER_UNFILED_TAG).performClick().assertIsSelected()
        composeRule.onNodeWithText(WORKFLOW_NAME).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(listOf(workflow), latestLibrary.workflows)
            assertEquals(emptyList<Any>(), latestLibrary.folders)
            assertNull(latestLibrary.folderIdFor(WORKFLOW_ID))
        }
    }

    private companion object {
        const val WORKFLOW_ID = "folder-flow-workflow"
        const val WORKFLOW_NAME = "Folder flow fixture"
    }
}