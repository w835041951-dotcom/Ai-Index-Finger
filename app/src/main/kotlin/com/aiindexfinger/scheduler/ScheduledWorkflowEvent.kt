package com.aiindexfinger.scheduler

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScheduledWorkflowEvent(
    val sequence: Long,
    val workflowId: String,
)

class ScheduledWorkflowEventController {
    private var nextSequence = 0L
    private val pendingEvents = ArrayDeque<ScheduledWorkflowEvent>()
    private val mutableEvent = MutableStateFlow<ScheduledWorkflowEvent?>(null)
    val event = mutableEvent.asStateFlow()

    fun publish(workflowId: String?) {
        if (workflowId == null) return
        pendingEvents += ScheduledWorkflowEvent(++nextSequence, workflowId)
        publishNextIfIdle()
    }

    fun consume(sequence: Long) {
        if (mutableEvent.value?.sequence != sequence) return
        pendingEvents.removeFirstOrNull()
        mutableEvent.value = null
        publishNextIfIdle()
    }

    private fun publishNextIfIdle() {
        if (mutableEvent.value == null) mutableEvent.value = pendingEvents.firstOrNull()
    }
}

fun removeTriggeredSchedule(
    schedules: List<WorkflowSchedule>,
    workflowId: String,
): List<WorkflowSchedule> = schedules.filterNot { it.workflowId == workflowId }
