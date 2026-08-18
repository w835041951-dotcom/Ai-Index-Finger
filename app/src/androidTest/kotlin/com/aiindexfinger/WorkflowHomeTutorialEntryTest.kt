package com.aiindexfinger

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkflowHomeTutorialEntryTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyWorkflowStateShowsTutorialActionAndInvokesCallback() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val tutorialLabel = context.getString(R.string.tutorial_action)
        var tutorialClicks = 0

        composeRule.setContent {
            MaterialTheme {
                WorkflowHome(
                    workflows = emptyList(),
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
                    onOpenTutorial = { tutorialClicks += 1 },
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

        composeRule.onNodeWithText(tutorialLabel).assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(1, tutorialClicks)
        }
    }
}
