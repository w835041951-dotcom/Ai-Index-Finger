package com.aiindexfinger.data

import android.content.Context
import com.aiindexfinger.executor.RunResult
import com.aiindexfinger.executor.StepExecutionDiagnostic
import com.aiindexfinger.executor.StepExecutionOutcome
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.Step
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import java.util.UUID

@Serializable
data class RunRecord(
    val id: String,
    val workflowId: String,
    val workflowName: String,
    val startedAtMillis: Long,
    val durationMillis: Long,
    val status: RunStatus = RunStatus.Unknown,
    val failedStepId: String? = null,
    val failedStepLocation: RunStepLocation? = null,
    val failureMessage: String? = null,
    val failureCode: String? = null,
    val failureArguments: Map<String, String> = emptyMap(),
    val diagnostics: List<RunStepDiagnostic> = emptyList(),
)

@Serializable
data class RunStepDiagnostic(
    val sequence: Long,
    val stepId: String,
    val durationMillis: Long,
    val attemptCount: Int,
    val outcome: RunStepOutcome = RunStepOutcome.Unknown,
    val location: RunStepLocation? = null,
    val failureCode: String? = null,
    val failureArguments: Map<String, String> = emptyMap(),
    val failedStepId: String? = null,
    val failedStepLocation: RunStepLocation? = null,
)

@Serializable
data class RunStepLocation(
    val segments: List<RunStepLocationSegment>,
)

@Serializable
data class RunStepLocationSegment(
    val index: Int,
    val branch: RunStepBranch? = null,
)

@Serializable
enum class RunStepBranch {
    RepeatBody,
    IfTrue,
    IfFalse,
}

@Serializable
enum class RunStepOutcome {
    Completed,
    ContinuedAfterFailure,
    Failed,
    Cancelled,
    Unknown,
}

@Serializable
enum class RunStatus {
    Completed,
    CompletedWithWarnings,
    Cancelled,
    Failed,
    Rejected,
    Unknown,
}

internal const val RUN_FAILURE_CONTROL_NOTIFICATION_UNAVAILABLE =
    "control.NotificationUnavailable"

class RunHistoryStorageException(cause: Throwable) :
    IllegalStateException("Stored run history is corrupt and cannot be modified", cause)

sealed interface RunHistoryLoadResult {
    val records: List<RunRecord>

    data object Missing : RunHistoryLoadResult {
        override val records: List<RunRecord> = emptyList()
    }

    data class Loaded(
        override val records: List<RunRecord>,
        val readOnly: Boolean = false,
    ) : RunHistoryLoadResult

    data class Corrupt(val error: Throwable) : RunHistoryLoadResult {
        override val records: List<RunRecord> = emptyList()
    }
}

