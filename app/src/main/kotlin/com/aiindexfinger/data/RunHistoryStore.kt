package com.aiindexfinger.data

import android.content.Context
import com.aiindexfinger.executor.RunResult
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
)

@Serializable
enum class RunStatus {
    Completed,
    Cancelled,
    Failed,
    Rejected,
}

class RunHistoryStore(context: Context) {
    private val file = File(context.filesDir, FILE_NAME)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun load(): List<RunRecord> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(RunRecord.serializer()), file.readText())
        }.getOrDefault(emptyList())
    }

    fun append(records: List<RunRecord>, record: RunRecord): List<RunRecord> {
        val updated = (listOf(record) + records).take(MAX_RECORDS)
        save(updated)
        return updated
    }

    fun clear() {
        Files.deleteIfExists(file.toPath())
    }

    private fun save(records: List<RunRecord>) {
        AtomicFileWriter.write(
            file,
            json.encodeToString(ListSerializer(RunRecord.serializer()), records),
        )
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
): RunRecord {
    val failed = this as? RunResult.Failed
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
            is RunResult.Failed -> RunStatus.Failed
        },
        failedStepId = failed?.stepId,
        failureMessage = failed?.message,
    )
}