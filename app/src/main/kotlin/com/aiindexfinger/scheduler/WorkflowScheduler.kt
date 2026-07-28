package com.aiindexfinger.scheduler

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class WorkflowScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val store = ScheduleStore(appContext)

    fun schedule(workflowId: String, workflowName: String, delayMinutes: Long): List<WorkflowSchedule> {
        require(delayMinutes in 1..MAX_DELAY_MINUTES) { "Delay must be between 1 minute and 1 year" }
        val scheduledAtMillis = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(delayMinutes)
        val input = Data.Builder()
            .putString(ScheduleNotificationWorker.KEY_WORKFLOW_ID, workflowId)
            .putString(ScheduleNotificationWorker.KEY_WORKFLOW_NAME, workflowName)
            .build()
        val request = OneTimeWorkRequestBuilder<ScheduleNotificationWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setInputData(input)
            .addTag(workName(workflowId))
            .build()
        workManager.enqueueUniqueWork(workName(workflowId), ExistingWorkPolicy.REPLACE, request)
        return store.put(WorkflowSchedule(workflowId, workflowName, scheduledAtMillis))
    }

    fun cancel(workflowId: String): List<WorkflowSchedule> {
        workManager.cancelUniqueWork(workName(workflowId))
        return store.remove(workflowId)
    }

    fun load(workflowIds: Set<String>): List<WorkflowSchedule> = store.removeMissingWorkflows(workflowIds)

    private fun workName(workflowId: String) = "workflow-schedule-$workflowId"

    private companion object {
        const val MAX_DELAY_MINUTES = 525_600L
    }
}