package com.aiindexfinger.scheduler

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.ValidationIssue
import com.aiindexfinger.model.readinessIssues
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class WorkflowScheduleValidationException(
    val issue: ValidationIssue,
) : IllegalArgumentException(issue.code.name)

internal enum class ScheduleWorkOrigin {
    External,
    RunningWorker,
}

internal fun existingWorkPolicy(origin: ScheduleWorkOrigin): ExistingWorkPolicy = when (origin) {
    ScheduleWorkOrigin.External -> ExistingWorkPolicy.REPLACE
    ScheduleWorkOrigin.RunningWorker -> ExistingWorkPolicy.APPEND_OR_REPLACE
}

class WorkflowScheduler(
    context: Context,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val currentZoneId: () -> ZoneId = ZoneId::systemDefault,
) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val store = ScheduleStore(appContext)

    suspend fun schedule(
        workflow: Workflow,
        targetEpochMillis: Long,
        recurrence: ScheduleRecurrence = ScheduleRecurrence.Once,
    ): List<WorkflowSchedule> = SCHEDULER_MUTEX.withLock {
        workflow.readinessIssues().firstOrNull()?.let {
            throw WorkflowScheduleValidationException(it)
        }
        val recurrenceLocalTimeMinutes = recurrenceLocalTimeMinutes(
            targetEpochMillis,
            recurrence,
            currentZoneId(),
        )
        val schedule = WorkflowSchedule(
            workflow.id,
            workflow.name,
            targetEpochMillis,
            recurrence = recurrence,
            recurrenceLocalTimeMinutes = recurrenceLocalTimeMinutes,
            occurrenceId = UUID.randomUUID().toString(),
        )
        val delayMillis = scheduleDelayMillis(targetEpochMillis, currentTimeMillis())
        persistScheduledWork(
            schedule = schedule,
            loadSchedules = store::load,
            persistSchedule = store::put,
            removeSchedule = store::remove,
            enqueue = { enqueue(schedule, delayMillis = delayMillis) },
        )
    }

    internal suspend fun completeOccurrence(
        workflowId: String,
        expectedAtMillis: Long,
        origin: ScheduleWorkOrigin = ScheduleWorkOrigin.External,
        expectedOccurrenceId: String? = null,
    ) = SCHEDULER_MUTEX.withLock {
            val previous = store.load().firstOrNull { it.workflowId == workflowId }
            val completion = store.completeOccurrence(
                workflowId,
                expectedAtMillis,
                currentTimeMillis(),
                currentZoneId(),
                expectedOccurrenceId,
                UUID.randomUUID().toString(),
            )
            completion.nextSchedule?.let { next ->
                try {
                    enqueue(next, existingWorkPolicy(origin))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    restoreSchedule(previous, error)
                    throw error
                }
            }
            completion
        }

    internal suspend fun missOccurrence(
        workflowId: String,
        expectedAtMillis: Long,
        origin: ScheduleWorkOrigin = ScheduleWorkOrigin.External,
        expectedOccurrenceId: String? = null,
    ) = SCHEDULER_MUTEX.withLock {
            val previous = store.load().firstOrNull { it.workflowId == workflowId }
            val completion = store.missOccurrence(
                workflowId,
                expectedAtMillis,
                currentTimeMillis(),
                currentZoneId(),
                expectedOccurrenceId,
                UUID.randomUUID().toString(),
            )
            completion.nextSchedule?.let { next ->
                try {
                    enqueue(next, existingWorkPolicy(origin))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    restoreSchedule(previous, error)
                    throw error
                }
            }
            completion
        }

    internal fun isPendingOccurrence(workflowId: String, expectedAtMillis: Long): Boolean =
        store.isPendingOccurrence(workflowId, expectedAtMillis)

    internal suspend fun discardOccurrence(
        workflowId: String,
        expectedAtMillis: Long,
        expectedOccurrenceId: String?,
    ): ScheduleDiscard = SCHEDULER_MUTEX.withLock {
            store.discardOccurrence(workflowId, expectedAtMillis, expectedOccurrenceId)
        }

    suspend fun consumeMissedOccurrence(workflowId: String): List<WorkflowSchedule> =
        SCHEDULER_MUTEX.withLock {
        val schedule = store.load().firstOrNull { it.workflowId == workflowId }
        if (schedule?.status == ScheduleStatus.Missed) {
            workManager.cancelUniqueWork(workName(workflowId)).await()
        }
        store.consumeMissedOccurrence(workflowId)
    }

    private suspend fun enqueue(
        schedule: WorkflowSchedule,
        workPolicy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE,
        delayMillis: Long = scheduleDelayMillis(schedule.scheduledAtMillis, currentTimeMillis()),
    ) {
        val input = Data.Builder()
            .putString(ScheduleNotificationWorker.KEY_WORKFLOW_ID, schedule.workflowId)
            .putLong(ScheduleNotificationWorker.KEY_SCHEDULED_AT_MILLIS, schedule.scheduledAtMillis)
            .apply {
                schedule.occurrenceId?.let { putString(ScheduleNotificationWorker.KEY_OCCURRENCE_ID, it) }
            }
            .build()
        val request = OneTimeWorkRequestBuilder<ScheduleNotificationWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(input)
            .addTag(workName(schedule.workflowId))
            .build()
        workManager.enqueueUniqueWork(workName(schedule.workflowId), workPolicy, request).await()
    }

    suspend fun cancel(workflowId: String): List<WorkflowSchedule> = SCHEDULER_MUTEX.withLock {
        cancelScheduledWork(
            workflowId = workflowId,
            loadSchedules = store::load,
            persistSchedule = store::put,
            removeSchedule = store::remove,
            cancelWork = { workManager.cancelUniqueWork(workName(workflowId)).await() },
        )
    }

    suspend fun load(workflowIds: Set<String>): List<WorkflowSchedule> = SCHEDULER_MUTEX.withLock {
        store.load()
            .asSequence()
            .map { it.workflowId }
            .filterNot { it in workflowIds }
            .forEach { workManager.cancelUniqueWork(workName(it)).await() }
        var schedules = store.removeMissingWorkflows(workflowIds)
        schedules.filter { it.status == ScheduleStatus.Pending }.forEach { schedule ->
            if (schedule.scheduledAtMillis > currentTimeMillis()) {
                enqueue(schedule)
            } else {
                val previous = schedule
                val completion = store.missOccurrence(
                    schedule.workflowId,
                    schedule.scheduledAtMillis,
                    currentTimeMillis(),
                    currentZoneId(),
                    expectedOccurrenceId = schedule.occurrenceId,
                    nextOccurrenceId = UUID.randomUUID().toString(),
                )
                try {
                    completion.nextSchedule?.let { enqueue(it) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    restoreSchedule(previous, error)
                    throw error
                }
                schedules = completion.schedules
            }
        }
        schedules
    }

    suspend fun loadWithoutReconciliation(): List<WorkflowSchedule> = SCHEDULER_MUTEX.withLock {
        store.load()
    }

    private fun restoreSchedule(previous: WorkflowSchedule?, originalError: Exception) {
        if (previous == null) return
        try {
            store.put(previous)
        } catch (restoreError: Exception) {
            originalError.addSuppressed(restoreError)
        }
    }

    private fun workName(workflowId: String) = "workflow-schedule-$workflowId"

    private companion object {
        val SCHEDULER_MUTEX = Mutex()
    }
}

