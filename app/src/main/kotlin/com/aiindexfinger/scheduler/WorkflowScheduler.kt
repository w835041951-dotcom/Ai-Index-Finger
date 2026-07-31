package com.aiindexfinger.scheduler

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.isReadyToRun
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class WorkflowScheduler(
    context: Context,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val currentZoneId: () -> ZoneId = ZoneId::systemDefault,
) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val store = ScheduleStore(appContext)

    fun schedule(
        workflow: Workflow,
        targetEpochMillis: Long,
        recurrence: ScheduleRecurrence = ScheduleRecurrence.Once,
    ): List<WorkflowSchedule> = synchronized(SCHEDULER_LOCK) {
        require(workflow.isReadyToRun()) { "只能计划就绪状态的工作流" }
        val schedule = WorkflowSchedule(workflow.id, workflow.name, targetEpochMillis, recurrence = recurrence)
        enqueue(schedule)
        store.put(schedule)
    }

    internal fun completeOccurrence(workflowId: String, expectedAtMillis: Long) =
        synchronized(SCHEDULER_LOCK) {
            val completion = store.completeOccurrence(
                workflowId,
                expectedAtMillis,
                currentTimeMillis(),
                currentZoneId(),
            )
            completion.nextSchedule?.let(::enqueue)
            completion
        }

    internal fun missOccurrence(workflowId: String, expectedAtMillis: Long) =
        synchronized(SCHEDULER_LOCK) {
            val completion = store.missOccurrence(
                workflowId,
                expectedAtMillis,
                currentTimeMillis(),
                currentZoneId(),
            )
            completion.nextSchedule?.let(::enqueue)
            completion
        }

    internal fun isPendingOccurrence(workflowId: String, expectedAtMillis: Long): Boolean =
        store.isPendingOccurrence(workflowId, expectedAtMillis)

    fun consumeMissedOccurrence(workflowId: String): List<WorkflowSchedule> = synchronized(SCHEDULER_LOCK) {
        val schedule = store.load().firstOrNull { it.workflowId == workflowId }
        if (schedule?.status == ScheduleStatus.Missed) workManager.cancelUniqueWork(workName(workflowId))
        store.consumeMissedOccurrence(workflowId)
    }

    private fun enqueue(schedule: WorkflowSchedule) {
        val delayMillis = scheduleDelayMillis(schedule.scheduledAtMillis, currentTimeMillis())
        val input = Data.Builder()
            .putString(ScheduleNotificationWorker.KEY_WORKFLOW_ID, schedule.workflowId)
            .putString(ScheduleNotificationWorker.KEY_WORKFLOW_NAME, schedule.workflowName)
            .putLong(ScheduleNotificationWorker.KEY_SCHEDULED_AT_MILLIS, schedule.scheduledAtMillis)
            .build()
        val request = OneTimeWorkRequestBuilder<ScheduleNotificationWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(input)
            .addTag(workName(schedule.workflowId))
            .build()
        workManager.enqueueUniqueWork(workName(schedule.workflowId), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(workflowId: String): List<WorkflowSchedule> = synchronized(SCHEDULER_LOCK) {
        workManager.cancelUniqueWork(workName(workflowId))
        store.remove(workflowId)
    }

    fun load(workflowIds: Set<String>): List<WorkflowSchedule> = synchronized(SCHEDULER_LOCK) {
        store.load()
            .asSequence()
            .map { it.workflowId }
            .filterNot { it in workflowIds }
            .forEach { workManager.cancelUniqueWork(workName(it)) }
        var schedules = store.removeMissingWorkflows(workflowIds)
        schedules.filter { it.status == ScheduleStatus.Pending }.forEach { schedule ->
            if (schedule.scheduledAtMillis > currentTimeMillis()) {
                enqueue(schedule)
            } else {
                schedules = missOccurrence(schedule.workflowId, schedule.scheduledAtMillis).schedules
            }
        }
        schedules
    }

    private fun workName(workflowId: String) = "workflow-schedule-$workflowId"

    private companion object {
        val SCHEDULER_LOCK = Any()
    }
}

internal fun scheduleDelayMillis(targetEpochMillis: Long, currentEpochMillis: Long): Long {
    require(targetEpochMillis > currentEpochMillis) { "计划时间必须晚于当前时间" }
    val delayMillis = targetEpochMillis - currentEpochMillis
    require(delayMillis in 1..MAX_SCHEDULE_DELAY_MILLIS) { "计划时间必须在一年以内" }
    return delayMillis
}

internal val MAX_SCHEDULE_DELAY_MILLIS: Long = TimeUnit.DAYS.toMillis(365)