class RunHistoryStore private constructor(
    private val file: File,
) {
    constructor(context: Context) : this(File(context.filesDir, FILE_NAME))

    internal constructor(directory: File, fileName: String = FILE_NAME) : this(File(directory, fileName))

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        prettyPrint = true
    }

    fun load(): List<RunRecord> = synchronized(FILE_LOCK) {
        loadDetailed().records
    }

    fun loadDetailed(): RunHistoryLoadResult = synchronized(FILE_LOCK) {
        AtomicFileWriter.cleanupTemporary(file)
        if (!file.exists()) return RunHistoryLoadResult.Missing
        try {
            require(file.length() <= MAX_HISTORY_BYTES) { "Stored run history is too large" }
            val content = file.readText()
            val records = json.decodeFromString(ListSerializer(RunRecord.serializer()), content)
            RunHistoryLoadResult.Loaded(
                records = records.take(MAX_RECORDS),
                readOnly = records.size > MAX_RECORDS || containsUnsupportedHistoryData(content),
            )
        } catch (error: StackOverflowError) {
            RunHistoryLoadResult.Corrupt(error)
        } catch (exception: Exception) {
            RunHistoryLoadResult.Corrupt(exception)
        }
    }

    fun append(record: RunRecord): List<RunRecord> = synchronized(FILE_LOCK) {
        val updated = (listOf(record) + loadForMutation()).distinctBy { it.id }.take(MAX_RECORDS)
        save(updated)
        updated
    }

    fun clear() = synchronized(FILE_LOCK) {
        Files.deleteIfExists(File(file.parentFile, "${file.name}.tmp").toPath())
        Files.deleteIfExists(file.toPath())
    }

    private fun save(records: List<RunRecord>) {
        AtomicFileWriter.write(
            file,
            json.encodeToString(ListSerializer(RunRecord.serializer()), records),
        )
    }

    private fun loadForMutation(): List<RunRecord> {
        AtomicFileWriter.cleanupTemporary(file)
        if (!file.exists()) return emptyList()
        return try {
            check(file.length() <= MAX_HISTORY_BYTES) { "Stored run history is too large" }
            val content = file.readText()
            if (containsUnsupportedHistoryData(content)) {
                throw IllegalStateException("Stored run history contains newer data")
            }
            json.decodeFromString(ListSerializer(RunRecord.serializer()), content).also { records ->
                check(records.size <= MAX_RECORDS) { "Stored run history contains too many records" }
            }
        } catch (error: StackOverflowError) {
            throw RunHistoryStorageException(error)
        } catch (exception: Exception) {
            throw RunHistoryStorageException(exception)
        }
    }

    private fun containsUnsupportedHistoryData(content: String): Boolean {
        val records = json.parseToJsonElement(content) as? JsonArray ?: return false
        return records.any { element ->
            val record = element as? JsonObject ?: return@any false
            record.hasUnknownKeys(RUN_RECORD_KEYS) ||
                record.hasUnknownEnum("status", RunStatus.entries.mapTo(mutableSetOf(), RunStatus::name)) ||
                record.hasUnsupportedLocation("failedStepLocation") ||
                (record["diagnostics"] as? JsonArray).orEmpty().any { diagnosticElement ->
                    val diagnostic = diagnosticElement as? JsonObject ?: return@any false
                    diagnostic.hasUnknownKeys(RUN_DIAGNOSTIC_KEYS) ||
                        diagnostic.hasUnknownEnum(
                        "outcome",
                        RunStepOutcome.entries.mapTo(mutableSetOf(), RunStepOutcome::name),
                    ) || diagnostic.hasUnsupportedLocation("location") ||
                        diagnostic.hasUnsupportedLocation("failedStepLocation")
                }
        }
    }

    private fun JsonObject.hasUnknownEnum(key: String, knownValues: Set<String>): Boolean =
        this[key]?.jsonPrimitive?.content?.let { it !in knownValues } == true

    private fun JsonObject.hasUnknownKeys(knownKeys: Set<String>): Boolean = keys.any { it !in knownKeys }

    private fun JsonObject.hasUnsupportedLocation(key: String): Boolean {
        val location = this[key] as? JsonObject ?: return false
        if (location.hasUnknownKeys(RUN_LOCATION_KEYS)) return true
        return (location["segments"] as? JsonArray).orEmpty().any { segmentElement ->
            val segment = segmentElement as? JsonObject ?: return@any false
            segment.hasUnknownKeys(RUN_LOCATION_SEGMENT_KEYS) ||
                segment.hasUnknownEnum(
                    "branch",
                    RunStepBranch.entries.mapTo(mutableSetOf(), RunStepBranch::name),
                )
        }
    }

    private companion object {
        val FILE_LOCK = Any()
        const val FILE_NAME = "run-history.json"
        const val MAX_RECORDS = 100
        const val MAX_HISTORY_BYTES = 32L * 1024 * 1024
        val RUN_RECORD_KEYS = setOf(
            "id", "workflowId", "workflowName", "startedAtMillis", "durationMillis", "status",
            "failedStepId", "failedStepLocation", "failureMessage", "failureCode",
            "failureArguments", "diagnostics",
        )
        val RUN_DIAGNOSTIC_KEYS = setOf(
            "sequence", "stepId", "durationMillis", "attemptCount", "outcome", "location",
            "failureCode", "failureArguments", "failedStepId", "failedStepLocation",
        )
        val RUN_LOCATION_KEYS = setOf("segments")
        val RUN_LOCATION_SEGMENT_KEYS = setOf("index", "branch")
    }
}

