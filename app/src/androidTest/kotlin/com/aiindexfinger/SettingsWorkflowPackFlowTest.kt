package com.aiindexfinger

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiindexfinger.data.SettingsWorkflowPack
import com.aiindexfinger.data.ClockWorkflowPack
import com.aiindexfinger.data.FilesWorkflowPack
import com.aiindexfinger.data.WorkflowLibrary
import com.aiindexfinger.model.WorkflowState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsWorkflowPackFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun installActionCreatesThreeFoldersWithTwentyOneDraftWorkflowsIdempotently() {
        var latestLibrary = WorkflowLibrary()
        composeRule.setContent {
            var library by remember { mutableStateOf(latestLibrary) }
            latestLibrary = library
            MaterialTheme {
                WorkflowHome(
                    workflows = library.workflows,
                    folders = library.folders,
                    workflowFolderIds = library.workflowFolderIds,
                    onSaveFolder = { library = library.withFolder(it) },
                    onDeleteFolder = { library = library.withoutFolder(it) },
                    onMoveWorkflow = { workflowId, folderId ->
                        library = library.moveWorkflow(workflowId, folderId)
                    },
                    onInstallSettingsPack = {
                        listOf(
                            Triple(
                                SettingsWorkflowPack.definition,
                                "Settings",
                                listOf(
                                    "Settings home",
                                    "Settings list",
                                    "Settings search",
                                    "Open Network & internet",
                                    "Open Connected devices",
                                    "Open Apps settings",
                                    "Open Notifications settings",
                                    "Open Sound & vibration",
                                    "Open Modes",
                                    "Open Display & touch",
                                    "Open Storage",
                                    "Open Location settings",
                                    "Open Accessibility settings",
                                    "Open Accounts settings",
                                    "Open Languages settings",
                                ),
                            ),
                            Triple(ClockWorkflowPack.definition, "Clock", listOf("Open Clock", "Verify time", "Verify date")),
                            Triple(FilesWorkflowPack.definition, "Files", listOf("Open Files", "Verify header", "Verify list")),
                        ).forEach { (pack, folderName, names) ->
                            library = pack.install(library, folderName, names).library
                        }
                    },
                    runRecords = emptyList(),
                    schedules = emptyList(),
                    onCreate = {}, onEdit = {}, onImport = {}, onExportAll = {}, onExport = {},
                    onDuplicate = {}, onCompare = { _, _ -> }, onViewVersions = {}, onDelete = {},
                    onSchedule = { _, _, _ -> }, onCancelSchedule = {}, onClearRunHistory = {},
                    onViewRunHistory = {}, runningWorkflowId = null, runMessage = null, onRun = {},
                    onPreflight = {}, onStop = {}, onOpenAccessibilitySettings = {},
                    onReviewAccessibilityDisclosure = {},
                )
            }
        }

        composeRule.onNodeWithTag(SETTINGS_PACK_INSTALL_TAG).performScrollTo().performClick()
        composeRule.onNodeWithTag(folderFilterTag(SettingsWorkflowPack.FOLDER_ID))
            .performScrollTo().performClick().assertIsSelected()
        composeRule.onNodeWithText("Settings home").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Open Storage").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Open Accessibility settings").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Open Languages settings").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_PACK_INSTALL_TAG).performScrollTo()
        composeRule.onNodeWithTag(folderFilterTag(FilesWorkflowPack.FOLDER_ID))
            .performScrollTo().performClick().assertIsSelected()
        composeRule.onNodeWithText("Open Files").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_PACK_INSTALL_TAG).performScrollTo()
        composeRule.onNodeWithTag(folderFilterTag(ClockWorkflowPack.FOLDER_ID))
            .performScrollTo().performClick().assertIsSelected()
        composeRule.onNodeWithText("Open Clock").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag(SETTINGS_PACK_INSTALL_TAG).performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(21, latestLibrary.workflows.size)
            assertEquals(listOf(WorkflowState.Draft), latestLibrary.workflows.map { it.state }.distinct())
            assertEquals(3, latestLibrary.folders.size)
            assertEquals(21, latestLibrary.workflowFolderIds.size)
        }
    }
}