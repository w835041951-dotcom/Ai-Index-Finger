package com.aiindexfinger.scheduler

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.isReadyToRun
import java.util.concurrent.TimeUnit

class WorkflowScheduler(
    context: Context,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val store = ScheduleStore(appContext)

    fun schedule(workflow: Workflow, targetEpochMillis: Long): List<WorkflowSchedule> {
        require(workflow.isReadyToRun()) { "Only ready workflows can be scheduled" }
        val delayMillis = scheduleDelayMillis(targetEpochMillis, currentTimeMillis())
        val input = Data.Builder()
            .putString(ScheduleNotificationWorker.KEY_WORKFLOW_ID, workflow.id)
            .putString(ScheduleNotificationWorker.KEY_WORKFLOW_NAME, workflow.name)
            .build()
        val request = OneTimeWorkRequestBuilder<ScheduleNotificationWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(input)
            .addTag(workName(workflow.id))
            .build()
        workManager.enqueueUniqueWork(workName(workflow.id), ExistingWorkPolicy.REPLACE, request)
        return store.put(WorkflowSchedule(workflow.id, workflow.name, targetEpochMillis))
    }

    fun cancel(workflowId: String): List<WorkflowSchedule> {
        workManager.cancelUniqueWork(workName(workflowId))
        return store.remove(workflowId)
    }

    fun load(workflowIds: Set<String>): List<WorkflowSchedule> {
        store.load()
            .asSequence()
            .map { it.workflowId }
            .filterNot { it in workflowIds }
            .forEach { workManager.cancelUniqueWork(workName(it)) }
        return store.removeMissingWorkflows(workflowIds)
    }

    private fun workName(workflowId: String) = "workflow-schedule-$workflowId"

}

internal fun scheduleDelayMillis(targetEpochMillis: Long, currentEpochMillis: Long): Long {
    require(targetEpochMillis > currentEpochMillis) { "Schedule time must be in the future" }
    val delayMillis = targetEpochMillis - currentEpochMillis
    require(delayMillis in 1..MAX_SCHEDULE_DELAY_MILLIS) { "Schedule time must be within 1 year" }
    return delayMillis
}

internal val MAX_SCHEDULE_DELAY_MILLIS: Long = TimeUnit.DAYS.toMillis(365)