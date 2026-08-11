package com.aiindexfinger

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aiindexfinger.automation.AutomationAccessibilityService
import com.aiindexfinger.data.ClockWorkflowPack
import com.aiindexfinger.executor.RunResult
import com.aiindexfinger.model.NodeAttribute
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
class ClockValidationWorkflowRuntimeTest {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val uiAutomation: UiAutomation = instrumentation.getUiAutomation(
        UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES,
    )
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var originalServices: String
    private lateinit var originalAccessibilityEnabled: String
    private lateinit var originalClockLocales: String

    @Before
    fun setUp() {
        originalServices = shell("settings get secure enabled_accessibility_services").trim()
        originalAccessibilityEnabled = shell("settings get secure accessibility_enabled").trim()
        originalClockLocales = clockLocales()
        shell("settings put secure enabled_accessibility_services $SERVICE_COMPONENT")
        shell("settings put secure accessibility_enabled 1")
        runBlocking {
            withTimeout(SERVICE_TIMEOUT_MILLIS) {
                AutomationAccessibilityService.connected.filter { it }.first()
            }
            removeValidationFixtureIfPresent()
        }
    }

    @After
    fun tearDown() {
        AutomationAccessibilityService.instance?.stopWorkflow()
        runBlocking { removeValidationFixtureIfPresent() }
        setClockLocales(originalClockLocales)
        restoreSecureSetting("enabled_accessibility_services", originalServices)
        restoreSecureSetting("accessibility_enabled", originalAccessibilityEnabled)
    }

    @Test
    fun validationWorkflowsSetTimeAndSilentSoundWithoutChangingOtherAlarms() = runBlocking {
        val workflows = ClockWorkflowPack.definition.install(
            com.aiindexfinger.data.WorkflowLibrary(),
            "Clock",
            List(ClockWorkflowPack.definition.workflowIds.size) { "Clock $it" },
        ).library.workflows.associateBy { it.id }
        listOf("" to "Silent", "zh-CN" to "静音").forEach { (locale, silentLabel) ->
            setClockLocales(locale)
            createAlarmFixture(ClockWorkflowPack.VALIDATION_ALARM_LABEL, hour = 4, minute = 37)
            openAlarmList()
            waitForMatch(validationLabelSelector, "validation fixture list label")
            createAlarmFixture(SENTINEL_ALARM_LABEL, hour = 5, minute = 49)
            val service = requireNotNull(AutomationAccessibilityService.instance)
            openAlarmList()
            waitForMatch(validationLabelSelector, "validation fixture after sentinel creation")
            waitForMatch(sentinelLabelSelector, "sentinel fixture list label")
            assertTrue(service.click(alarmSwitchSelector(ClockWorkflowPack.VALIDATION_ALARM_LABEL)))
            assertTrue(service.click(alarmSwitchSelector(SENTINEL_ALARM_LABEL)))
            openAlarmList()
            assertSentinelState()

            repeat(2) {
                runWorkflow(requireNotNull(workflows[ClockWorkflowPack.SET_VALIDATION_ALARM_TIME_WORKFLOW_ID]))
                assertFixtureState(expectedTime = "10:37", expectedRingtone = null)
                runWorkflow(requireNotNull(workflows[ClockWorkflowPack.SET_VALIDATION_ALARM_SOUND_WORKFLOW_ID]))
                assertFixtureState(expectedTime = "10:37", expectedRingtone = silentLabel)
            }

            assertSentinelState()
            removeFixtureIfPresent(validationLabelSelector)
            removeFixtureIfPresent(sentinelLabelSelector)
        }
    }

    private suspend fun runWorkflow(workflow: com.aiindexfinger.model.Workflow) {
        val service = requireNotNull(AutomationAccessibilityService.instance)
        AutomationAccessibilityService.latestRun.value = null
        assertTrue("Workflow ${workflow.id} did not start", service.startWorkflow(workflow))
        val outcome = withTimeout(WORKFLOW_TIMEOUT_MILLIS) {
            AutomationAccessibilityService.latestRun
                .filter { it?.record?.workflowId == workflow.id }
                .first()
        }
        assertEquals("Workflow ${workflow.id} failed", RunResult.Completed, outcome?.result)
    }

    private suspend fun assertFixtureState(expectedTime: String, expectedRingtone: String?) {
        val service = requireNotNull(AutomationAccessibilityService.instance)
        openAlarmList()
        assertEquals(1, service.countMatches(validationLabelSelector))
        assertTrue(service.click(validationLabelSelector))
        waitForMatch(validationDetailLabelSelector, "validation fixture detail")
        assertTrue(service.countMatches(alarmOffSelector) == 1)
        val time = service.readNodeAttribute(clockSelector, NodeAttribute.Text)
        assertTrue("Expected $expectedTime but was $time", time?.contains(expectedTime) == true)
        if (expectedRingtone != null) {
            val ringtone = service.readNodeAttribute(ringtoneSelector, NodeAttribute.Text)
            assertTrue("Expected $expectedRingtone but was $ringtone", ringtone?.contains(expectedRingtone) == true)
        }
        shell("input keyevent KEYCODE_BACK")
        waitForMatch(validationLabelSelector, "validation fixture after closing details")
        assertEquals(1, service.countMatches(validationLabelSelector))
    }

