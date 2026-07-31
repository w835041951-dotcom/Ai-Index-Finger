package com.aiindexfinger

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aiindexfinger.automation.AutomationAccessibilityService
import com.aiindexfinger.data.RunHistoryStore
import com.aiindexfinger.data.RunStatus
import com.aiindexfinger.data.SettingsWorkflowPack
import com.aiindexfinger.data.ClockWorkflowPack
import com.aiindexfinger.data.FilesWorkflowPack
import com.aiindexfinger.data.WorkflowLibrary
import com.aiindexfinger.data.WorkflowStore
import com.aiindexfinger.executor.RunResult
import com.aiindexfinger.model.NodeSelector
import com.aiindexfinger.model.WorkflowState
import com.aiindexfinger.model.Step
import com.aiindexfinger.model.TextMatchMode
import java.io.FileInputStream
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsWorkflowRuntimeTest {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val uiAutomation: UiAutomation = instrumentation.getUiAutomation(
        UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES,
    )
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var originalServices: String
    private lateinit var originalAccessibilityEnabled: String

    @Before
    fun setUp() {
        originalServices = shell("settings get secure enabled_accessibility_services").trim()
        originalAccessibilityEnabled = shell("settings get secure accessibility_enabled").trim()
        context.filesDir.resolve("workflows.json").delete()
        context.filesDir.resolve("workflows.backup.json").delete()
        context.filesDir.resolve("run-history.json").delete()
        shell("settings put secure enabled_accessibility_services $SERVICE_COMPONENT")
        shell("settings put secure accessibility_enabled 1")
        runBlocking {
            withTimeout(SERVICE_TIMEOUT_MILLIS) {
                AutomationAccessibilityService.connected.filter { it }.first()
            }
        }
    }

    @After
    fun tearDown() {
        AutomationAccessibilityService.instance?.stopWorkflow()
        restoreSecureSetting("enabled_accessibility_services", originalServices)
        restoreSecureSetting("accessibility_enabled", originalAccessibilityEnabled)
        context.filesDir.resolve("workflows.json").delete()
        context.filesDir.resolve("workflows.backup.json").delete()
        context.filesDir.resolve("run-history.json").delete()
    }

    @Test
    fun installedSystemAppWorkflowsCompleteThroughAccessibilityService() = runBlocking {
        val store = WorkflowStore(context)
        var installed = WorkflowLibrary()
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
            installed = pack.install(installed, folderName, names).library
        }
        store.saveLibrary(installed)

        assertEquals(
            setOf(SettingsWorkflowPack.FOLDER_ID, ClockWorkflowPack.FOLDER_ID, FilesWorkflowPack.FOLDER_ID),
            installed.folders.map { it.id }.toSet(),
        )
        assertTrue(installed.workflows.all { it.state == WorkflowState.Ready })
        installed.workflows.forEach { workflow ->
            val launchPackageName = workflow.steps.filterIsInstance<Step.LaunchApp>().single().packageName
            val terminalPackageName = workflow.steps.filterIsInstance<Step.WaitForNode>().last().selector.packageName
            shell("am force-stop $launchPackageName")
            AutomationAccessibilityService.latestRun.value = null
            val service = requireNotNull(AutomationAccessibilityService.instance)
            assertTrue("Workflow ${workflow.id} did not start", service.startWorkflow(workflow))
            val outcome = withTimeout(WORKFLOW_TIMEOUT_MILLIS) {
                AutomationAccessibilityService.latestRun
                    .filter { it?.record?.workflowId == workflow.id }
                    .first()
            }
            assertEquals("Workflow ${workflow.id} failed", RunResult.Completed, outcome?.result)
            assertTrue(shell("dumpsys window").contains(terminalPackageName))
            destinationSelectorByWorkflowId[workflow.id]?.let { destinationSelector ->
                assertTrue(
                    "Workflow ${workflow.id} did not reach its destination",
                    service.countMatches(destinationSelector) > 0,
                )
            }
        }

        val records = RunHistoryStore(context).load()
        assertEquals(21, records.count { it.workflowId in installed.workflows.map { workflow -> workflow.id } })
        assertTrue(records.all { it.status == RunStatus.Completed })
        assertEquals(installed, store.loadLibrary())
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

    private companion object {
        const val SERVICE_COMPONENT =
            "com.aiindexfinger/com.aiindexfinger.automation.AutomationAccessibilityService"
        const val SERVICE_TIMEOUT_MILLIS = 15_000L
        const val WORKFLOW_TIMEOUT_MILLIS = 30_000L
        val destinationSelectorByWorkflowId = mapOf(
            SettingsWorkflowPack.OPEN_NETWORK_WORKFLOW_ID to NodeSelector("com.android.settings", text = "Internet"),
            SettingsWorkflowPack.OPEN_CONNECTED_DEVICES_WORKFLOW_ID to
                NodeSelector("com.android.settings", text = "Pair new device"),
            SettingsWorkflowPack.OPEN_APPS_WORKFLOW_ID to NodeSelector(
                "com.android.settings",
                text = "See all",
                textMatchMode = TextMatchMode.Contains,
            ),
            SettingsWorkflowPack.OPEN_NOTIFICATIONS_WORKFLOW_ID to
                NodeSelector("com.android.settings", text = "App notifications"),
            SettingsWorkflowPack.OPEN_SOUND_WORKFLOW_ID to
                NodeSelector("com.android.settings", text = "Media volume"),
            SettingsWorkflowPack.OPEN_MODES_WORKFLOW_ID to
                NodeSelector("com.android.settings", text = "Do Not Disturb"),
            SettingsWorkflowPack.OPEN_DISPLAY_WORKFLOW_ID to
                NodeSelector("com.android.settings", text = "Brightness level"),
            SettingsWorkflowPack.OPEN_STORAGE_WORKFLOW_ID to
                NodeSelector("com.android.settings", text = "Free up space"),
            SettingsWorkflowPack.OPEN_LOCATION_WORKFLOW_ID to
                NodeSelector("com.android.settings", text = "Use location"),
            SettingsWorkflowPack.OPEN_ACCESSIBILITY_WORKFLOW_ID to
                NodeSelector("com.android.settings", text = "TalkBack"),
            SettingsWorkflowPack.OPEN_ACCOUNTS_WORKFLOW_ID to
                NodeSelector("com.android.settings", text = "Add account"),
            SettingsWorkflowPack.OPEN_LANGUAGES_WORKFLOW_ID to
                NodeSelector("com.android.settings", text = "Add a language"),
        )
    }
}