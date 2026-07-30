package com.aiindexfinger.data

import android.content.Context
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.WorkflowState
import com.aiindexfinger.model.WorkflowValidator
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class WorkflowStore(context: Context) {
    private val fileStore = WorkflowFileStore(context.filesDir)

    fun load(): List<Workflow> = fileStore.load()

    fun loadDetailed(): WorkflowLoadResult = fileStore.loadDetailed()

    fun save(workflows: List<Workflow>) = fileStore.save(workflows)
}

sealed interface WorkflowLoadResult {
    val workflows: List<Workflow>

    data object Missing : WorkflowLoadResult {
        override val workflows: List<Workflow> = emptyList()
    }

    data class Loaded(override val workflows: List<Workflow>) : WorkflowLoadResult

    data class RecoveredFromBackup(override val workflows: List<Workflow>) : WorkflowLoadResult

    data class UnsupportedVersion(val schemaVersion: Int) : WorkflowLoadResult {
        override val workflows: List<Workflow> = emptyList()
    }

    data class Corrupt(
        val primaryError: Throwable?,
        val backupError: Throwable?,
    ) : WorkflowLoadResult {
        override val workflows: List<Workflow> = emptyList()
    }
}

internal class WorkflowFileStore(directory: File) {
    private val file = File(directory, FILE_NAME)
    private val backupFile = File(directory, BACKUP_FILE_NAME)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun load(): List<Workflow> = loadDetailed().workflows

    fun loadDetailed(): WorkflowLoadResult {
        val primary = decode(file)
        if (primary is DecodeResult.Success) {
            return primary.unsupportedVersion?.let(WorkflowLoadResult::UnsupportedVersion)
                ?: WorkflowLoadResult.Loaded(primary.workflows)
        }

        val backup = decode(backupFile)
        if (backup is DecodeResult.Success) {
            backup.unsupportedVersion?.let { return WorkflowLoadResult.UnsupportedVersion(it) }
            return WorkflowLoadResult.RecoveredFromBackup(backup.workflows)
        }
        if (primary is DecodeResult.Missing && backup is DecodeResult.Missing) {
            return WorkflowLoadResult.Missing
        }
        return WorkflowLoadResult.Corrupt(
            primaryError = (primary as? DecodeResult.Failure)?.error,
            backupError = (backup as? DecodeResult.Failure)?.error,
        )
    }

    fun save(workflows: List<Workflow>) {
        val loadResult = loadDetailed()
        check(loadResult !is WorkflowLoadResult.Corrupt &&
            loadResult !is WorkflowLoadResult.UnsupportedVersion
        ) {
            "Workflow files require explicit recovery before saving"
        }
        workflows.filter { it.state == WorkflowState.Ready }.forEach { workflow ->
            val issue = WorkflowValidator.validate(workflow).firstOrNull()
            require(issue == null) { issue?.message ?: "就绪工作流无效" }
        }
        val content = json.encodeToString(ListSerializer(Workflow.serializer()), workflows)
        if (decode(file) is DecodeResult.Success) {
            Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        AtomicFileWriter.write(file, content)
    }

    private fun decode(source: File): DecodeResult {
        if (!source.exists()) return DecodeResult.Missing
        return try {
            json.decodeFromString(ListSerializer(Workflow.serializer()), source.readText())
                .let(DecodeResult::Success)
        } catch (error: Exception) {
            DecodeResult.Failure(error)
        }
    }

    private sealed interface DecodeResult {
        data object Missing : DecodeResult
        data class Success(val workflows: List<Workflow>) : DecodeResult {
            val unsupportedVersion: Int?
                get() = workflows.maxOfOrNull(Workflow::schemaVersion)
                    ?.takeIf { it > Workflow.CURRENT_SCHEMA_VERSION }
        }
        data class Failure(val error: Exception) : DecodeResult
    }

    private companion object {
        const val FILE_NAME = "workflows.json"
        const val BACKUP_FILE_NAME = "workflows.backup.json"
    }
}