    private suspend fun assertSentinelState() {
        val service = requireNotNull(AutomationAccessibilityService.instance)
        openAlarmList()
        assertEquals(1, service.countMatches(sentinelLabelSelector))
        assertTrue(service.click(sentinelLabelSelector))
        waitForMatch(sentinelDetailLabelSelector, "sentinel fixture detail")
        assertEquals(1, service.countMatches(alarmOffSelector))
        val time = service.readNodeAttribute(clockSelector, NodeAttribute.Text)
        assertTrue("Expected sentinel 5:49 but was $time", time?.contains("5:49") == true)
        shell("input keyevent KEYCODE_BACK")
        waitForMatch(sentinelLabelSelector, "sentinel fixture after closing details")
    }

    private suspend fun waitForMatch(selector: NodeSelector, description: String = selector.viewId.orEmpty()) {
        val service = requireNotNull(AutomationAccessibilityService.instance)
        runCatching {
            withTimeout(UI_TIMEOUT_MILLIS) {
                while (service.countMatches(selector) == 0) kotlinx.coroutines.delay(100)
            }
        }.getOrElse {
            throw AssertionError(
                "Timed out waiting for $description; matches=${service.countMatches(selector)}",
                it,
            )
        }
    }

    private fun createAlarmFixture(label: String, hour: Int, minute: Int) {
        shell(
            "am start -W -a android.intent.action.SET_ALARM -p $CLOCK_PACKAGE " +
                "--ei android.intent.extra.alarm.HOUR $hour " +
                "--ei android.intent.extra.alarm.MINUTES $minute " +
                "--es android.intent.extra.alarm.MESSAGE $label " +
                "--ez android.intent.extra.alarm.SKIP_UI true",
        )
    }

    private suspend fun removeValidationFixtureIfPresent() {
        removeFixtureIfPresent(validationLabelSelector)
        removeFixtureIfPresent(sentinelLabelSelector)
    }

    private suspend fun removeFixtureIfPresent(labelSelector: NodeSelector) {
        openAlarmList()
        val service = AutomationAccessibilityService.instance ?: return
        repeat(2) {
            if (service.countMatches(labelSelector) == 0) return
            if (!service.click(labelSelector)) return
            waitForMatch(deleteSelector, "fixture delete button")
            service.click(deleteSelector)
            openAlarmList()
        }
    }

    private fun openAlarmList() {
        shell("am start -W -a android.intent.action.SHOW_ALARMS -p $CLOCK_PACKAGE")
    }

    private fun clockLocales(): String = shell(
        "cmd locale get-app-locales $CLOCK_PACKAGE --user 0",
    ).substringAfter('[').substringBefore(']')

    private fun setClockLocales(locales: String) {
        val argument = if (locales.isBlank()) "" else " --locales $locales"
        shell("cmd locale set-app-locales $CLOCK_PACKAGE --user 0$argument")
        shell("am force-stop $CLOCK_PACKAGE")
    }

    private fun restoreSecureSetting(key: String, value: String) {
        if (value == "null" || value.isBlank()) shell("settings delete secure $key")
        else shell("settings put secure $key $value")
    }

    private fun shell(command: String): String {
        val descriptor: ParcelFileDescriptor = uiAutomation.executeShellCommand(command)
        return FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
    }

    private companion object {
        const val CLOCK_PACKAGE = "com.google.android.deskclock"
        const val CLOCK_ID_PREFIX = "$CLOCK_PACKAGE:id/"
        const val SERVICE_COMPONENT =
            "com.aiindexfinger/com.aiindexfinger.automation.AutomationAccessibilityService"
        const val SERVICE_TIMEOUT_MILLIS = 15_000L
        const val UI_TIMEOUT_MILLIS = 30_000L
        const val WORKFLOW_TIMEOUT_MILLIS = 60_000L
        const val SENTINEL_ALARM_LABEL = "AI_INDEX_FINGER_CLOCK_SENTINEL_V1"
        val validationLabelSelector = NodeSelector(
            CLOCK_PACKAGE,
            viewId = "${CLOCK_ID_PREFIX}label",
            text = ClockWorkflowPack.VALIDATION_ALARM_LABEL,
        )
        val validationDetailLabelSelector = NodeSelector(
            CLOCK_PACKAGE,
            viewId = "${CLOCK_ID_PREFIX}alarm_label",
            text = ClockWorkflowPack.VALIDATION_ALARM_LABEL,
        )
        val sentinelLabelSelector = NodeSelector(
            CLOCK_PACKAGE,
            viewId = "${CLOCK_ID_PREFIX}label",
            text = SENTINEL_ALARM_LABEL,
        )
        val sentinelDetailLabelSelector = NodeSelector(
            CLOCK_PACKAGE,
            viewId = "${CLOCK_ID_PREFIX}alarm_label",
            text = SENTINEL_ALARM_LABEL,
        )
        fun alarmSwitchSelector(label: String) = NodeSelector(
            CLOCK_PACKAGE,
            viewId = "${CLOCK_ID_PREFIX}onoff",
            ancestor = com.aiindexfinger.model.AncestorSelector(
                contentDescription = label,
                contentDescriptionMatchMode = com.aiindexfinger.model.TextMatchMode.Contains,
            ),
        )
        val alarmOffSelector = NodeSelector(CLOCK_PACKAGE, viewId = "${CLOCK_ID_PREFIX}schedule_alarm_detail_1")
        val clockSelector = NodeSelector(CLOCK_PACKAGE, viewId = "${CLOCK_ID_PREFIX}clock")
        val ringtoneSelector = NodeSelector(CLOCK_PACKAGE, viewId = "${CLOCK_ID_PREFIX}alarm_ringtone")
        val deleteSelector = NodeSelector(CLOCK_PACKAGE, viewId = "${CLOCK_ID_PREFIX}delete_button")
    }
}