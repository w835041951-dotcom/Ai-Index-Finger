package com.aiindexfinger

import com.aiindexfinger.model.Value
import com.aiindexfinger.model.ScrollDirection
import com.aiindexfinger.model.NodeSelector
import com.aiindexfinger.model.RecordedClickTargetMode
import com.aiindexfinger.model.Step
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.WorkflowState
import com.aiindexfinger.data.WorkflowLibrary
import com.aiindexfinger.scheduler.ScheduleStorageException
import com.aiindexfinger.scheduler.WorkflowSchedule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import com.aiindexfinger.model.FailurePolicy
import com.aiindexfinger.automation.ScreenPoint
import com.aiindexfinger.automation.ScreenBounds
import com.aiindexfinger.automation.gesturePointsAreInsideDisplay
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationSettingsMappingTest {
    @Test
    fun `dynamic semantics tags preserve short IDs and bound imported long IDs`() {
        assertEquals("workflow-run-short", workflowRunTag("short"))
        assertEquals("step-short-edit", stepOperationTag("short", "edit"))
        assertEquals("floating-workflow-short", floatingWorkflowTag("short"))

        val longId = "界".repeat(1_000)
        val same = workflowRunTag(longId)
        assertEquals(same, workflowRunTag(longId))
        assertTrue(same.toByteArray(Charsets.UTF_8).size <= 256)
        assertTrue(stepOperationTag(longId, "delete").toByteArray(Charsets.UTF_8).size <= 256)
        assertTrue(floatingWorkflowTag(longId).toByteArray(Charsets.UTF_8).size <= 256)
        assertFalse(same.contains(longId))
        assertFalse(same == workflowRunTag(longId + "different"))
    }

    @Test
    fun `scheduled notification reflects current workflow availability`() {
        val ready = Workflow(
            id = "ready",
            name = "Ready",
            steps = listOf(Step.Delay("delay", 1)),
            state = WorkflowState.Ready,
        )
        val draft = ready.copy(id = "draft", name = "Draft", state = WorkflowState.Draft)

        assertEquals(
            ScheduledWorkflowAvailability.Ready,
            scheduledWorkflowAvailability(listOf(ready, draft), ready.id),
        )
        assertEquals(
            ScheduledWorkflowAvailability.NotReady,
            scheduledWorkflowAvailability(listOf(ready, draft), draft.id),
        )
        assertEquals(
            ScheduledWorkflowAvailability.Missing,
            scheduledWorkflowAvailability(listOf(ready, draft), "missing"),
        )
    }

    @Test
    fun `startup schedule loading falls back without hiding corruption or cancellation`() = runBlocking {
        val saved = listOf(WorkflowSchedule("workflow", "Workflow", 100))
        val fallback = loadSchedulesForStartup(
            reconcile = { throw IllegalStateException("work manager unavailable") },
            loadWithoutReconciliation = { saved },
        )
        val corrupt = loadSchedulesForStartup(
            reconcile = { throw ScheduleStorageException(IllegalStateException("corrupt")) },
            loadWithoutReconciliation = { error("must not load") },
        )

        assertEquals(ScheduleStartupLoad(saved, ScheduleStartupIssue.ReconciliationFailed), fallback)
        assertEquals(
            ScheduleStartupLoad(emptyList(), ScheduleStartupIssue.StorageCorrupt),
            corrupt,
        )
        assertTrue(
            runCatching {
                loadSchedulesForStartup(
                    reconcile = { throw CancellationException("cancelled") },
                    loadWithoutReconciliation = { saved },
                )
            }.exceptionOrNull() is CancellationException,
        )
    }

    @Test
    fun `captured screen bounds must match current display dimensions`() {
        val portrait = ScreenBounds(0, 0, 1_080, 2_400)

        assertTrue(captureBoundsMatchDisplay(portrait, 1_080, 2_400))
        assertFalse(captureBoundsMatchDisplay(portrait, 2_400, 1_080))
        assertFalse(captureBoundsMatchDisplay(portrait, 540, 1_200))
    }
    @Test
    fun `export filename preserves readable Chinese and removes path syntax`() {
        assertEquals(
            "每日闹钟_静音.aiflow.json",
            Workflow(id = "id", name = "每日闹钟 / 静音", steps = emptyList()).exportFileName(),
        )
        assertEquals(
            "private_name.aiflow.json",
            Workflow(id = "id", name = "../private\\name", steps = emptyList()).exportFileName(),
        )
        assertEquals(
            "workflow.aiflow.json",
            Workflow(id = "id", name = "../", steps = emptyList()).exportFileName(),
        )
    }
    @Test
    fun `exports resolve against canonical persisted workflows`() {
        val requested = Workflow(id = "id", name = "Old", steps = emptyList())
        val latest = requested.copy(name = "Latest")
        val canonical = WorkflowLibrary(workflows = listOf(latest))

        assertEquals(latest, canonicalWorkflowForExport(requested, canonical))
        assertNull(canonicalWorkflowForExport(requested, WorkflowLibrary()))
        assertEquals(requested, canonicalWorkflowForExport(requested, null))
        assertEquals(canonical, canonicalLibraryForExport(WorkflowLibrary(), canonical))
        assertEquals(canonical, canonicalLibraryForExport(canonical, null))
    }
    @Test
    fun `runtime gestures require every point inside current display bounds`() {
        val bounds = ScreenBounds(100, 200, 1_180, 2_600)

        assertTrue(gesturePointsAreInsideDisplay(bounds, ScreenPoint(100, 200)))
        assertTrue(
            gesturePointsAreInsideDisplay(
                bounds,
                ScreenPoint(100, 200),
                ScreenPoint(1_179, 2_599),
            ),
        )
        assertFalse(gesturePointsAreInsideDisplay(bounds, ScreenPoint(1_180, 2_599)))
        assertFalse(gesturePointsAreInsideDisplay(bounds, ScreenPoint(1_179, 2_600)))
    }
    @Test
    fun `scroll directions use matching visible labels`() {
        assertEquals(R.string.scroll_forward, scrollDirectionLabelRes(ScrollDirection.Forward))
        assertEquals(R.string.scroll_backward, scrollDirectionLabelRes(ScrollDirection.Backward))
    }

    @Test
    fun `coordinate actions default inside current display`() {
        assertEquals(ScreenPoint(360, 640), defaultTapCoordinate(720, 1_280))
        assertEquals(
            SwipeCoordinateDefaults(
                start = ScreenPoint(360, 960),
                end = ScreenPoint(360, 320),
            ),
            defaultSwipeCoordinates(720, 1_280),
        )
    }

    @Test
    fun `new coordinates stay inside display while unchanged imported values remain editable`() {
        val outside = ScreenPoint(900, 1_500)

        assertFalse(coordinateCanSave(outside, 720, 1_280, original = null))
        assertTrue(coordinateCanSave(outside, 720, 1_280, original = outside))
        assertFalse(coordinateCanSave(ScreenPoint(901, 1_500), 720, 1_280, original = outside))
        assertTrue(coordinateCanSave(ScreenPoint(719, 1_279), 720, 1_280, original = null))
    }

    @Test
    fun `rotationUpdatesOnlyUntouchedCoordinateDefaults`() {
        val portraitDefault = ScreenPoint(360, 640)
        val landscapeDefault = ScreenPoint(640, 360)

        assertEquals(
            landscapeDefault,
            updateAdaptiveCoordinateDefault(portraitDefault, portraitDefault, landscapeDefault),
        )
        assertEquals(
            ScreenPoint(100, 200),
            updateAdaptiveCoordinateDefault(
                ScreenPoint(100, 200),
                portraitDefault,
                landscapeDefault,
            ),
        )
    }

    @Test
    fun `delay editor accepts every nonnegative duration`() {
        assertTrue(delayDurationCanSave(0))
        assertTrue(delayDurationCanSave(1_000))
        assertFalse(delayDurationCanSave(-1))
        assertFalse(delayDurationCanSave(null))
    }

    @Test
    fun `recorded click control mode requires a repairable selector`() {
        val selector = NodeSelector("com.example", text = "Save")

        assertFalse(recordedClickCanSave(RecordedClickTargetMode.Control, null, coordinateValid = true))
        assertTrue(recordedClickCanSave(RecordedClickTargetMode.Control, selector, coordinateValid = true))
        assertTrue(recordedClickCanSave(RecordedClickTargetMode.Coordinates, null, coordinateValid = true))
        assertFalse(recordedClickCanSave(RecordedClickTargetMode.Coordinates, null, coordinateValid = false))
    }

    @Test
    fun `failure policy inputs enforce model ranges`() {
        assertTrue(stepTimeoutCanSave(""))
        assertTrue(stepTimeoutCanSave("1"))
        assertFalse(stepTimeoutCanSave("0"))
        assertFalse(stepTimeoutCanSave("value"))
        assertTrue(retryPolicyCanSave(attempts = 1, delayMillis = 0))
        assertTrue(retryPolicyCanSave(attempts = 10, delayMillis = 1_000))
        assertFalse(retryPolicyCanSave(attempts = 0, delayMillis = 0))
        assertFalse(retryPolicyCanSave(attempts = 11, delayMillis = 0))
        assertFalse(retryPolicyCanSave(attempts = 1, delayMillis = -1))
        assertFalse(retryPolicyCanSave(attempts = null, delayMillis = 0))
    }

    @Test
    fun `failure policy editor selects the persisted policy`() {
        assertEquals(FailurePolicyChoice.Stop, failurePolicyChoice(FailurePolicy.Stop))
        assertEquals(FailurePolicyChoice.Continue, failurePolicyChoice(FailurePolicy.Continue))
        assertEquals(
            FailurePolicyChoice.Retry,
            failurePolicyChoice(FailurePolicy.Retry(attempts = 2, delayMillis = 100)),
        )
    }

    @Test
    fun `warning text meets contrast requirements in both themes`() {
        assertTrue(contrastRatio(warningTextColor(false), Color.White) >= 4.5)
        assertTrue(contrastRatio(warningTextColor(true), Color(0xFF171D1A)) >= 4.5)
    }

    @Test
    fun `image match percentages round trip every supported permille value`() {
        for (permille in 0..1_000) {
            assertEquals(permille, imageMatchPercentToPermille(imageMatchPercentText(permille)))
        }
    }

    @Test
    fun `image match percentage accepts bounded values with one decimal place`() {
        assertEquals(0, imageMatchPercentToPermille(" 0 "))
        assertEquals(25, imageMatchPercentToPermille("2.5"))
        assertEquals(920, imageMatchPercentToPermille("92.0"))
        assertEquals(1_000, imageMatchPercentToPermille("100.0"))
    }

    @Test
    fun `image match percentage rejects invalid precision and range`() {
        listOf("", "-1", ".5", "92.25", "100.1", "101", "value").forEach { value ->
            assertNull(value, imageMatchPercentToPermille(value))
        }
    }

    @Test
    fun `optional launch action trims values and clears blanks`() {
        assertEquals(
            "android.settings.WIFI_SETTINGS",
            normalizedOptionalText("  android.settings.WIFI_SETTINGS  "),
        )
        assertNull(normalizedOptionalText("   "))
    }

    @Test
    fun `launch target validation distinguishes verified and runtime checked targets`() {
        assertEquals(
            LaunchTargetEditorStatus.MissingPackage,
            launchTargetEditorStatus(" ", "", isResolvable = false),
        )
        assertEquals(
            LaunchTargetEditorStatus.Resolvable,
            launchTargetEditorStatus("com.example.settings", "", isResolvable = true),
        )
        assertEquals(
            LaunchTargetEditorStatus.Unverified,
            launchTargetEditorStatus(
                "com.example.hidden",
                "example.OPEN",
                isResolvable = false,
            ),
        )
        assertEquals(
            LaunchTargetEditorStatus.Unavailable,
            launchTargetEditorStatus("com.example.missing", "", isResolvable = false),
        )
        assertEquals(
            LaunchTargetEditorStatus.PreservedUnavailable,
            launchTargetEditorStatus(
                "com.example.missing",
                "",
                initialPackageName = "com.example.missing",
                isResolvable = false,
            ),
        )
    }

    @Test
    fun `unchanged imported text is preserved and edited text is trimmed`() {
        assertEquals(" source ", preserveUnchangedOrTrim(" source ", " source "))
        assertEquals("updated", preserveUnchangedOrTrim(" updated ", " source "))
    }

    @Test
    fun `set variable values preserve their source type when reopened and saved`() {
        val values = listOf(
            Value.Literal("ready"),
            Value.Variable("source"),
            Value.Template("Order-${'$'}{orderId}"),
        )

        values.forEach { original ->
            assertEquals(
                original,
                variableValueOrNull(variableValueMode(original), variableValueText(original)),
            )
        }
    }

    @Test
    fun `variable reference trims names and rejects blanks`() {
        assertEquals(Value.Variable("source"), variableValueOrNull(VariableValueMode.Variable, " source "))
        assertNull(variableValueOrNull(VariableValueMode.Variable, "   "))
    }

    @Test
    fun `unchanged imported variable identifiers are preserved`() {
        val importedReference = Value.Variable(" source ")

        assertEquals(
            importedReference,
            variableValueOrNull(
                VariableValueMode.Variable,
                " source ",
                originalValue = importedReference,
            ),
        )
        assertEquals(
            Value.Variable("updated"),
            variableValueOrNull(
                VariableValueMode.Variable,
                " updated ",
                originalValue = importedReference,
            ),
        )
        assertEquals(" source ", preserveUnchangedOrTrim(" source ", " source "))
    }

    @Test
    fun `comparison operands preserve all value source combinations`() {
        val values = listOf(
            Value.Literal("ready"),
            Value.Variable("source"),
            Value.Template("Order-${'$'}{orderId}"),
        )

        values.forEach { left ->
            values.forEach { right ->
                assertEquals(
                    left,
                    variableValueOrNull(variableValueMode(left), variableValueText(left)),
                )
                assertEquals(
                    right,
                    variableValueOrNull(variableValueMode(right), variableValueText(right)),
                )
            }
        }
    }
}

private fun contrastRatio(first: Color, second: Color): Double {
    val lighter = maxOf(first.luminance(), second.luminance()).toDouble()
    val darker = minOf(first.luminance(), second.luminance()).toDouble()
    return (lighter + 0.05) / (darker + 0.05)
}