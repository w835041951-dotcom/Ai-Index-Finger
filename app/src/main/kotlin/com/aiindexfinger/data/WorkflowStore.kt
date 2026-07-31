package com.aiindexfinger.data

import android.content.Context
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.WorkflowState
import com.aiindexfinger.model.WorkflowValidator
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64

class WorkflowStore(context: Context) {
    private val fileStore = WorkflowFileStore(context.filesDir)

    fun load(): List<Workflow> = fileStore.load()

    fun loadLibrary(): WorkflowLibrary = fileStore.loadLibrary()

    fun loadDetailed(): WorkflowLoadResult = fileStore.loadDetailed()

    fun save(workflows: List<Workflow>) = fileStore.save(workflows)

    fun saveLibrary(library: WorkflowLibrary) = fileStore.saveLibrary(library)

    fun listVersions(workflowId: String): List<WorkflowVersion> = fileStore.listVersions(workflowId)

    fun rollback(workflowId: String, versionId: String): Workflow = fileStore.rollback(workflowId, versionId)
}

@Serializable
data class WorkflowVersion(
    val versionId: String,
    val createdAtEpochMillis: Long,
    val workflow: Workflow,
)

sealed interface WorkflowLoadResult {
    val workflows: List<Workflow>
    val library: WorkflowLibrary

    data object Missing : WorkflowLoadResult {
        override val workflows: List<Workflow> = emptyList()
        override val library: WorkflowLibrary = WorkflowLibrary()
    }

    data class Loaded(override val library: WorkflowLibrary) : WorkflowLoadResult {
        override val workflows: List<Workflow> get() = library.workflows
    }

    data class RecoveredFromBackup(override val library: WorkflowLibrary) : WorkflowLoadResult {
        override val workflows: List<Workflow> get() = library.workflows
    }

    data class UnsupportedVersion(val schemaVersion: Int) : WorkflowLoadResult {
        override val workflows: List<Workflow> = emptyList()
        override val library: WorkflowLibrary = WorkflowLibrary()
    }

    data class Corrupt(
        val primaryError: Throwable?,
        val backupError: Throwable?,
    ) : WorkflowLoadResult {
        override val workflows: List<Workflow> = emptyList()
        override val library: WorkflowLibrary = WorkflowLibrary()
    }
}

