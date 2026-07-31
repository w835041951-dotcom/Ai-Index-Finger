package com.aiindexfinger.scheduler

import android.content.Context
import com.aiindexfinger.data.AtomicFileWriter
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.time.ZoneId

@Serializable
data class WorkflowSchedule(
    val workflowId: String,
    val workflowName: String,
    val scheduledAtMillis: Long,
    val status: ScheduleStatus = ScheduleStatus.Pending,
    val recurrence: ScheduleRecurrence = ScheduleRecurrence.Once,
    val missedOccurrencePending: Boolean = false,
)

@Serializable
enum class ScheduleRecurrence {
    Once,
    Daily,
    Weekly,
}

@Serializable
enum class ScheduleStatus {
    Pending,
    Missed,
}

class ScheduleStorageException(cause: Throwable) :
    IllegalStateException("Stored schedules are corrupt and cannot be modified", cause)

class ScheduleStore private constructor(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, FILE_NAME))

    private val json = Json { ignoreUnknownKeys = true }

    fun load(): List<WorkflowSchedule> = synchronized(FILE_LOCK) {
        loadForMutation()
    }

    fun put(schedule: WorkflowSchedule): List<WorkflowSchedule> = synchronized(FILE_LOCK) {
        val updated = load().filterNot { it.workflowId == schedule.workflowId } + schedule
        save(updated)
        updated
    }

    fun remove(workflowId: String): List<WorkflowSchedule> = synchronized(FILE_LOCK) {
        val updated = load().filterNot { it.workflowId == workflowId }
        save(updated)
        updated
    }

    fun removeMissingWorkflows(workflowIds: Set<String>): List<WorkflowSchedule> = synchronized(FILE_LOCK) {
        val updated = load().filter { it.workflowId in workflowIds }
        save(updated)
        updated
    }

    fun consumeMissedOccurrence(workflowId: String): List<WorkflowSchedule> = synchronized(FILE_LOCK) {
        val updated = consumeMissedSchedule(load(), workflowId)
        save(updated)
        updated
    }

    fun isPendingOccurrence(workflowId: String, expectedAtMillis: Long): Boolean = synchronized(FILE_LOCK) {
        load().any { schedule ->
            schedule.workflowId == workflowId &&
                schedule.scheduledAtMillis == expectedAtMillis &&
                schedule.status == ScheduleStatus.Pending
        }
    }

    internal fun completeOccurrence(
        workflowId: String,
        expectedAtMillis: Long,
        completedAtMillis: Long,
        zoneId: ZoneId,
    ): ScheduleCompletion = synchronized(FILE_LOCK) {
        val completion = completeScheduleOccurrence(
            load(),
            workflowId,
            expectedAtMillis,
            completedAtMillis,
            zoneId,
        )
        if (completion.accepted) save(completion.schedules)
        completion
    }

    internal fun missOccurrence(
        workflowId: String,
        expectedAtMillis: Long,
        missedAtMillis: Long,
        zoneId: ZoneId,
    ): ScheduleCompletion = synchronized(FILE_LOCK) {
        val completion = missScheduleOccurrence(
            load(),
            workflowId,
            expectedAtMillis,
            missedAtMillis,
            zoneId,
        )
        if (completion.accepted) save(completion.schedules)
        completion
    }

    private fun save(schedules: List<WorkflowSchedule>) {
        if (schedules.isEmpty()) {
            Files.deleteIfExists(file.toPath())
            return
        }
        AtomicFileWriter.write(
            file,
            json.encodeToString(ListSerializer(WorkflowSchedule.serializer()), schedules),
        )
    }

    private fun loadForMutation(): List<WorkflowSchedule> {
        if (!file.exists()) return emptyList()
        return try {
            json.decodeFromString(ListSerializer(WorkflowSchedule.serializer()), file.readText())
        } catch (exception: Exception) {
            throw ScheduleStorageException(exception)
        }
    }

    internal companion object {
        private const val FILE_NAME = "workflow-schedules.json"
        private val FILE_LOCK = Any()

        fun forFile(file: File) = ScheduleStore(file)
    }
}

internal data class ScheduleCompletion(
    val accepted: Boolean,
    val schedules: List<WorkflowSchedule>,
    val nextSchedule: WorkflowSchedule?,
)

internal fun completeScheduleOccurrence(
    schedules: List<WorkflowSchedule>,
    workflowId: String,
    expectedAtMillis: Long,
    completedAtMillis: Long,
    zoneId: ZoneId,
): ScheduleCompletion {
    val current = schedules.firstOrNull { it.workflowId == workflowId }
    if (current == null || current.scheduledAtMillis != expectedAtMillis ||
        current.status != ScheduleStatus.Pending
    ) {
        return ScheduleCompletion(false, schedules, null)
    }
    val nextAtMillis = nextOccurrenceEpochMillis(
        current.scheduledAtMillis,
        current.recurrence,
        zoneId,
        completedAtMillis,
    )
    val next = nextAtMillis?.let {
        current.copy(scheduledAtMillis = it, status = ScheduleStatus.Pending)
    }
    return ScheduleCompletion(
        accepted = true,
        schedules = schedules.filterNot { it.workflowId == workflowId } + listOfNotNull(next),
        nextSchedule = next,
    )
}

internal fun missScheduleOccurrence(
    schedules: List<WorkflowSchedule>,
    workflowId: String,
    expectedAtMillis: Long,
    missedAtMillis: Long,
    zoneId: ZoneId,
): ScheduleCompletion {
    val current = schedules.firstOrNull { it.workflowId == workflowId }
    if (current == null || current.scheduledAtMillis != expectedAtMillis ||
        current.status != ScheduleStatus.Pending
    ) {
        return ScheduleCompletion(false, schedules, null)
    }
    if (current.recurrence == ScheduleRecurrence.Once) {
        return ScheduleCompletion(
            accepted = true,
            schedules = schedules.map { schedule ->
                if (schedule.workflowId == workflowId) schedule.copy(status = ScheduleStatus.Missed) else schedule
            },
            nextSchedule = null,
        )
    }
    val next = current.copy(
        scheduledAtMillis = requireNotNull(
            nextOccurrenceEpochMillis(
                current.scheduledAtMillis,
                current.recurrence,
                zoneId,
                missedAtMillis,
            ),
        ),
        status = ScheduleStatus.Pending,
        missedOccurrencePending = true,
    )
    return ScheduleCompletion(
        accepted = true,
        schedules = schedules.map { schedule ->
            if (schedule.workflowId == workflowId) next else schedule
        },
        nextSchedule = next,
    )
}

internal fun consumeMissedSchedule(
    schedules: List<WorkflowSchedule>,
    workflowId: String,
): List<WorkflowSchedule> = schedules.mapNotNull { schedule ->
    when {
        schedule.workflowId != workflowId -> schedule
        schedule.status == ScheduleStatus.Missed -> null
        else -> schedule.copy(missedOccurrencePending = false)
    }
}