package com.aiindexfinger.automation

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aiindexfinger.model.Step
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.WorkflowState
import java.io.FileInputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkflowDebuggerOverlayRuntimeTest {
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
        context.filesDir.resolve("run-history.json").delete()
        shell("settings put secure enabled_accessibility_services $SERVICE_COMPONENT")
        shell("settings put secure accessibility_enabled 1")
        runBlocking {
            awaitCondition { AutomationAccessibilityService.connected.value }
        }
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync {
            AutomationAccessibilityService.instance?.stopWorkflow()
        }
        restoreSecureSetting("enabled_accessibility_services", originalServices)
        restoreSecureSetting("accessibility_enabled", originalAccessibilityEnabled)
        context.filesDir.resolve("run-history.json").delete()
    }

    @Test
    fun debuggerOverlayFollowsStepThroughLifecycle() = runBlocking {
        val service = requireNotNull(AutomationAccessibilityService.instance)
        val workflow = Workflow(
            id = WORKFLOW_ID,
            name = "Floating debugger test",
            steps = listOf(
                Step.Delay("first", 500),
                Step.Delay("second", 1),
            ),
            state = WorkflowState.Ready,
        )
        AutomationAccessibilityService.latestRun.value = null

        var started = false
        instrumentation.runOnMainSync {
            started = service.startWorkflow(workflow, debug = true)
        }
        assertTrue(started)
        awaitCondition {
            AutomationAccessibilityService.debugPaused.value &&
                AutomationAccessibilityService.currentStepLocation.value
                    ?.segments?.lastOrNull()?.index == 0 &&
                service.isDebuggerOverlayVisible()
        }

        instrumentation.runOnMainSync { assertTrue(service.advanceWorkflow()) }
        awaitCondition { !service.isDebuggerOverlayVisible() }
        awaitCondition {
            AutomationAccessibilityService.debugPaused.value &&
                AutomationAccessibilityService.currentStepLocation.value
                    ?.segments?.lastOrNull()?.index == 1 &&
                service.isDebuggerOverlayVisible()
        }

        instrumentation.runOnMainSync { assertTrue(service.advanceWorkflow()) }
        awaitCondition {
            AutomationAccessibilityService.latestRun.value?.record?.workflowId == WORKFLOW_ID
        }
        assertFalse(service.isDebuggerOverlayVisible())
    }

    private suspend fun awaitCondition(predicate: () -> Boolean) {
        withTimeout(TIMEOUT_MILLIS) {
            while (!predicate()) delay(10)
        }
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
        const val WORKFLOW_ID = "floating-debugger-runtime"
        const val TIMEOUT_MILLIS = 15_000L
    }
}