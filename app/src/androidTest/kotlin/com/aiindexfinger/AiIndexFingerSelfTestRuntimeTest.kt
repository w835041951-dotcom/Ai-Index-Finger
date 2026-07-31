package com.aiindexfinger

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aiindexfinger.automation.AutomationAccessibilityService
import com.aiindexfinger.data.AiIndexFingerSelfTestPack
import com.aiindexfinger.data.RunHistoryStore
import com.aiindexfinger.data.RunStatus
import com.aiindexfinger.data.WorkflowLibrary
import com.aiindexfinger.data.WorkflowStore
import com.aiindexfinger.executor.RunResult
import com.aiindexfinger.model.NodeSelector
import java.io.FileInputStream
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiIndexFingerSelfTestRuntimeTest {
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
    fun selfTestsVerifyWorkflowHomeObservationAndRuntimeDiagnostics() = runBlocking {
        val installed = AiIndexFingerSelfTestPack.definition.install(
            WorkflowLibrary(),
            "AI Index Finger",
            listOf("Verify workflow home", "Verify observation and runtime"),
        ).library
        WorkflowStore(context).saveLibrary(installed)
        shell("am start -W -n com.aiindexfinger/.MainActivity")

        val service = requireNotNull(AutomationAccessibilityService.instance)
        AiIndexFingerSelfTestPack.definition.workflowIds.forEach { workflowId ->
            val workflow = installed.workflows.single { it.id == workflowId }
            AutomationAccessibilityService.latestRun.value = null
            assertTrue(service.startWorkflow(workflow))
            val outcome = withTimeout(WORKFLOW_TIMEOUT_MILLIS) {
                AutomationAccessibilityService.latestRun
                    .filter { it?.record?.workflowId == workflow.id }
                    .first()
            }
            assertEquals(RunResult.Completed, outcome?.result)
        }

        assertTrue(
            service.countMatches(
                NodeSelector("com.aiindexfinger", text = AiIndexFingerSelfTestPack.HOME_MARKER_TEXT),
            ) > 0,
        )
        val records = RunHistoryStore(context).load()
        AiIndexFingerSelfTestPack.definition.workflowIds.forEach { workflowId ->
            assertEquals(RunStatus.Completed, records.single { it.workflowId == workflowId }.status)
        }
        val runtimeWorkflowId = AiIndexFingerSelfTestPack.VERIFY_OBSERVATION_RUNTIME_WORKFLOW_ID
        val runtimeDiagnostics = records.single { it.workflowId == runtimeWorkflowId }
            .diagnostics
            .sortedBy { it.sequence }
        assertEquals(
            listOf(
                "$runtimeWorkflowId-wait",
                "$runtimeWorkflowId-read",
                "$runtimeWorkflowId-if",
                "$runtimeWorkflowId-repeat",
                "$runtimeWorkflowId-repeat-wait",
                "$runtimeWorkflowId-repeat-wait",
            ),
            runtimeDiagnostics.map { it.stepId },
        )
        assertFalse(runtimeDiagnostics.any { it.stepId == "$runtimeWorkflowId-failure" })
        assertTrue(runtimeDiagnostics.all { it.outcome.name == "Completed" })
        assertEquals(installed, WorkflowStore(context).loadLibrary())
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
    }
}
