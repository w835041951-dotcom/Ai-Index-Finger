package com.aiindexfinger.automation

import com.aiindexfinger.model.SelectorUse
import com.aiindexfinger.model.SelectorRole
import com.aiindexfinger.model.Step
import com.aiindexfinger.model.FailurePolicy
import com.aiindexfinger.model.RecordedClickTargetMode
import com.aiindexfinger.model.ValidationIssue
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.WorkflowState
import com.aiindexfinger.model.WorkflowValidationSummary
import com.aiindexfinger.model.WorkflowValidator
import com.aiindexfinger.model.effectiveState
import com.aiindexfinger.model.launchTargets
import com.aiindexfinger.model.selectorUses
import com.aiindexfinger.scheduler.ScheduleNotificationReadiness

enum class PreflightRecoveryAction {
    SetUpAutomation,
    OpenNotificationSettings,
}

fun WorkflowPreflightReport.recoveryActions(): List<PreflightRecoveryAction> = buildList {
    if (!accessibilityConnected) add(PreflightRecoveryAction.SetUpAutomation)
    if (notificationStatus != ScheduleNotificationReadiness.Ready) {
        add(PreflightRecoveryAction.OpenNotificationSettings)
    }
}

data class LaunchTargetCheck(
    val packageName: String,
    val intentAction: String?,
    val status: LaunchTargetStatus,
)

enum class LaunchTargetStatus {
    Available,
    Unverified,
    Unavailable,
}

data class SelectorPreflightCheck(
    val use: SelectorUse,
    val matchCount: Int?,
    val expectation: SelectorPreflightExpectation = SelectorPreflightExpectation.RequiredPresent,
) {
    val requiredMatchAvailable: Boolean?
        get() = requirementSatisfied

    val requirementSatisfied: Boolean?
        get() = matchCount?.let { count ->
            when (expectation) {
                SelectorPreflightExpectation.RequiredPresent -> count > use.selector.matchIndex
                SelectorPreflightExpectation.RequiredAbsent -> count <= use.selector.matchIndex
                SelectorPreflightExpectation.ObserveOnly -> null
            }
        }
}

data class CoordinatePreflightIssue(
    val stepId: String,
    val displayWidth: Int,
    val displayHeight: Int,
)

data class ImageTemplatePreflightIssue(val stepId: String)

enum class SelectorPreflightExpectation {
    RequiredPresent,
    RequiredAbsent,
    ObserveOnly,
}

data class WorkflowPreflightReport(
    val state: WorkflowState,
    val validation: WorkflowValidationSummary,
    val accessibilityConnected: Boolean,
    val notificationStatus: ScheduleNotificationReadiness,
    val launchTargets: List<LaunchTargetCheck>,
    val selectors: List<SelectorPreflightCheck>,
    val requiresImageCapture: Boolean,
    val imageCaptureSupported: Boolean,
    val coordinateIssues: List<CoordinatePreflightIssue> = emptyList(),
    val imageTemplateIssues: List<ImageTemplatePreflightIssue> = emptyList(),
) {
    val validationIssues: List<ValidationIssue>
        get() = validation.issues
}

fun buildWorkflowPreflightReport(
    workflow: Workflow,
    accessibilityConnected: Boolean,
    notificationStatus: ScheduleNotificationReadiness,
    isLaunchable: (String, String?) -> Boolean,
    countMatches: (com.aiindexfinger.model.NodeSelector) -> Int,
    imageCaptureSupported: Boolean = true,
    displayWidth: Int? = null,
    displayHeight: Int? = null,
    isImageTemplateValid: (Step.ImageClick) -> Boolean = { true },
): WorkflowPreflightReport {
    val selectorContexts = collectSelectorPreflightContexts(workflow.steps)
    return WorkflowPreflightReport(
    state = workflow.effectiveState(),
    validation = WorkflowValidator.inspect(workflow),
    accessibilityConnected = accessibilityConnected,
    notificationStatus = notificationStatus,
    launchTargets = workflow.launchTargets().map { target ->
        val normalizedTarget = normalizedLaunchTarget(target.packageName, target.intentAction)
        val launchable = normalizedTarget?.let {
            isLaunchable(it.packageName, it.intentAction)
        } == true
        LaunchTargetCheck(
            target.packageName,
            target.intentAction,
            when {
                launchable -> LaunchTargetStatus.Available
                normalizedTarget?.intentAction != null -> LaunchTargetStatus.Unverified
                else -> LaunchTargetStatus.Unavailable
            },
        )
    },
    selectors = selectorContexts.map { context ->
        val use = context.use
        SelectorPreflightCheck(
            use = use,
            matchCount = if (
                accessibilityConnected && !context.shouldDeferSelectorProbe()
            ) {
                countMatches(use.selector)
            } else {
                null
            },
            expectation = context.expectation,
        )
    },
    requiresImageCapture = workflow.steps.any(Step::containsImageClick),
    imageCaptureSupported = imageCaptureSupported,
    coordinateIssues = if (displayWidth != null && displayHeight != null &&
        displayWidth > 0 && displayHeight > 0
    ) {
        collectCoordinatePreflightIssues(workflow.steps, displayWidth, displayHeight)
    } else {
        emptyList()
    },
    imageTemplateIssues = collectImageTemplatePreflightIssues(
        workflow.steps,
        isImageTemplateValid,
    ),
    )
}

