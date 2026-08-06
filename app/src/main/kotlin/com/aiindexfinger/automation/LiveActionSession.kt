package com.aiindexfinger.automation

import com.aiindexfinger.model.Step
import com.aiindexfinger.model.StepListPath
import com.aiindexfinger.model.insertStep
import com.aiindexfinger.model.stepsAt

internal data class LiveActionDestination(
    val workflowId: String,
    val listPath: StepListPath,
)

sealed interface LiveActionCandidate {
    data class Coordinate(val x: Int, val y: Int) : LiveActionCandidate

    data class Image(
        val packageName: String,
        val templatePngBase64: String,
        val templateWidth: Int,
        val templateHeight: Int,
    ) : LiveActionCandidate
}

internal fun LiveActionCandidate.toStep(id: String): Step = when (this) {
    is LiveActionCandidate.Coordinate -> Step.Tap(id = id, x = x, y = y)
    is LiveActionCandidate.Image -> Step.ImageClick(
        id = id,
        packageName = packageName,
        templatePngBase64 = templatePngBase64,
        templateWidth = templateWidth,
        templateHeight = templateHeight,
    )
}

internal data class LiveActionHandoffResult(
    val steps: List<Step>,
    val consume: Boolean,
    val appended: Boolean,
)

internal fun applyLiveActionHandoff(
    steps: List<Step>,
    editorWorkflowId: String,
    action: PendingOverlayAction.LiveAction,
    newId: () -> String,
): LiveActionHandoffResult {
    if (action.workflowId != editorWorkflowId) {
        return LiveActionHandoffResult(steps, consume = false, appended = false)
    }
    val targetSize = runCatching { steps.stepsAt(action.listPath).size }.getOrNull()
        ?: return LiveActionHandoffResult(steps, consume = true, appended = false)
    return LiveActionHandoffResult(
        steps = steps.insertStep(action.listPath, targetSize, action.candidate.toStep(newId())),
        consume = true,
        appended = true,
    )
}

internal data class ConfirmedLiveAction(
    val destination: LiveActionDestination,
    val candidate: LiveActionCandidate,
)

internal class LiveActionSession {
    private var destination: LiveActionDestination? = null
    private var candidate: LiveActionCandidate? = null

    val isActive: Boolean
        get() = destination != null

    val selectedCandidate: LiveActionCandidate?
        get() = candidate

    fun start(workflowId: String, listPath: StepListPath) {
        destination = LiveActionDestination(workflowId, listPath)
        candidate = null
    }

    fun select(candidate: LiveActionCandidate): Boolean {
        if (!isActive) return false
        this.candidate = candidate
        return true
    }

    fun confirm(): ConfirmedLiveAction? {
        val confirmedDestination = destination ?: return null
        val confirmedCandidate = candidate ?: return null
        cancel()
        return ConfirmedLiveAction(confirmedDestination, confirmedCandidate)
    }

    fun cancel() {
        destination = null
        candidate = null
    }
}