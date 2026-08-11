package com.aiindexfinger

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aiindexfinger.automation.AutomationAccessibilityService
import com.aiindexfinger.data.WorkflowLibrary
import com.aiindexfinger.data.WorkflowStore
import com.aiindexfinger.model.Step
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.WorkflowState
import java.io.FileInputStream
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FloatingWorkflowEditorCollapseTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val uiAutomation: UiAutomation = instrumentation.getUiAutomation(
        UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES,
    )
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var scenario: ActivityScenario<FloatingWorkflowEditorActivity>
    private lateinit var originalServices: String
    private lateinit var originalAccessibilityEnabled: String
    private var originalDisclosureAcknowledged: Boolean? = null

    @Before
    fun setUp() {
        originalServices = shell("settings get secure enabled_accessibility_services").trim()
        originalAccessibilityEnabled = shell("settings get secure accessibility_enabled").trim()
        val disclosurePreferences = context.getSharedPreferences("release_readiness", Context.MODE_PRIVATE)
        originalDisclosureAcknowledged = if (
            disclosurePreferences.contains("accessibility_disclosure_acknowledged")
        ) {
            disclosurePreferences.getBoolean("accessibility_disclosure_acknowledged", false)
        } else {
            null
        }
        disclosurePreferences.edit().remove("accessibility_disclosure_acknowledged").commit()
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
                        ),
                        state = WorkflowState.Draft,
                    ),
                ),
            ),
        )
        shell("settings put secure enabled_accessibility_services $SERVICE_COMPONENT")
        shell("settings put secure accessibility_enabled 1")
        runBlocking {
            withTimeout(SERVICE_TIMEOUT_MILLIS) {
                AutomationAccessibilityService.connected.filter { it }.first()
            }
        }
        context.packageManager.getLaunchIntentForPackage(CLOCK_PACKAGE)?.let { intent ->
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            instrumentation.waitForIdleSync()
        }
        scenario = ActivityScenario.launch(FloatingWorkflowEditorActivity.createIntent(context))
    }

    @After
    fun tearDown() {
        scenario.close()
        AutomationAccessibilityService.instance?.hideFloatingEditorRestoreControl()
        restoreSecureSetting("enabled_accessibility_services", originalServices)
        restoreSecureSetting("accessibility_enabled", originalAccessibilityEnabled)
        val disclosurePreferences = context.getSharedPreferences("release_readiness", Context.MODE_PRIVATE)
        val disclosureEditor = disclosurePreferences.edit()
        originalDisclosureAcknowledged?.let { acknowledged ->
            disclosureEditor.putBoolean("accessibility_disclosure_acknowledged", acknowledged)
        } ?: disclosureEditor.remove("accessibility_disclosure_acknowledged")
        disclosureEditor.commit()
        clearWorkflowFiles()
    }

    @Test
    fun collapsedEditorRestoresSameUnsavedSession() {
        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(WORKFLOW_NAME).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(floatingWorkflowTag(WORKFLOW_ID)).performClick()
        composeRule.onNodeWithTag(stepOperationTag("repeat", "duplicate")).performClick()
        composeRule.onAllNodesWithText("Repeat 2 times").assertCountEquals(2)

        composeRule.onNodeWithTag(FLOATING_EDITOR_COLLAPSE_TAG).performClick()
        runBlocking {
            withTimeout(UI_TIMEOUT_MILLIS) {
                AutomationAccessibilityService.floatingEditorRestoreVisible.filter { it }.first()
            }
        }
        assertTrue(FloatingWorkflowEditorActivity.hasCollapsedSession())

        assertTrue(requireNotNull(AutomationAccessibilityService.instance).restoreFloatingWorkflowEditor())
        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MILLIS) {
            !FloatingWorkflowEditorActivity.hasCollapsedSession() &&
                composeRule.onAllNodesWithText("Repeat 2 times").fetchSemanticsNodes().size == 2
        }
        assertFalse(AutomationAccessibilityService.floatingEditorRestoreVisible.value)
        composeRule.onAllNodesWithText("Repeat 2 times").assertCountEquals(2)
    }

    @Test
    fun disconnectedPreflightRequiresDisclosureAndDeclinePreservesSession() {
        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(WORKFLOW_NAME).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(floatingWorkflowTag(WORKFLOW_ID)).performClick()
        composeRule.onNodeWithTag(stepOperationTag("repeat", "duplicate")).performClick()
        composeRule.onAllNodesWithText("Repeat 2 times").assertCountEquals(2)

        shell("settings delete secure enabled_accessibility_services")
        shell("settings put secure accessibility_enabled 0")
        runBlocking {
            withTimeout(SERVICE_TIMEOUT_MILLIS) {
                AutomationAccessibilityService.connected.filter { !it }.first()
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.test_entire_workflow)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.set_up_automation)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.accessibility_disclosure_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.not_now)).performClick()

        composeRule.onAllNodesWithText("Repeat 2 times").assertCountEquals(2)
        assertFalse(
            context.getSharedPreferences("release_readiness", Context.MODE_PRIVATE)
                .getBoolean("accessibility_disclosure_acknowledged", false),
        )
    }

    private fun restoreSecureSetting(key: String, value: String) {
        if (value == "null" || value.isBlank()) {
            shell("settings delete secure $key")
        } else {
            shell("settings put secure $key $value")
        }
    }

    private fun shell(command: String): String {
        val descriptor: ParcelFileDescriptor = uiAutomation.executeShellCommand(command)
        return FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
    }

    private fun clearWorkflowFiles() {
        context.filesDir.resolve("workflows.json").delete()
        context.filesDir.resolve("workflows.backup.json").delete()
        context.filesDir.resolve("workflow-versions").deleteRecursively()
    }

    private companion object {
        const val SERVICE_COMPONENT =
            "com.aiindexfinger/com.aiindexfinger.automation.AutomationAccessibilityService"
        const val WORKFLOW_ID = "floating-collapse-workflow"
        const val WORKFLOW_NAME = "Floating collapse demo"
        const val CLOCK_PACKAGE = "com.google.android.deskclock"
        const val SERVICE_TIMEOUT_MILLIS = 15_000L
        const val UI_TIMEOUT_MILLIS = 10_000L
    }
}