private fun collectImageTemplatePreflightIssues(
    steps: List<Step>,
    isValid: (Step.ImageClick) -> Boolean,
): List<ImageTemplatePreflightIssue> = buildList {
    steps.forEach { step ->
        when (step) {
            is Step.ImageClick -> if (!isValid(step)) add(ImageTemplatePreflightIssue(step.id))
            is Step.Repeat -> addAll(collectImageTemplatePreflightIssues(step.steps, isValid))
            is Step.IfElse -> {
                addAll(collectImageTemplatePreflightIssues(step.whenTrue, isValid))
                addAll(collectImageTemplatePreflightIssues(step.whenFalse, isValid))
            }
            else -> Unit
        }
    }
}

private fun collectCoordinatePreflightIssues(
    steps: List<Step>,
    displayWidth: Int,
    displayHeight: Int,
): List<CoordinatePreflightIssue> = buildList {
    fun inside(x: Int, y: Int): Boolean = x in 0 until displayWidth && y in 0 until displayHeight
    steps.forEach { step ->
        val invalid = when (step) {
            is Step.Tap -> !inside(step.x, step.y)
            is Step.Swipe -> !inside(step.startX, step.startY) || !inside(step.endX, step.endY)
            is Step.RecordedClick -> step.targetMode == RecordedClickTargetMode.Coordinates &&
                !inside(step.x, step.y)
            else -> false
        }
        if (invalid) add(CoordinatePreflightIssue(step.id, displayWidth, displayHeight))
        when (step) {
            is Step.Repeat -> addAll(
                collectCoordinatePreflightIssues(step.steps, displayWidth, displayHeight),
            )
            is Step.IfElse -> {
                addAll(collectCoordinatePreflightIssues(step.whenTrue, displayWidth, displayHeight))
                addAll(collectCoordinatePreflightIssues(step.whenFalse, displayWidth, displayHeight))
            }
            else -> Unit
        }
    }
}

private data class SelectorPreflightContext(
    val use: SelectorUse,
    val guaranteedLaunchPackages: Set<String>?,
    val expectation: SelectorPreflightExpectation,
) {
    fun shouldDeferSelectorProbe(): Boolean {
        val packages = guaranteedLaunchPackages ?: return false
        val selectorPackage = use.selector.packageName.trim()
        return selectorPackage.isBlank() || packages == setOf(selectorPackage)
    }
}

private fun collectSelectorPreflightContexts(steps: List<Step>): List<SelectorPreflightContext> =
    collectSelectorPreflightContexts(steps, guaranteedLaunchPackages = null).first

private fun collectSelectorPreflightContexts(
    steps: List<Step>,
    guaranteedLaunchPackages: Set<String>?,
): Pair<List<SelectorPreflightContext>, Set<String>?> {
    val contexts = mutableListOf<SelectorPreflightContext>()
    var guaranteedPackages = guaranteedLaunchPackages
    steps.forEach { step ->
        val guaranteedPackagesBeforeStep = guaranteedPackages
        fun add(
            role: SelectorRole,
            selector: com.aiindexfinger.model.NodeSelector,
            expectation: SelectorPreflightExpectation = SelectorPreflightExpectation.RequiredPresent,
        ) {
            contexts += SelectorPreflightContext(
                SelectorUse(step.id, role, selector),
                guaranteedPackages,
                expectation,
            )
        }
        when (step) {
            is Step.LaunchApp -> {
                guaranteedPackages = if (step.failurePolicy is FailurePolicy.Continue) {
                    guaranteedPackagesBeforeStep
                } else {
                    setOf(step.packageName.trim())
                }
            }
            is Step.Click -> add(SelectorRole.Click, step.selector)
            is Step.RecordedClick -> if (step.targetMode == RecordedClickTargetMode.Control) {
                step.selector?.let { add(SelectorRole.RecordedClick, it) }
            }
            is Step.LongClick -> add(SelectorRole.LongClick, step.selector)
            is Step.InputText -> add(SelectorRole.InputText, step.selector)
            is Step.ReadNodeText -> add(SelectorRole.ReadNodeText, step.selector)
            is Step.Scroll -> add(SelectorRole.Scroll, step.selector)
            is Step.WaitForNode -> add(
                SelectorRole.WaitForNode,
                step.selector,
                if (step.mustExist) SelectorPreflightExpectation.RequiredPresent
                else SelectorPreflightExpectation.RequiredAbsent,
            )
            is Step.IfElse -> {
                (step.condition as? com.aiindexfinger.model.Condition.NodeExists)?.let {
                    add(
                        SelectorRole.NodeCondition,
                        it.selector,
                        SelectorPreflightExpectation.ObserveOnly,
                    )
                }
                val trueResult = collectSelectorPreflightContexts(step.whenTrue, guaranteedPackages)
                val falseResult = collectSelectorPreflightContexts(step.whenFalse, guaranteedPackages)
                contexts += trueResult.first
                contexts += falseResult.first
                guaranteedPackages = if (step.failurePolicy is FailurePolicy.Continue) {
                    guaranteedPackagesBeforeStep
                } else {
                    combineGuaranteedLaunchPackages(trueResult.second, falseResult.second)
                }
            }
            is Step.Repeat -> {
                val result = collectSelectorPreflightContexts(step.steps, guaranteedPackages)
                contexts += result.first
                guaranteedPackages = if (step.failurePolicy is FailurePolicy.Continue) {
                    guaranteedPackagesBeforeStep
                } else {
                    result.second
                }
            }
            else -> Unit
        }
    }
    return contexts to guaranteedPackages
}

private fun combineGuaranteedLaunchPackages(
    first: Set<String>?,
    second: Set<String>?,
): Set<String>? = if (first == null || second == null) null else first + second

private fun Step.containsImageClick(): Boolean = when (this) {
    is Step.ImageClick -> true
    is Step.Repeat -> steps.any(Step::containsImageClick)
    is Step.IfElse -> (whenTrue + whenFalse).any(Step::containsImageClick)
    else -> false
}
