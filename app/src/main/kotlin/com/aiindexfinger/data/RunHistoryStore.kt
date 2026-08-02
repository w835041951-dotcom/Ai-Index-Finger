package com.aiindexfinger.data

import android.content.Context
import com.aiindexfinger.executor.RunResult
import com.aiindexfinger.executor.StepExecutionDiagnostic
import com.aiindexfinger.executor.StepExecutionOutcome
import com.aiindexfinger.model.Workflow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
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
    val status: RunStatus,
    val failedStepId: String? = null,
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
    val outcome: RunStepOutcome,
)

@Serializable
enum class RunStepOutcome {
    Completed,
    ContinuedAfterFailure,
    Failed,
    Cancelled,
}

@Serializable
enum class RunStatus {
    Completed,
    Cancelled,
    Failed,
    Rejected,
}

class RunHistoryStorageException(cause: Throwable) :
    IllegalStateException("Stored run history is corrupt and cannot be modified", cause)

sealed interface RunHistoryLoadResult {
    val records: List<RunRecord>

    data object Missing : RunHistoryLoadResult {
        override val records: List<RunRecord> = emptyList()
    }

    data class Loaded(override val records: List<RunRecord>) : RunHistoryLoadResult

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
        prettyPrint = true
    }

    @Synchronized
    fun load(): List<RunRecord> = loadDetailed().records

    @Synchronized
    fun loadDetailed(): RunHistoryLoadResult {
        if (!file.exists()) return RunHistoryLoadResult.Missing
        return try {
            RunHistoryLoadResult.Loaded(
                json.decodeFromString(ListSerializer(RunRecord.serializer()), file.readText()),
            )
        } catch (exception: Exception) {
            RunHistoryLoadResult.Corrupt(exception)
        }
    }

    @Synchronized
    fun append(record: RunRecord): List<RunRecord> {
        val updated = (listOf(record) + loadForMutation()).distinctBy { it.id }.take(MAX_RECORDS)
        save(updated)
        return updated
    }

    @Synchronized
    fun clear() {
        Files.deleteIfExists(file.toPath())
    }

    private fun save(records: List<RunRecord>) {
        AtomicFileWriter.write(
            file,
            json.encodeToString(ListSerializer(RunRecord.serializer()), records),
        )
    }

    private fun loadForMutation(): List<RunRecord> {
        if (!file.exists()) return emptyList()
        return try {
            json.decodeFromString(ListSerializer(RunRecord.serializer()), file.readText())
        } catch (exception: Exception) {
            throw RunHistoryStorageException(exception)
        }
    }

    private companion object {
        const val FILE_NAME = "run-history.json"
        const val MAX_RECORDS = 100
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
    return RunRecord(
        id = UUID.randomUUID().toString(),
        workflowId = workflow.id,
        workflowName = workflow.name,
        startedAtMillis = startedAtMillis,
        durationMillis = (finishedAtMillis - startedAtMillis).coerceAtLeast(0),
        status = when (this) {
            RunResult.Completed -> RunStatus.Completed
            RunResult.Cancelled -> RunStatus.Cancelled
            RunResult.AlreadyRunning -> RunStatus.Rejected
            is RunResult.NotReady -> RunStatus.Rejected
            is RunResult.Failed -> RunStatus.Failed
        },
        failedStepId = failed?.stepId,
        failureCode = when {
            failed != null -> "execution.${failed.error.code.name}"
            notReady != null -> "validation.${notReady.issue.code.name}"
            else -> null
        },
        failureArguments = failed?.error?.arguments ?: notReady?.issue?.arguments.orEmpty(),
        diagnostics = diagnostics.map { diagnostic ->
            RunStepDiagnostic(
                sequence = diagnostic.sequence,
                stepId = diagnostic.stepId,
                durationMillis = diagnostic.durationMillis,
                attemptCount = diagnostic.attemptCount,
                outcome = diagnostic.outcome.toRunStepOutcome(),
            )
        },
    )
}

private fun StepExecutionOutcome.toRunStepOutcome(): RunStepOutcome = when (this) {
    StepExecutionOutcome.Completed -> RunStepOutcome.Completed
    StepExecutionOutcome.ContinuedAfterFailure -> RunStepOutcome.ContinuedAfterFailure
    StepExecutionOutcome.Failed -> RunStepOutcome.Failed
    StepExecutionOutcome.Cancelled -> RunStepOutcome.Cancelled
}