package com.aiindexfinger.automation

import com.aiindexfinger.model.SelectorUse
import com.aiindexfinger.model.SelectorRole
import com.aiindexfinger.model.Step
import com.aiindexfinger.model.ValidationIssue
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.WorkflowState
import com.aiindexfinger.model.WorkflowValidationSummary
import com.aiindexfinger.model.WorkflowValidator
import com.aiindexfinger.model.effectiveState
import com.aiindexfinger.model.launchTargets
import com.aiindexfinger.model.selectorUses

enum class NotificationPreflightStatus {
    Granted,
    Denied,
    NotRequired,
}

enum class PreflightRecoveryAction {
    SetUpAutomation,
}

fun WorkflowPreflightReport.recoveryActions(): List<PreflightRecoveryAction> = buildList {
    if (!accessibilityConnected) add(PreflightRecoveryAction.SetUpAutomation)
}

data class LaunchTargetCheck(
    val packageName: String,
    val intentAction: String?,
    val isLaunchable: Boolean,
)

data class SelectorPreflightCheck(
    val use: SelectorUse,
    val matchCount: Int?,
) {
    val requiredMatchAvailable: Boolean?
        get() = matchCount?.let { it > use.selector.matchIndex }
}

data class WorkflowPreflightReport(
    val state: WorkflowState,
    val validation: WorkflowValidationSummary,
    val accessibilityConnected: Boolean,
    val notificationStatus: NotificationPreflightStatus,
    val launchTargets: List<LaunchTargetCheck>,
    val selectors: List<SelectorPreflightCheck>,
    val requiresImageCapture: Boolean,
    val imageCaptureSupported: Boolean,
) {
    val validationIssues: List<ValidationIssue>
        get() = validation.issues
}

fun buildWorkflowPreflightReport(
    workflow: Workflow,
    accessibilityConnected: Boolean,
    notificationStatus: NotificationPreflightStatus,
    isLaunchable: (String, String?) -> Boolean,
    countMatches: (com.aiindexfinger.model.NodeSelector) -> Int,
    imageCaptureSupported: Boolean = true,
): WorkflowPreflightReport {
    val selectorContexts = collectSelectorPreflightContexts(workflow.steps)
    return WorkflowPreflightReport(
    state = workflow.effectiveState(),
    validation = WorkflowValidator.inspect(workflow),
    accessibilityConnected = accessibilityConnected,
    notificationStatus = notificationStatus,
    launchTargets = workflow.launchTargets().map { target ->
        LaunchTargetCheck(
            target.packageName,
            target.intentAction,
            isLaunchable(target.packageName, target.intentAction),
        )
    },
    selectors = selectorContexts.map { context ->
        val use = context.use
        SelectorPreflightCheck(
            use = use,
            matchCount = if (
                accessibilityConnected && !(context.launchPrecedes && use.selector.packageName.isBlank())
            ) {
                countMatches(use.selector)
            } else {
                null
            },
        )
    },
    requiresImageCapture = workflow.steps.any(Step::containsImageClick),
    imageCaptureSupported = imageCaptureSupported,
    )
}

private data class SelectorPreflightContext(
    val use: SelectorUse,
    val launchPrecedes: Boolean,
)

private fun collectSelectorPreflightContexts(steps: List<Step>): List<SelectorPreflightContext> =
    collectSelectorPreflightContexts(steps, launchPrecedes = false).first

private fun collectSelectorPreflightContexts(
    steps: List<Step>,
    launchPrecedes: Boolean,
): Pair<List<SelectorPreflightContext>, Boolean> {
    val contexts = mutableListOf<SelectorPreflightContext>()
    var launchGuaranteed = launchPrecedes
    steps.forEach { step ->
        fun add(role: SelectorRole, selector: com.aiindexfinger.model.NodeSelector) {
            contexts += SelectorPreflightContext(SelectorUse(step.id, role, selector), launchGuaranteed)
        }
        when (step) {
            is Step.LaunchApp -> launchGuaranteed = true
            is Step.Click -> add(SelectorRole.Click, step.selector)
            is Step.RecordedClick -> step.selector?.let { add(SelectorRole.RecordedClick, it) }
            is Step.LongClick -> add(SelectorRole.LongClick, step.selector)
            is Step.InputText -> add(SelectorRole.InputText, step.selector)
            is Step.ReadNodeText -> add(SelectorRole.ReadNodeText, step.selector)
            is Step.Scroll -> add(SelectorRole.Scroll, step.selector)
            is Step.WaitForNode -> add(SelectorRole.WaitForNode, step.selector)
            is Step.IfElse -> {
                (step.condition as? com.aiindexfinger.model.Condition.NodeExists)?.let {
                    add(SelectorRole.NodeCondition, it.selector)
                }
                val trueResult = collectSelectorPreflightContexts(step.whenTrue, launchGuaranteed)
                val falseResult = collectSelectorPreflightContexts(step.whenFalse, launchGuaranteed)
                contexts += trueResult.first
                contexts += falseResult.first
                launchGuaranteed = trueResult.second && falseResult.second
            }
            is Step.Repeat -> {
                val result = collectSelectorPreflightContexts(step.steps, launchGuaranteed)
                contexts += result.first
                launchGuaranteed = result.second
            }
            else -> Unit
        }
    }
    return contexts to launchGuaranteed
}

private fun Step.containsImageClick(): Boolean = when (this) {
    is Step.ImageClick -> true
    is Step.Repeat -> steps.any(Step::containsImageClick)
    is Step.IfElse -> (whenTrue + whenFalse).any(Step::containsImageClick)
    else -> false
}
