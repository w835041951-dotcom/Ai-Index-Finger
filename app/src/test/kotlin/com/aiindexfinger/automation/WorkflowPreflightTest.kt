package com.aiindexfinger.automation

import com.aiindexfinger.model.NodeSelector
import com.aiindexfinger.model.Step
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.WorkflowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowPreflightTest {
    private val selector = NodeSelector("com.example", text = "Item", matchIndex = 1)

    @Test
    fun reportSeparatesDraftValidationPermissionsAndRuntimeChecks() {
        val workflow = Workflow(
            id = "draft",
            name = "Draft",
            state = WorkflowState.Draft,
            steps = listOf(
                Step.LaunchApp("launch", "com.example"),
                Step.Click("click", selector),
            ),
        )

        val report = buildWorkflowPreflightReport(
            workflow = workflow,
            accessibilityConnected = true,
            notificationStatus = NotificationPreflightStatus.Denied,
            isLaunchable = { it == "com.example" },
            countMatches = { 2 },
        )

        assertEquals(WorkflowState.Draft, report.state)
        assertTrue(report.validationIssues.isEmpty())
        assertEquals(2, report.validation.definedStepCount)
        assertTrue(report.validation.definedVariables.isEmpty())
        assertEquals(NotificationPreflightStatus.Denied, report.notificationStatus)
        assertTrue(report.launchTargets.single().isLaunchable)
        assertEquals(2, report.selectors.single().matchCount)
        assertEquals(true, report.selectors.single().requiredMatchAvailable)
    }

    @Test
    fun disconnectedServiceDoesNotProbeOrReportZeroMatches() {
        var probeCount = 0
        val workflow = Workflow(
            id = "ready",
            name = "Ready",
            state = WorkflowState.Ready,
            steps = listOf(Step.Click("click", selector)),
        )

        val report = buildWorkflowPreflightReport(
            workflow = workflow,
            accessibilityConnected = false,
            notificationStatus = NotificationPreflightStatus.NotRequired,
            isLaunchable = { false },
            countMatches = {
                probeCount += 1
                0
            },
        )

        assertEquals(0, probeCount)
        assertNull(report.selectors.single().matchCount)
        assertNull(report.selectors.single().requiredMatchAvailable)
    }

    @Test
    fun selectorMatchIndexMustExistAndMissingLaunchTargetIsReported() {
        val workflow = Workflow(
            id = "ready",
            name = "Ready",
            state = WorkflowState.Ready,
            steps = listOf(
                Step.LaunchApp("launch", "com.missing"),
                Step.Click("click", selector),
            ),
        )

        val report = buildWorkflowPreflightReport(
            workflow = workflow,
            accessibilityConnected = true,
            notificationStatus = NotificationPreflightStatus.Granted,
            isLaunchable = { false },
            countMatches = { 1 },
        )

        assertFalse(report.launchTargets.single().isLaunchable)
        assertEquals(false, report.selectors.single().requiredMatchAvailable)
    }

    @Test
    fun recoveryActionsOnlyIncludeRequiredAutomationSetup() {
        val workflow = Workflow(
            id = "ready",
            name = "Ready",
            state = WorkflowState.Ready,
            steps = listOf(Step.Click("click", selector)),
        )

        val blocked = buildWorkflowPreflightReport(
            workflow = workflow,
            accessibilityConnected = false,
            notificationStatus = NotificationPreflightStatus.Denied,
            isLaunchable = { true },
            countMatches = { 0 },
        )
        val available = buildWorkflowPreflightReport(
            workflow = workflow,
            accessibilityConnected = true,
            notificationStatus = NotificationPreflightStatus.NotRequired,
            isLaunchable = { true },
            countMatches = { 1 },
        )

        assertEquals(
            listOf(PreflightRecoveryAction.SetUpAutomation),
            blocked.recoveryActions(),
        )
        assertTrue(available.recoveryActions().isEmpty())
    }

    @Test
    fun imageClickReportsUnsupportedScreenshotCapabilityIncludingNestedSteps() {
        val imageClick = Step.ImageClick("image", "com.example", "aGVsbG8=", 24, 24)
        val workflow = Workflow(
            id = "image",
            name = "Image",
            steps = listOf(Step.Repeat("repeat", 1, listOf(imageClick))),
        )

        val report = buildWorkflowPreflightReport(
            workflow = workflow,
            accessibilityConnected = true,
            notificationStatus = NotificationPreflightStatus.NotRequired,
            isLaunchable = { true },
            countMatches = { 0 },
            imageCaptureSupported = false,
        )

        assertTrue(report.requiresImageCapture)
        assertFalse(report.imageCaptureSupported)
    }
}