internal class WorkflowFileStore(
    directory: File,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    private val file = File(directory, FILE_NAME)
    private val backupFile = File(directory, BACKUP_FILE_NAME)
    private val versionsDirectory = File(directory, VERSIONS_DIRECTORY_NAME)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun load(): List<Workflow> = loadDetailed().workflows

    fun loadLibrary(): WorkflowLibrary = loadDetailed().library

    fun loadDetailed(): WorkflowLoadResult {
        val primary = decode(file)
        if (primary is DecodeResult.Success) {
            return primary.unsupportedVersion?.let(WorkflowLoadResult::UnsupportedVersion)
                ?: WorkflowLoadResult.Loaded(primary.library)
        }

        val backup = decode(backupFile)
        if (backup is DecodeResult.Success) {
            backup.unsupportedVersion?.let { return WorkflowLoadResult.UnsupportedVersion(it) }
            return WorkflowLoadResult.RecoveredFromBackup(backup.library)
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
        saveLibrary(loadLibrary().copy(workflows = workflows))
    }

    fun saveLibrary(library: WorkflowLibrary) {
        val loadResult = loadDetailed()
        check(loadResult !is WorkflowLoadResult.Corrupt &&
            loadResult !is WorkflowLoadResult.UnsupportedVersion
        ) {
            "Workflow files require explicit recovery before saving"
        }
        require(library.formatVersion <= WorkflowLibrary.CURRENT_FORMAT_VERSION) {
            "Workflow library format is newer than this app supports"
        }
        val normalizedLibrary = library.normalized()
        normalizedLibrary.workflows.filter { it.state == WorkflowState.Ready }.forEach { workflow ->
            val issue = WorkflowValidator.validate(workflow).firstOrNull()
            require(issue == null) { issue?.message ?: "就绪工作流无效" }
        }
        val previousById = loadResult.workflows.associateBy(Workflow::id)
        val incomingById = normalizedLibrary.workflows.associateBy(Workflow::id)
        previousById.forEach { (workflowId, previous) ->
            if (incomingById[workflowId] != previous) snapshot(previous)
        }
        writeLibrary(normalizedLibrary)
    }

    fun listVersions(workflowId: String): List<WorkflowVersion> = versionDirectory(workflowId)
        .listFiles { candidate -> candidate.isFile && candidate.extension == VERSION_FILE_EXTENSION }
        .orEmpty()
        .mapNotNull(::decodeVersion)
        .filter { it.workflow.id == workflowId }
        .sortedWith(
            compareByDescending<WorkflowVersion> { it.createdAtEpochMillis }
                .thenByDescending { versionSequence(it.versionId) },
        )

    fun rollback(workflowId: String, versionId: String): Workflow {
        val version = listVersions(workflowId).firstOrNull { it.versionId == versionId }
            ?: throw IllegalArgumentException("Workflow version is missing or corrupt")
        val loadResult = loadDetailed()
        check(loadResult !is WorkflowLoadResult.Corrupt &&
            loadResult !is WorkflowLoadResult.UnsupportedVersion
        ) {
            "Workflow files require explicit recovery before rollback"
        }
        val restored = version.workflow.let { workflow ->
            if (workflow.state == WorkflowState.Ready && WorkflowValidator.validate(workflow).isNotEmpty()) {
                workflow.copy(state = WorkflowState.Draft)
            } else {
                workflow
            }
        }
        val current = loadResult.workflows.firstOrNull { it.id == workflowId }
        if (current != null) snapshot(current)
        val currentLibrary = loadResult.library
        writeLibrary(currentLibrary.copy(workflows = currentLibrary.workflows.map { workflow ->
            if (workflow.id == workflowId) restored else workflow
        }.let { workflows ->
            if (current == null) workflows + restored else workflows
        }).normalized())
        return restored
    }

    private fun writeLibrary(library: WorkflowLibrary) {
        val content = json.encodeToString(WorkflowLibrary.serializer(), library)
        if (decode(file) is DecodeResult.Success) {
            Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        AtomicFileWriter.write(file, content)
    }

    private fun snapshot(workflow: Workflow) {
        val directory = versionDirectory(workflow.id).apply { mkdirs() }
        val timestamp = currentTimeMillis()
        val sequence = directory.listFiles().orEmpty()
            .mapNotNull { it.nameWithoutExtension.substringAfterLast('-').toIntOrNull() }
            .maxOrNull()
            ?.plus(1)
            ?: 0
        val versionId = "$timestamp-$sequence"
        val version = WorkflowVersion(versionId, timestamp, workflow)
        AtomicFileWriter.write(
            File(directory, "$versionId.$VERSION_FILE_EXTENSION"),
            json.encodeToString(WorkflowVersion.serializer(), version),
        )
        directory.listFiles { candidate ->
            candidate.isFile && candidate.extension == VERSION_FILE_EXTENSION
        }.orEmpty()
            .sortedWith(
                compareByDescending<File> { versionTimestamp(it.nameWithoutExtension) }
                    .thenByDescending { versionSequence(it.nameWithoutExtension) },
            )
            .drop(MAX_VERSIONS_PER_WORKFLOW)
            .forEach(File::delete)
    }

    private fun versionDirectory(workflowId: String): File {
        val encodedId = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(workflowId.toByteArray(Charsets.UTF_8))
        return File(versionsDirectory, encodedId)
    }

    private fun decodeVersion(source: File): WorkflowVersion? = runCatching {
        json.decodeFromString(WorkflowVersion.serializer(), source.readText())
    }.getOrNull()

    private fun versionTimestamp(versionId: String): Long = versionId.substringBefore('-').toLongOrNull() ?: Long.MIN_VALUE

    private fun versionSequence(versionId: String): Int = versionId.substringAfterLast('-').toIntOrNull() ?: Int.MIN_VALUE

    private fun decode(source: File): DecodeResult {
        if (!source.exists()) return DecodeResult.Missing
        return try {
            val root = json.parseToJsonElement(source.readText())
            when (root) {
                is JsonArray -> WorkflowLibrary(
                    workflows = json.decodeFromJsonElement(ListSerializer(Workflow.serializer()), root),
                )
                is JsonObject -> json.decodeFromJsonElement(WorkflowLibrary.serializer(), root)
                else -> error("Workflow library must be a JSON array or object")
            }.normalized().let(DecodeResult::Success)
        } catch (error: Exception) {
            DecodeResult.Failure(error)
        }
    }

    private sealed interface DecodeResult {
        data object Missing : DecodeResult
        data class Success(val library: WorkflowLibrary) : DecodeResult {
            val workflows: List<Workflow> get() = library.workflows
            val unsupportedVersion: Int?
                get() = library.formatVersion
                    .takeIf { it > WorkflowLibrary.CURRENT_FORMAT_VERSION }
                    ?: workflows.maxOfOrNull(Workflow::schemaVersion)
                        ?.takeIf { it > Workflow.CURRENT_SCHEMA_VERSION }
        }
        data class Failure(val error: Exception) : DecodeResult
    }

    private companion object {
        const val FILE_NAME = "workflows.json"
        const val BACKUP_FILE_NAME = "workflows.backup.json"
        const val VERSIONS_DIRECTORY_NAME = "workflow-versions"
        const val VERSION_FILE_EXTENSION = "json"
        const val MAX_VERSIONS_PER_WORKFLOW = 5
    }
}
