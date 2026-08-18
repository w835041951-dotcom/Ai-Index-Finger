package com.aiindexfinger.automation

import com.aiindexfinger.model.NodeSelector
import com.aiindexfinger.model.Step
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.WorkflowState
import com.aiindexfinger.model.RecordedBounds
import com.aiindexfinger.model.RecordedClickTargetMode
import com.aiindexfinger.model.RecordedControl
import com.aiindexfinger.scheduler.ScheduleNotificationReadiness
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
            notificationStatus = ScheduleNotificationReadiness.RuntimePermissionRequired,
            isLaunchable = { packageName, _ -> packageName == "com.example" },
            countMatches = { 2 },
        )

        assertEquals(WorkflowState.Draft, report.state)
        assertTrue(report.validationIssues.isEmpty())
        assertEquals(2, report.validation.definedStepCount)
        assertTrue(report.validation.definedVariables.isEmpty())
        assertEquals(ScheduleNotificationReadiness.RuntimePermissionRequired, report.notificationStatus)
        assertEquals(LaunchTargetStatus.Available, report.launchTargets.single().status)
        assertNull(report.selectors.single().matchCount)
        assertNull(report.selectors.single().requiredMatchAvailable)
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
            notificationStatus = ScheduleNotificationReadiness.Ready,
            isLaunchable = { _, _ -> false },
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
    fun launchDependentActiveWindowSelectorIsDeferredToRuntime() {
        var probeCount = 0
        val workflow = Workflow(
            id = "handoff",
            name = "Handoff",
            steps = listOf(
                Step.LaunchApp("launch", "com.example"),
                Step.Click("click", NodeSelector("", text = "Continue")),
            ),
        )

        val report = buildWorkflowPreflightReport(
            workflow = workflow,
            accessibilityConnected = true,
            notificationStatus = ScheduleNotificationReadiness.Ready,
            isLaunchable = { _, _ -> true },
            countMatches = { probeCount++; 1 },
        )

        assertEquals(0, probeCount)
        assertNull(report.selectors.single().matchCount)
        assertNull(report.selectors.single().requiredMatchAvailable)
    }

    @Test
    fun selectorForGuaranteedLaunchPackageIsDeferredButOtherPackageIsProbed() {
        var probes = 0
        val workflow = Workflow(
            id = "package-handoff",
            name = "Package handoff",
            steps = listOf(
                Step.LaunchApp("launch", "com.example"),
                Step.Click("same", NodeSelector("com.example", text = "Continue")),
                Step.Click("other", NodeSelector("com.other", text = "Continue")),
            ),
        )

        val report = buildWorkflowPreflightReport(
            workflow = workflow,
            accessibilityConnected = true,
            notificationStatus = ScheduleNotificationReadiness.Ready,
            isLaunchable = { _, _ -> true },
            countMatches = { probes++; 1 },
        )

        assertEquals(1, probes)
        assertNull(report.selectors.first { it.use.stepId == "same" }.matchCount)
        assertEquals(1, report.selectors.first { it.use.stepId == "other" }.matchCount)
    }

    @Test
    fun continuedLaunchDoesNotGuaranteeTheTargetWindow() {
        var directProbes = 0
        var nestedProbes = 0
        val activeWindowClick = Step.Click("click", NodeSelector("", text = "Continue"))
        val direct = Workflow(
            id = "continued-launch",
            name = "Continued launch",
            steps = listOf(
                Step.LaunchApp(
                    "launch",
                    "com.example",
                    failurePolicy = com.aiindexfinger.model.FailurePolicy.Continue,
                ),
                activeWindowClick,
            ),
        )
        val nested = Workflow(
            id = "continued-container",
            name = "Continued container",
            steps = listOf(
                Step.Repeat(
                    "repeat",
                    times = 1,
                    steps = listOf(Step.LaunchApp("launch", "com.example")),
                    failurePolicy = com.aiindexfinger.model.FailurePolicy.Continue,
                ),
                activeWindowClick,
            ),
        )

        val directReport = buildWorkflowPreflightReport(
            workflow = direct,
            accessibilityConnected = true,
            notificationStatus = ScheduleNotificationReadiness.Ready,
            isLaunchable = { _, _ -> true },
            countMatches = { directProbes++; 1 },
        )
        val nestedReport = buildWorkflowPreflightReport(
            workflow = nested,
            accessibilityConnected = true,
            notificationStatus = ScheduleNotificationReadiness.Ready,
            isLaunchable = { _, _ -> true },
            countMatches = { nestedProbes++; 1 },
        )

        assertEquals(1, directProbes)
        assertEquals(1, nestedProbes)
        assertEquals(1, directReport.selectors.single().matchCount)
        assertEquals(1, nestedReport.selectors.single().matchCount)
    }

    @Test
    fun activeWindowSelectorBeforeLaunchIsStillProbed() {
        var probeCount = 0
        val workflow = Workflow(
            id = "before-handoff",
            name = "Before handoff",
            steps = listOf(
                Step.Click("click", NodeSelector("", text = "Continue")),
                Step.LaunchApp("launch", "com.example"),
            ),
        )

        val report = buildWorkflowPreflightReport(
            workflow = workflow,
            accessibilityConnected = true,
            notificationStatus = ScheduleNotificationReadiness.Ready,
            isLaunchable = { _, _ -> true },
            countMatches = { probeCount++; 1 },
        )

        assertEquals(1, probeCount)
        assertEquals(1, report.selectors.single().matchCount)
    }

    @Test
    fun launchInOnlyOneBranchDoesNotDeferFollowingSelector() {
        var probeCount = 0
        val workflow = Workflow(
            id = "conditional-handoff",
            name = "Conditional handoff",
            steps = listOf(
                Step.IfElse(
                    id = "branch",
                    condition = com.aiindexfinger.model.Condition.Equals(
                        com.aiindexfinger.model.Value.Literal("a"),
                        com.aiindexfinger.model.Value.Literal("a"),
                    ),
                    whenTrue = listOf(Step.LaunchApp("launch", "com.example")),
                ),
                Step.Click("click", NodeSelector("", text = "Continue")),
            ),
        )

        val report = buildWorkflowPreflightReport(
            workflow = workflow,
            accessibilityConnected = true,
            notificationStatus = ScheduleNotificationReadiness.Ready,
            isLaunchable = { _, _ -> true },
            countMatches = { probeCount++; 1 },
        )

        assertEquals(1, probeCount)
        assertEquals(1, report.selectors.single().matchCount)
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
            notificationStatus = ScheduleNotificationReadiness.Ready,
            isLaunchable = { _, _ -> false },
            countMatches = { 1 },
        )

        assertEquals(LaunchTargetStatus.Unavailable, report.launchTargets.single().status)
        assertEquals(false, report.selectors.single().requiredMatchAvailable)
    }

    @Test
    fun selectorPreflightRespectsActionSemantics() {
        val coordinateClick = Step.RecordedClick(
            id = "recorded",
            x = 10,
            y = 20,
            selector = selector,
            control = RecordedControl(
                packageName = "com.example",
                bounds = RecordedBounds(0, 0, 20, 40),
                clickable = true,
                enabled = true,
                longClickable = false,
                scrollable = false,
            ),
            targetMode = RecordedClickTargetMode.Coordinates,
        )
        val workflow = Workflow(
            id = "semantics",
            name = "Semantics",
            steps = listOf(
                coordinateClick,
                Step.WaitForNode("disappear", selector, mustExist = false),
                Step.IfElse(
                    "condition",
                    com.aiindexfinger.model.Condition.NodeExists(selector),
                    whenTrue = emptyList(),
                ),
            ),
        )

        val report = buildWorkflowPreflightReport(
            workflow = workflow,
            accessibilityConnected = true,
            notificationStatus = ScheduleNotificationReadiness.Ready,
            isLaunchable = { _, _ -> true },
            countMatches = { 0 },
        )

        assertEquals(2, report.selectors.size)
        assertEquals(SelectorPreflightExpectation.RequiredAbsent, report.selectors[0].expectation)
        assertEquals(true, report.selectors[0].requirementSatisfied)
        assertEquals(SelectorPreflightExpectation.ObserveOnly, report.selectors[1].expectation)
        assertEquals(null, report.selectors[1].requirementSatisfied)
    }

    @Test
    fun coordinatePreflightFindsNestedDeviceSpecificOutOfBoundsActions() {
        val control = RecordedControl(
            packageName = "com.example",
            bounds = RecordedBounds(0, 0, 10, 10),
            clickable = true,
            enabled = true,
            longClickable = false,
            scrollable = false,
        )
        val workflow = Workflow(
            id = "coordinates",
            name = "Coordinates",
            steps = listOf(
                Step.Tap("valid", 99, 199),
                Step.Repeat(
                    "repeat",
                    1,
                    listOf(
                        Step.Tap("tap-outside", 100, 199),
                        Step.Swipe("swipe-outside", 0, 0, 99, 200),
                        Step.RecordedClick(
                            "recorded-outside",
                            100,
                            200,
                            control = control,
                            targetMode = RecordedClickTargetMode.Coordinates,
                        ),
                    ),
                ),
            ),
        )

        val report = buildWorkflowPreflightReport(
            workflow = workflow,
            accessibilityConnected = true,
            notificationStatus = ScheduleNotificationReadiness.Ready,
            isLaunchable = { _, _ -> true },
            countMatches = { 0 },
            displayWidth = 100,
            displayHeight = 200,
        )

        assertEquals(
            listOf("tap-outside", "swipe-outside", "recorded-outside"),
            report.coordinateIssues.map(CoordinatePreflightIssue::stepId),
        )
        assertTrue(report.coordinateIssues.all { it.displayWidth == 100 && it.displayHeight == 200 })
    }

    @Test
    fun imageTemplatePreflightFindsNestedInvalidSavedTemplate() {
        val valid = Step.ImageClick("valid-image", "com.example", "aGVsbG8=", 24, 24)
        val invalid = Step.ImageClick("invalid-image", "com.example", "aGVsbG8=", 24, 24)
        val workflow = Workflow(
            id = "images",
            name = "Images",
            steps = listOf(Step.Repeat("repeat", 1, listOf(valid, invalid))),
        )

        val report = buildWorkflowPreflightReport(
            workflow = workflow,
            accessibilityConnected = true,
            notificationStatus = ScheduleNotificationReadiness.Ready,
            isLaunchable = { _, _ -> true },
            countMatches = { 0 },
            isImageTemplateValid = { it.id == valid.id },
        )

        assertEquals(listOf("invalid-image"), report.imageTemplateIssues.map(ImageTemplatePreflightIssue::stepId))
    }

    @Test
    fun recoveryActionsIncludeEachRequiredSystemSetup() {
        val workflow = Workflow(
            id = "ready",
            name = "Ready",
            state = WorkflowState.Ready,
            steps = listOf(Step.Click("click", selector)),
        )

        val blocked = buildWorkflowPreflightReport(
            workflow = workflow,
            accessibilityConnected = false,
            notificationStatus = ScheduleNotificationReadiness.ChannelDisabled,
            isLaunchable = { _, _ -> true },
            countMatches = { 0 },
        )
        val available = buildWorkflowPreflightReport(
            workflow = workflow,
            accessibilityConnected = true,
            notificationStatus = ScheduleNotificationReadiness.Ready,
            isLaunchable = { _, _ -> true },
            countMatches = { 1 },
        )

        assertEquals(
            listOf(
                PreflightRecoveryAction.SetUpAutomation,
                PreflightRecoveryAction.OpenNotificationSettings,
            ),
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
            notificationStatus = ScheduleNotificationReadiness.Ready,
            isLaunchable = { _, _ -> true },
            countMatches = { 0 },
            imageCaptureSupported = false,
        )

        assertTrue(report.requiresImageCapture)
        assertFalse(report.imageCaptureSupported)
    }

    @Test
    fun directIntentActionIsIncludedInLaunchabilityProbe() {
        val workflow = Workflow(
            id = "direct",
            name = "Direct",
            steps = listOf(
                Step.LaunchApp("launch", "com.example", intentAction = "example.UNAVAILABLE"),
            ),
        )

        val report = buildWorkflowPreflightReport(
            workflow = workflow,
            accessibilityConnected = true,
            notificationStatus = ScheduleNotificationReadiness.Ready,
            isLaunchable = { packageName, intentAction ->
                packageName == "com.example" && intentAction == null
            },
            countMatches = { 0 },
        )

        assertEquals("example.UNAVAILABLE", report.launchTargets.single().intentAction)
        assertEquals(LaunchTargetStatus.Unverified, report.launchTargets.single().status)
    }

    @Test
    fun importedPackageIsNormalizedBeforeLauncherProbe() {
        val workflow = Workflow(
            id = "blank-action",
            name = "Blank action",
            steps = listOf(Step.LaunchApp("launch", " com.example ", intentAction = null)),
        )
        var probedPackage: String? = null
        var probedAction: String? = "not-null"

        val report = buildWorkflowPreflightReport(
            workflow = workflow,
            accessibilityConnected = true,
            notificationStatus = ScheduleNotificationReadiness.Ready,
            isLaunchable = { packageName, intentAction ->
                probedPackage = packageName
                probedAction = intentAction
                true
            },
            countMatches = { 0 },
        )

        assertEquals("com.example", probedPackage)
        assertEquals(null, probedAction)
        assertEquals(LaunchTargetStatus.Available, report.launchTargets.single().status)
    }
}