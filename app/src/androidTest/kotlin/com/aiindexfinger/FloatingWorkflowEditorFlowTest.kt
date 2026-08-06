package com.aiindexfinger

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.Gravity
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiindexfinger.data.WorkflowLibrary
import com.aiindexfinger.data.WorkflowStore
import com.aiindexfinger.model.Step
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.WorkflowState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class FloatingWorkflowEditorFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var scenario: ActivityScenario<FloatingWorkflowEditorActivity>

    @Before
    fun setUp() {
        clearWorkflowFiles()
        WorkflowStore(context).saveLibrary(
            WorkflowLibrary(
                workflows = listOf(
                    Workflow(
                        id = WORKFLOW_ID,
                        name = WORKFLOW_NAME,
                        steps = listOf(
                            Step.Repeat(
                                id = "repeat",
                                times = 2,
                                steps = listOf(Step.Delay("delay", 100)),
                            ),
                            Step.Delay("root-delay", 200),
                        ),
                        state = WorkflowState.Draft,
                    ),
                ),
            ),
        )
        context.packageManager.getLaunchIntentForPackage(CLOCK_PACKAGE)?.let { intent ->
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
        scenario = ActivityScenario.launch(FloatingWorkflowEditorActivity.createIntent(context))
    }

    @After
    fun tearDown() {
        scenario.close()
        clearWorkflowFiles()
    }

    @Test
    fun existingNestedWorkflowAndCompleteOperationSurfaceAreAvailable() {
        composeRule.waitUntil { composeRule.onAllNodesWithText(WORKFLOW_NAME).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag(floatingWorkflowTag(WORKFLOW_ID)).assertIsDisplayed().performClick()

        composeRule.onNodeWithText("Repeat 2 times").assertIsDisplayed()
        composeRule.onNodeWithTag(stepOperationTag("repeat", "down")).assertIsEnabled()
        composeRule.onNodeWithTag(stepOperationTag("repeat", "settings")).performClick()
        composeRule.onNodeWithText("Step settings").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Open steps").performClick()
        composeRule.onNodeWithText("Wait 100 ms").assertIsDisplayed()
        composeRule.onNodeWithText("Up one level").performClick()
        composeRule.onNodeWithTag(stepOperationTag("repeat", "duplicate")).performClick()
        composeRule.onAllNodesWithText("Repeat 2 times").assertCountEquals(2)
        composeRule.onNodeWithTag(stepOperationTag("root-delay", "delete")).performScrollTo().performClick()
        composeRule.onNodeWithText("Delete step?").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag(WORKFLOW_EDITOR_ALL_ACTIONS_TAG).performScrollTo().assertIsDisplayed()

        listOf(
            "Launch app",
            "Click",
            "Click by screenshot",
            "Record clicks over app",
            "Long click",
            "Tap coordinates",
            "Scroll element",
            "Input text",
            "Swipe",
            "Wait",
            "Home",
            "Recents",
            "Wait for element",
            "Set variable",
            "Read element attribute",
            "Repeat steps",
            "Value comparison",
            "Element exists condition",
        ).forEach { label ->
            composeRule.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }
        composeRule.onAllNodesWithText("Back")[1].performScrollTo().assertIsDisplayed()
    }

    @Test
    fun newWorkflowCanBeCreatedAndSavedAsDraft() {
        composeRule.waitUntil {
            composeRule.onAllNodesWithTag(FLOATING_EDITOR_NEW_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(FLOATING_EDITOR_NEW_TAG).performClick()
        val nameField = composeRule.onNodeWithTag(WORKFLOW_NAME_INPUT_TAG).assertIsDisplayed()
        nameField.performTextClearance()
        composeRule.onNodeWithTag(WORKFLOW_NAME_INPUT_TAG).performTextInput("Created over app")
        composeRule.onNodeWithText("Save draft").performClick()

        composeRule.waitUntil {
            WorkflowStore(context).loadLibrary().workflows.any { it.name == "Created over app" }
        }
        assertTrue(WorkflowStore(context).loadLibrary().workflows.any { it.name == "Created over app" })
    }

    @Test
    fun editorWindowLeavesTheTargetAppVisibleAroundIt() {
        scenario.onActivity { activity ->
            val metrics = activity.resources.displayMetrics
            val attributes = activity.window.attributes

            assertTrue(attributes.width in 1 until metrics.widthPixels)
            assertTrue(attributes.height in 1 until metrics.heightPixels)
            assertEquals(Gravity.END or Gravity.CENTER_VERTICAL, attributes.gravity)
            assertTrue(attributes.dimAmount > 0f)
        }

        val screenshot = requireNotNull(
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot(),
        )
        assertNotEquals(screenshot.getPixel(4, screenshot.height / 2), screenshot.getPixel(screenshot.width / 2, screenshot.height / 2))
        screenshot.recycle()
    }

    @Test
    fun staleFloatingEditorCannotOverwriteNewerSavedWorkflow() {
        composeRule.waitUntil { composeRule.onAllNodesWithText(WORKFLOW_NAME).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag(floatingWorkflowTag(WORKFLOW_ID)).performClick()

        val baseline = WorkflowStore(context).loadLibrary().workflows.single { it.id == WORKFLOW_ID }
        val newer = baseline.copy(name = "Saved elsewhere")
        runBlocking {
            (context.applicationContext as AiIndexFingerApplication).commitWorkflow(baseline, newer)
        }

        composeRule.onNodeWithText("Save draft").performClick()
        composeRule.waitUntil {
            composeRule.onAllNodesWithText(
                context.getString(R.string.workflow_edit_conflict),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(context.getString(R.string.workflow_edit_conflict)).assertIsDisplayed()
        assertEquals(
            "Saved elsewhere",
            WorkflowStore(context).loadLibrary().workflows.single { it.id == WORKFLOW_ID }.name,
        )
    }

    @Test
    fun unsavedEditingSessionSurvivesRotation() {
        composeRule.waitUntil { composeRule.onAllNodesWithText(WORKFLOW_NAME).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag(floatingWorkflowTag(WORKFLOW_ID)).performClick()
        composeRule.onNodeWithTag(stepOperationTag("repeat", "duplicate")).performClick()
        composeRule.onAllNodesWithText("Repeat 2 times").assertCountEquals(2)

        scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        scenario.onActivity { activity ->
            val metrics = activity.resources.displayMetrics
            val attributes = activity.window.attributes
            assertTrue(attributes.width in 1 until metrics.widthPixels)
            assertTrue(attributes.height in 1 until metrics.heightPixels)
            assertTrue(attributes.width >= (metrics.widthPixels * 0.85f).toInt())
            assertTrue(attributes.height >= (metrics.heightPixels * 0.82f).toInt())
        }
        scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        composeRule.onAllNodesWithText("Repeat 2 times").assertCountEquals(2)
    }

    private fun clearWorkflowFiles() {
        context.filesDir.resolve("workflows.json").delete()
        context.filesDir.resolve("workflows.backup.json").delete()
        context.filesDir.resolve("workflow-versions").deleteRecursively()
    }

    private companion object {
        const val WORKFLOW_ID = "floating-workflow"
        const val WORKFLOW_NAME = "Floating demo"
        const val CLOCK_PACKAGE = "com.google.android.deskclock"
    }
}
