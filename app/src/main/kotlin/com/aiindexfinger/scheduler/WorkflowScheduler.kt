package com.aiindexfinger.scheduler

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.ValidationIssue
import com.aiindexfinger.model.readinessIssues
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class WorkflowScheduleValidationException(
    val issue: ValidationIssue,
) : IllegalArgumentException(issue.code.name)

internal class ScheduleWorkOperationException(cause: Throwable) :
    IllegalStateException("Android scheduling operation failed", cause)

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

    internal suspend fun deliverOccurrence(
        workflowId: String,
        expectedAtMillis: Long,
        origin: ScheduleWorkOrigin = ScheduleWorkOrigin.External,
        expectedOccurrenceId: String? = null,
        deliverNotification: () -> Boolean,
    ): ScheduleOccurrenceDelivery = SCHEDULER_MUTEX.withLock {
        deliverScheduledOccurrence(
            workflowId = workflowId,
            expectedAtMillis = expectedAtMillis,
            expectedOccurrenceId = expectedOccurrenceId,
            loadSchedules = store::load,
            deliverNotification = deliverNotification,
            completeOccurrence = {
                store.completeOccurrence(
                    workflowId,
                    expectedAtMillis,
                    currentTimeMillis(),
                    currentZoneId(),
                    expectedOccurrenceId,
                    UUID.randomUUID().toString(),
                )
            },
            missOccurrence = {
                store.missOccurrence(
                    workflowId,
                    expectedAtMillis,
                    currentTimeMillis(),
                    currentZoneId(),
                    expectedOccurrenceId,
                    UUID.randomUUID().toString(),
                )
            },
            enqueue = { next -> enqueue(next, existingWorkPolicy(origin)) },
            restoreSchedule = store::put,
        )
    }

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
            workManager.cancelUniqueWork(scheduleWorkName(workflowId)).await()
        }
        store.consumeMissedOccurrence(workflowId)
    }

    private suspend fun enqueue(
        schedule: WorkflowSchedule,
        workPolicy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE,
        delayMillis: Long = scheduleDelayMillis(schedule.scheduledAtMillis, currentTimeMillis()),
    ) {
        val requestId = scheduleWorkRequestId(schedule)
        val input = scheduleWorkInput(schedule)
        val request = OneTimeWorkRequestBuilder<ScheduleNotificationWorker>()
            .setId(requestId)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(input)
            .addTag(scheduleWorkName(schedule.workflowId))
            .build()
        try {
            enqueueScheduledWorkIfMissing(
                requestId = requestId,
                workExists = { id ->
                    scheduledWorkNeedsNoEnqueue(
                        workManager.getWorkInfoByIdFlow(id).first()?.state,
                        workPolicy,
                    )
                },
                enqueue = {
                    workManager.enqueueUniqueWork(
                        scheduleWorkName(schedule.workflowId),
                        workPolicy,
                        request,
                    ).await()
                },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw ScheduleWorkOperationException(error)
        }
    }

    suspend fun cancel(workflowId: String): List<WorkflowSchedule> = SCHEDULER_MUTEX.withLock {
        cancelScheduledWork(
            workflowId = workflowId,
            loadSchedules = store::load,
            persistSchedule = store::put,
            removeSchedule = store::remove,
            cancelWork = { workManager.cancelUniqueWork(scheduleWorkName(workflowId)).await() },
        )
    }

    suspend fun load(workflowIds: Set<String>): List<WorkflowSchedule> = SCHEDULER_MUTEX.withLock {
        store.load()
            .asSequence()
            .map { it.workflowId }
            .filterNot { it in workflowIds }
            .forEach { workManager.cancelUniqueWork(scheduleWorkName(it)).await() }
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

    internal suspend fun resolveWorkRequest(requestId: UUID): ScheduleWorkTarget? =
        SCHEDULER_MUTEX.withLock {
            resolveScheduleWorkTarget(store.load(), requestId)
        }

    private fun restoreSchedule(previous: WorkflowSchedule?, originalError: Exception) {
        if (previous == null) return
        try {
            store.put(previous)
        } catch (restoreError: Exception) {
            originalError.addSuppressed(restoreError)
        }
    }

    private companion object {
        val SCHEDULER_MUTEX = Mutex()
    }
}

internal fun scheduleWorkName(workflowId: String): String = "workflow-schedule-$workflowId"

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

internal fun scheduleWorkRequestId(schedule: WorkflowSchedule): UUID = scheduleWorkRequestId(
    workflowId = schedule.workflowId,
    scheduledAtMillis = schedule.scheduledAtMillis,
    occurrenceId = schedule.occurrenceId,
)

private fun scheduleWorkRequestId(
    workflowId: String,
    scheduledAtMillis: Long,
    occurrenceId: String?,
): UUID {
    val identity = buildString {
        append("aiindexfinger:schedule:v1\n")
        append(workflowId.length).append(':').append(workflowId).append('\n')
        append(scheduledAtMillis).append('\n')
        append(occurrenceId?.length ?: -1).append(':').append(occurrenceId.orEmpty())
    }
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(identity.toByteArray(StandardCharsets.UTF_8))
    digest[6] = ((digest[6].toInt() and 0x0f) or 0x50).toByte()
    digest[8] = ((digest[8].toInt() and 0x3f) or 0x80).toByte()
    return ByteBuffer.wrap(digest).let { bytes -> UUID(bytes.long, bytes.long) }
}

internal fun workflowIdForWorkInput(workflowId: String): String? = workflowId.takeIf {
    it.toByteArray(StandardCharsets.UTF_8).size <= MAX_WORKFLOW_ID_INPUT_BYTES
}

internal fun scheduleWorkInput(schedule: WorkflowSchedule): Data = Data.Builder()
    .putLong(ScheduleNotificationWorker.KEY_SCHEDULED_AT_MILLIS, schedule.scheduledAtMillis)
    .apply {
        workflowIdForWorkInput(schedule.workflowId)?.let {
            putString(ScheduleNotificationWorker.KEY_WORKFLOW_ID, it)
        }
        schedule.occurrenceId?.let {
            putString(ScheduleNotificationWorker.KEY_OCCURRENCE_ID, it)
        }
    }
    .build()

internal data class ScheduleWorkTarget(
    val workflowId: String,
    val scheduledAtMillis: Long,
    val occurrenceId: String?,
)

internal fun resolveScheduleWorkTarget(
    schedules: List<WorkflowSchedule>,
    requestId: UUID,
): ScheduleWorkTarget? = schedules.firstNotNullOfOrNull { schedule ->
    when {
        scheduleWorkRequestId(schedule) == requestId -> ScheduleWorkTarget(
            schedule.workflowId,
            schedule.scheduledAtMillis,
            schedule.occurrenceId,
        )
        schedule.previousScheduledAtMillis != null && scheduleWorkRequestId(
            schedule.workflowId,
            schedule.previousScheduledAtMillis,
            schedule.previousOccurrenceId,
        ) == requestId -> ScheduleWorkTarget(
            schedule.workflowId,
            schedule.previousScheduledAtMillis,
            schedule.previousOccurrenceId,
        )
        else -> null
    }
}

internal suspend fun enqueueScheduledWorkIfMissing(
    requestId: UUID,
    workExists: suspend (UUID) -> Boolean,
    enqueue: suspend () -> Unit,
) {
    if (!workExists(requestId)) enqueue()
}

internal fun scheduledWorkNeedsNoEnqueue(
    state: WorkInfo.State?,
    workPolicy: ExistingWorkPolicy,
): Boolean = when (state) {
    WorkInfo.State.ENQUEUED,
    WorkInfo.State.RUNNING,
    WorkInfo.State.BLOCKED,
    -> true
    WorkInfo.State.SUCCEEDED -> workPolicy != ExistingWorkPolicy.REPLACE
    WorkInfo.State.FAILED,
    WorkInfo.State.CANCELLED,
    null,
    -> false
}

internal val MAX_SCHEDULE_DELAY_MILLIS: Long = TimeUnit.DAYS.toMillis(365)
private const val MAX_WORKFLOW_ID_INPUT_BYTES = 4 * 1024

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

internal data class ScheduleOccurrenceDelivery(
    val accepted: Boolean,
    val notificationDelivered: Boolean,
)

internal suspend fun deliverScheduledOccurrence(
    workflowId: String,
    expectedAtMillis: Long,
    expectedOccurrenceId: String?,
    loadSchedules: () -> List<WorkflowSchedule>,
    deliverNotification: () -> Boolean,
    completeOccurrence: () -> ScheduleCompletion,
    missOccurrence: () -> ScheduleCompletion,
    enqueue: suspend (WorkflowSchedule) -> Unit,
    restoreSchedule: (WorkflowSchedule) -> List<WorkflowSchedule>,
): ScheduleOccurrenceDelivery {
    val current = loadSchedules().firstOrNull { it.workflowId == workflowId }
        ?: return ScheduleOccurrenceDelivery(false, false)
    val previous = current.takeIf { schedule ->
        schedule.scheduledAtMillis == expectedAtMillis &&
            schedule.occurrenceId == expectedOccurrenceId &&
            schedule.status == ScheduleStatus.Pending
    }
    if (previous == null) {
        if (current.status == ScheduleStatus.Pending &&
            current.previousScheduledAtMillis == expectedAtMillis &&
            current.previousOccurrenceId == expectedOccurrenceId
        ) {
            enqueue(current)
        }
        return ScheduleOccurrenceDelivery(false, false)
    }
    val notificationDelivered = deliverNotification()
    val completion = if (notificationDelivered) completeOccurrence() else missOccurrence()
    if (!completion.accepted) return ScheduleOccurrenceDelivery(false, notificationDelivered)
    completion.nextSchedule?.let { next ->
        try {
            enqueue(next)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            try {
                restoreSchedule(previous)
            } catch (restoreError: Exception) {
                error.addSuppressed(restoreError)
            }
            throw error
        }
    }
    return ScheduleOccurrenceDelivery(true, notificationDelivered)
}