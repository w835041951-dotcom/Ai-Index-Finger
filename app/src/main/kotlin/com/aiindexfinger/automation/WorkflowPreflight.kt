package com.aiindexfinger.automation

import com.aiindexfinger.model.SelectorUse
import com.aiindexfinger.model.ValidationIssue
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.WorkflowState
import com.aiindexfinger.model.WorkflowValidationSummary
import com.aiindexfinger.model.WorkflowValidator
import com.aiindexfinger.model.effectiveState
import com.aiindexfinger.model.launchPackages
import com.aiindexfinger.model.selectorUses

enum class NotificationPreflightStatus {
    Granted,
    Denied,
    NotRequired,
}

enum class PreflightRecoveryAction {
    SetUpAutomation,
    GrantNotifications,
}

fun WorkflowPreflightReport.recoveryActions(): List<PreflightRecoveryAction> = buildList {
    if (!accessibilityConnected) add(PreflightRecoveryAction.SetUpAutomation)
    if (notificationStatus == NotificationPreflightStatus.Denied) {
        add(PreflightRecoveryAction.GrantNotifications)
    }
}

data class LaunchTargetCheck(
    val packageName: String,
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
) {
    val validationIssues: List<ValidationIssue>
        get() = validation.issues
}

fun buildWorkflowPreflightReport(
    workflow: Workflow,
    accessibilityConnected: Boolean,
    notificationStatus: NotificationPreflightStatus,
    isLaunchable: (String) -> Boolean,
    countMatches: (com.aiindexfinger.model.NodeSelector) -> Int,
): WorkflowPreflightReport = WorkflowPreflightReport(
    state = workflow.effectiveState(),
    validation = WorkflowValidator.inspect(workflow),
    accessibilityConnected = accessibilityConnected,
    notificationStatus = notificationStatus,
    launchTargets = workflow.launchPackages().map { packageName ->
        LaunchTargetCheck(packageName, isLaunchable(packageName))
    },
    selectors = workflow.selectorUses().map { use ->
        SelectorPreflightCheck(
            use = use,
            matchCount = if (accessibilityConnected) countMatches(use.selector) else null,
        )
    },
)
