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
    val recurrenceLocalTimeMinutes: Int? = null,
    val occurrenceId: String? = null,
    val previousOccurrenceId: String? = null,
    val previousScheduledAtMillis: Long? = null,
) {
    init {
        require(recurrenceLocalTimeMinutes == null || recurrenceLocalTimeMinutes in 0 until 24 * 60) {
            "Recurrence local time must be a minute of day"
        }
        require(occurrenceId == null || occurrenceId.isNotBlank() && occurrenceId.length <= 128) {
            "Occurrence ID must be non-blank and bounded"
        }
        require(
            previousOccurrenceId == null ||
                previousOccurrenceId.isNotBlank() && previousOccurrenceId.length <= 128,
        ) {
            "Previous occurrence ID must be non-blank and bounded"
        }
        require(previousScheduledAtMillis == null || previousScheduledAtMillis > 0) {
            "Previous occurrence time must be positive"
        }
    }
}

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

class ScheduleStorageWriteException(cause: Throwable) :
    IllegalStateException("Schedules could not be saved", cause)

class ScheduleStorageCapacityException :
    IllegalStateException("Stored schedules are too large")

class ScheduleStore private constructor(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, FILE_NAME))

    private val json = Json

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

    internal fun discardOccurrence(
        workflowId: String,
        expectedAtMillis: Long,
        expectedOccurrenceId: String?,
    ): ScheduleDiscard = synchronized(FILE_LOCK) {
        val discard = discardScheduleOccurrence(
            load(),
            workflowId,
            expectedAtMillis,
            expectedOccurrenceId,
        )
        if (discard.accepted) save(discard.schedules)
        discard
    }

    internal fun completeOccurrence(
        workflowId: String,
        expectedAtMillis: Long,
        completedAtMillis: Long,
        zoneId: ZoneId,
        expectedOccurrenceId: String? = null,
        nextOccurrenceId: String? = null,
    ): ScheduleCompletion = synchronized(FILE_LOCK) {
        val completion = completeScheduleOccurrence(
            load(),
            workflowId,
            expectedAtMillis,
            completedAtMillis,
            zoneId,
            expectedOccurrenceId,
            nextOccurrenceId,
        )
        if (completion.accepted) save(completion.schedules)
        completion
    }

    internal fun missOccurrence(
        workflowId: String,
        expectedAtMillis: Long,
        missedAtMillis: Long,
        zoneId: ZoneId,
        expectedOccurrenceId: String? = null,
        nextOccurrenceId: String? = null,
    ): ScheduleCompletion = synchronized(FILE_LOCK) {
        val completion = missScheduleOccurrence(
            load(),
            workflowId,
            expectedAtMillis,
            missedAtMillis,
            zoneId,
            expectedOccurrenceId,
            nextOccurrenceId,
        )
        if (completion.accepted) save(completion.schedules)
        completion
    }

    private fun save(schedules: List<WorkflowSchedule>) {
        if (schedules.isEmpty()) {
            try {
                Files.deleteIfExists(File(file.parentFile, "${file.name}.tmp").toPath())
                Files.deleteIfExists(file.toPath())
            } catch (error: Exception) {
                throw ScheduleStorageWriteException(error)
            }
            return
        }
        val content = json.encodeToString(ListSerializer(WorkflowSchedule.serializer()), schedules)
        if (content.toByteArray(Charsets.UTF_8).size > MAX_SCHEDULE_BYTES) {
            throw ScheduleStorageCapacityException()
        }
        try {
            AtomicFileWriter.write(
                file,
                content,
            )
        } catch (error: Exception) {
            throw ScheduleStorageWriteException(error)
        }
    }

    private fun loadForMutation(): List<WorkflowSchedule> {
        AtomicFileWriter.cleanupTemporary(file)
        if (!file.exists()) return emptyList()
        if (file.length() > MAX_SCHEDULE_BYTES) {
            throw ScheduleStorageException(IllegalStateException("Stored schedules are too large"))
        }
        return try {
            json.decodeFromString(ListSerializer(WorkflowSchedule.serializer()), file.readText()).also { schedules ->
                require(schedules.map(WorkflowSchedule::workflowId).distinct().size == schedules.size) {
                    "Workflow schedule IDs must be unique"
                }
            }
        } catch (error: StackOverflowError) {
            throw ScheduleStorageException(error)
        } catch (exception: Exception) {
            throw ScheduleStorageException(exception)
        }
    }

    internal companion object {
        private const val FILE_NAME = "workflow-schedules.json"
        private const val MAX_SCHEDULE_BYTES = 2L * 1024 * 1024
        private val FILE_LOCK = Any()

        fun forFile(file: File) = ScheduleStore(file)
    }
}