internal fun scheduleDelayMillis(targetEpochMillis: Long, currentEpochMillis: Long): Long {
    if (targetEpochMillis <= currentEpochMillis) {
        throw ScheduleTimeException(ScheduleTimeError.NotInFuture)
    }
    val delayMillis = targetEpochMillis - currentEpochMillis
    if (delayMillis !in 1..MAX_SCHEDULE_DELAY_MILLIS) {
        throw ScheduleTimeException(ScheduleTimeError.TooFarInFuture)
    }
    return delayMillis
}

internal val MAX_SCHEDULE_DELAY_MILLIS: Long = TimeUnit.DAYS.toMillis(365)

internal suspend fun persistScheduledWork(
    schedule: WorkflowSchedule,
    loadSchedules: () -> List<WorkflowSchedule>,
    persistSchedule: (WorkflowSchedule) -> List<WorkflowSchedule>,
    removeSchedule: (String) -> List<WorkflowSchedule>,
    enqueue: suspend () -> Unit,
): List<WorkflowSchedule> {
    val previous = loadSchedules().firstOrNull { it.workflowId == schedule.workflowId }
    val schedules = persistSchedule(schedule)
    try {
        enqueue()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        try {
            if (previous == null) removeSchedule(schedule.workflowId) else persistSchedule(previous)
        } catch (restoreError: Exception) {
            error.addSuppressed(restoreError)
        }
        throw error
    }
    return schedules
}

internal suspend fun cancelScheduledWork(
    workflowId: String,
    loadSchedules: () -> List<WorkflowSchedule>,
    persistSchedule: (WorkflowSchedule) -> List<WorkflowSchedule>,
    removeSchedule: (String) -> List<WorkflowSchedule>,
    cancelWork: suspend () -> Unit,
): List<WorkflowSchedule> {
    var previous: WorkflowSchedule? = null
    var schedules: List<WorkflowSchedule>? = null
    var storageFailure: Exception? = null
    try {
        previous = loadSchedules().firstOrNull { it.workflowId == workflowId }
        schedules = removeSchedule(workflowId)
    } catch (error: Exception) {
        storageFailure = error
    }
    try {
        cancelWork()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        if (storageFailure == null) {
            try {
                previous?.let(persistSchedule)
            } catch (restoreError: Exception) {
                error.addSuppressed(restoreError)
            }
            throw error
        }
        storageFailure.addSuppressed(error)
    }
    storageFailure?.let { throw it }
    return requireNotNull(schedules)
}