package com.aiindexfinger.data

import com.aiindexfinger.model.StepPath
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.uniquePathTo

data class RunHistoryDestination(
    val workflow: Workflow,
    val stepPath: StepPath?,
)

fun resolveRunHistoryDestination(
    record: RunRecord,
    workflows: List<Workflow>,
): RunHistoryDestination? {
    val workflow = workflows.firstOrNull { it.id == record.workflowId } ?: return null
    return RunHistoryDestination(
        workflow = workflow,
        stepPath = record.failedStepId?.let(workflow.steps::uniquePathTo),
    )
}