internal data class ScheduleCompletion(
    val accepted: Boolean,
    val schedules: List<WorkflowSchedule>,
    val nextSchedule: WorkflowSchedule?,
)

internal data class ScheduleDiscard(
    val accepted: Boolean,
    val schedules: List<WorkflowSchedule>,
)

internal fun discardScheduleOccurrence(
    schedules: List<WorkflowSchedule>,
    workflowId: String,
    expectedAtMillis: Long,
    expectedOccurrenceId: String? = null,
): ScheduleDiscard {
    val current = schedules.firstOrNull { it.workflowId == workflowId }
    if (current == null || current.scheduledAtMillis != expectedAtMillis ||
        current.status != ScheduleStatus.Pending || current.occurrenceId != expectedOccurrenceId
    ) {
        return ScheduleDiscard(false, schedules)
    }
    return ScheduleDiscard(
        accepted = true,
        schedules = schedules.filterNot { it.workflowId == workflowId },
    )
}

internal fun completeScheduleOccurrence(
    schedules: List<WorkflowSchedule>,
    workflowId: String,
    expectedAtMillis: Long,
    completedAtMillis: Long,
    zoneId: ZoneId,
    expectedOccurrenceId: String? = null,
    nextOccurrenceId: String? = null,
): ScheduleCompletion {
    val current = schedules.firstOrNull { it.workflowId == workflowId }
    if (current == null || current.scheduledAtMillis != expectedAtMillis ||
        current.status != ScheduleStatus.Pending || current.occurrenceId != expectedOccurrenceId
    ) {
        return ScheduleCompletion(false, schedules, null)
    }
    val anchorMinutes = current.recurrenceLocalTimeMinutes ?: recurrenceLocalTimeMinutes(
        current.scheduledAtMillis,
        current.recurrence,
        zoneId,
    )
    val nextAtMillis = nextOccurrenceEpochMillis(
        current.scheduledAtMillis,
        current.recurrence,
        zoneId,
        completedAtMillis,
        anchorMinutes,
    )
    val next = nextAtMillis?.let {
        current.copy(
            scheduledAtMillis = it,
            status = ScheduleStatus.Pending,
            recurrenceLocalTimeMinutes = anchorMinutes,
            occurrenceId = nextOccurrenceId ?: current.occurrenceId,
            previousOccurrenceId = current.occurrenceId,
            previousScheduledAtMillis = current.scheduledAtMillis,
        )
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
    expectedOccurrenceId: String? = null,
    nextOccurrenceId: String? = null,
): ScheduleCompletion {
    val current = schedules.firstOrNull { it.workflowId == workflowId }
    if (current == null || current.scheduledAtMillis != expectedAtMillis ||
        current.status != ScheduleStatus.Pending || current.occurrenceId != expectedOccurrenceId
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
    val anchorMinutes = current.recurrenceLocalTimeMinutes ?: recurrenceLocalTimeMinutes(
        current.scheduledAtMillis,
        current.recurrence,
        zoneId,
    )
    val next = current.copy(
        scheduledAtMillis = requireNotNull(
            nextOccurrenceEpochMillis(
                current.scheduledAtMillis,
                current.recurrence,
                zoneId,
                missedAtMillis,
                anchorMinutes,
            ),
        ),
        status = ScheduleStatus.Pending,
        missedOccurrencePending = true,
        recurrenceLocalTimeMinutes = anchorMinutes,
        occurrenceId = nextOccurrenceId ?: current.occurrenceId,
        previousOccurrenceId = current.occurrenceId,
        previousScheduledAtMillis = current.scheduledAtMillis,
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