fun RunResult.toRunRecord(
    workflow: Workflow,
    startedAtMillis: Long,
    finishedAtMillis: Long,
    diagnostics: List<StepExecutionDiagnostic> = emptyList(),
): RunRecord {
    val failed = this as? RunResult.Failed
    val notReady = this as? RunResult.NotReady
    val continuedFailure = diagnostics.firstOrNull {
        it.outcome == StepExecutionOutcome.ContinuedAfterFailure && it.error != null
    }
    val reportedStepId = failed?.stepId
        ?: continuedFailure?.failedStepId
        ?: continuedFailure?.stepId
    val reportedError = failed?.error ?: continuedFailure?.error
    return RunRecord(
        id = UUID.randomUUID().toString(),
        workflowId = workflow.id,
        workflowName = workflow.name,
        startedAtMillis = startedAtMillis,
        durationMillis = (finishedAtMillis - startedAtMillis).coerceAtLeast(0),
        status = when (this) {
            RunResult.Completed -> if (continuedFailure == null) {
                RunStatus.Completed
            } else {
                RunStatus.CompletedWithWarnings
            }
            RunResult.Cancelled -> RunStatus.Cancelled
            RunResult.AlreadyRunning -> RunStatus.Rejected
            is RunResult.NotReady -> RunStatus.Rejected
            is RunResult.Failed -> RunStatus.Failed
        },
        failedStepId = reportedStepId,
        failedStepLocation = reportedStepId?.let(workflow.steps::uniqueRunLocationTo),
        failureCode = when {
            reportedError != null -> "execution.${reportedError.code.name}"
            notReady != null -> "validation.${notReady.issue.code.name}"
            else -> null
        },
        failureArguments = reportedError?.arguments ?: notReady?.issue?.arguments.orEmpty(),
        diagnostics = diagnostics.map { diagnostic ->
            RunStepDiagnostic(
                sequence = diagnostic.sequence,
                stepId = diagnostic.stepId,
                durationMillis = diagnostic.durationMillis,
                attemptCount = diagnostic.attemptCount,
                outcome = diagnostic.outcome.toRunStepOutcome(),
                location = workflow.steps.uniqueRunLocationTo(diagnostic.stepId),
                failureCode = diagnostic.error?.let { "execution.${it.code.name}" },
                failureArguments = diagnostic.error?.arguments.orEmpty(),
                failedStepId = diagnostic.failedStepId,
                failedStepLocation = diagnostic.failedStepId?.let(workflow.steps::uniqueRunLocationTo),
            )
        },
    )
}

internal fun RunRecord.withControlNotificationCancellation(
    result: RunResult,
    controlsUnavailable: Boolean,
): RunRecord = if (result == RunResult.Cancelled && controlsUnavailable) {
    copy(failureCode = RUN_FAILURE_CONTROL_NOTIFICATION_UNAVAILABLE)
} else {
    this
}

private fun StepExecutionOutcome.toRunStepOutcome(): RunStepOutcome = when (this) {
    StepExecutionOutcome.Completed -> RunStepOutcome.Completed
    StepExecutionOutcome.ContinuedAfterFailure -> RunStepOutcome.ContinuedAfterFailure
    StepExecutionOutcome.Failed -> RunStepOutcome.Failed
    StepExecutionOutcome.Cancelled -> RunStepOutcome.Cancelled
}

internal fun List<Step>.uniqueRunLocationTo(stepId: String): RunStepLocation? {
    return runLocationsTo(stepId).singleOrNull()
}

internal fun List<Step>.runLocationsTo(stepId: String): List<RunStepLocation> =
    mutableListOf<RunStepLocation>().also { matches ->
        collectRunLocations(stepId, emptyList(), matches)
    }

private fun List<Step>.collectRunLocations(
    stepId: String,
    ancestors: List<RunStepLocationSegment>,
    matches: MutableList<RunStepLocation>,
) {
    forEachIndexed { index, step ->
        val current = ancestors + RunStepLocationSegment(index)
        if (step.id == stepId) matches += RunStepLocation(current)
        when (step) {
            is Step.Repeat -> step.steps.collectRunLocations(
                stepId,
                ancestors + RunStepLocationSegment(index, RunStepBranch.RepeatBody),
                matches,
            )
            is Step.IfElse -> {
                step.whenTrue.collectRunLocations(
                    stepId,
                    ancestors + RunStepLocationSegment(index, RunStepBranch.IfTrue),
                    matches,
                )
                step.whenFalse.collectRunLocations(
                    stepId,
                    ancestors + RunStepLocationSegment(index, RunStepBranch.IfFalse),
                    matches,
                )
            }
            else -> Unit
        }
    }
}