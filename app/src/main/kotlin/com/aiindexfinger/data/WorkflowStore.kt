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
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
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
        cleanupTemporaryFiles()
        val primary = decode(file)
        if (primary is DecodeResult.Unsupported) {
            return WorkflowLoadResult.UnsupportedVersion(primary.version)
        }
        if (primary is DecodeResult.Success) {
            return WorkflowLoadResult.Loaded(primary.library)
        }

        val backup = decode(backupFile)
        if (backup is DecodeResult.Unsupported) {
            return WorkflowLoadResult.UnsupportedVersion(backup.version)
        }
        if (backup is DecodeResult.Success) {
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

    private fun cleanupTemporaryFiles() {
        AtomicFileWriter.cleanupTemporary(file)
        AtomicFileWriter.cleanupTemporary(backupFile)
        versionsDirectory.listFiles { candidate -> candidate.isDirectory }.orEmpty()
            .flatMap { directory ->
                directory.listFiles { candidate ->
                    candidate.isFile && candidate.name.endsWith(".$VERSION_FILE_EXTENSION.tmp")
                }.orEmpty().asList()
            }
            .forEach { temporaryFile ->
                AtomicFileWriter.cleanupTemporary(
                    File(temporaryFile.parentFile, temporaryFile.name.removeSuffix(".tmp")),
                )
            }
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
        requireUniqueLibraryIds(normalizedLibrary)
        normalizedLibrary.workflows.forEach { workflow ->
            require(WorkflowValidator.structuralIssues(workflow).isEmpty()) {
                "Workflow structure is unsafe"
            }
        }
        normalizedLibrary.workflows.filter { it.state == WorkflowState.Ready }.forEach { workflow ->
            val issue = WorkflowValidator.validate(workflow).firstOrNull()
            require(issue == null) { issue?.code?.name ?: "Invalid ready workflow" }
        }
        val previousById = loadResult.workflows.associateBy(Workflow::id)
        val incomingById = normalizedLibrary.workflows.associateBy(Workflow::id)
        val removedWorkflowIds = previousById.keys - incomingById.keys
        previousById.forEach { (workflowId, previous) ->
            if (workflowId !in removedWorkflowIds && incomingById[workflowId] != previous) {
                snapshot(previous)
            }
        }
        removedWorkflowIds.forEach { workflowId ->
            check(versionDirectory(workflowId).deleteRecursively()) {
                "Workflow version history could not be deleted"
            }
        }
        val sanitizedBackup = loadResult.library.takeIf { removedWorkflowIds.isNotEmpty() }?.copy(
            workflows = loadResult.workflows.filterNot { it.id in removedWorkflowIds },
            workflowFolderIds = loadResult.library.workflowFolderIds - removedWorkflowIds,
        )?.normalized()
        writeLibrary(normalizedLibrary, sanitizedBackup)
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

    private fun writeLibrary(library: WorkflowLibrary, backupLibrary: WorkflowLibrary? = null) {
        val content = json.encodeToString(WorkflowLibrary.serializer(), library)
        require(content.toByteArray(Charsets.UTF_8).size <= MAX_LIBRARY_BYTES) {
            "Workflow library is too large"
        }
        if (backupLibrary != null) {
            val backupContent = json.encodeToString(WorkflowLibrary.serializer(), backupLibrary)
            require(backupContent.toByteArray(Charsets.UTF_8).size <= MAX_LIBRARY_BYTES) {
                "Workflow backup is too large"
            }
            AtomicFileWriter.write(
                backupFile,
                backupContent,
            )
        } else if (decode(file) is DecodeResult.Success) {
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
        val content = json.encodeToString(WorkflowVersion.serializer(), version)
        require(content.toByteArray(Charsets.UTF_8).size <= MAX_VERSION_BYTES) {
            "Workflow version is too large"
        }
        AtomicFileWriter.write(
            File(directory, "$versionId.$VERSION_FILE_EXTENSION"),
            content,
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

    private fun decodeVersion(source: File): WorkflowVersion? {
        if (source.length() > MAX_VERSION_BYTES) return null
        return runCatching {
            json.decodeFromString(WorkflowVersion.serializer(), source.readText())
        }.getOrNull()
    }

    private fun versionTimestamp(versionId: String): Long = versionId.substringBefore('-').toLongOrNull() ?: Long.MIN_VALUE

    private fun versionSequence(versionId: String): Int = versionId.substringAfterLast('-').toIntOrNull() ?: Int.MIN_VALUE

    private fun decode(source: File): DecodeResult {
        if (!source.exists()) return DecodeResult.Missing
        if (source.length() > MAX_LIBRARY_BYTES) {
            return DecodeResult.Failure(IllegalStateException("Workflow library is too large"))
        }
        return try {
            val root = json.parseToJsonElement(source.readText())
            declaredUnsupportedVersion(root)?.let { return DecodeResult.Unsupported(it) }
            when (root) {
                is JsonArray -> WorkflowLibrary(
                    workflows = json.decodeFromJsonElement(ListSerializer(Workflow.serializer()), root),
                )
                is JsonObject -> json.decodeFromJsonElement(WorkflowLibrary.serializer(), root)
                else -> error("Workflow library must be a JSON array or object")
            }.normalized().also { library ->
                requireUniqueLibraryIds(library)
                require(library.workflows.all { WorkflowValidator.structuralIssues(it).isEmpty() }) {
                    "Workflow structure is unsafe"
                }
            }.let(DecodeResult::Success)
        } catch (error: StackOverflowError) {
            DecodeResult.Failure(error)
        } catch (error: Exception) {
            DecodeResult.Failure(error)
        }
    }

    private sealed interface DecodeResult {
        data object Missing : DecodeResult
        data class Unsupported(val version: Int) : DecodeResult
        data class Success(val library: WorkflowLibrary) : DecodeResult
        data class Failure(val error: Throwable) : DecodeResult
    }

    private fun declaredUnsupportedVersion(root: kotlinx.serialization.json.JsonElement): Int? {
        if (root is JsonObject) {
            root["formatVersion"]?.jsonPrimitive?.intOrNull
                ?.takeIf { it > WorkflowLibrary.CURRENT_FORMAT_VERSION }
                ?.let { return it }
        }
        val workflows = when (root) {
            is JsonArray -> root
            is JsonObject -> root["workflows"] as? JsonArray
            else -> null
        } ?: return null
        return workflows.mapNotNull { element ->
            (element as? JsonObject)?.get("schemaVersion")?.jsonPrimitive?.intOrNull
        }.maxOrNull()?.takeIf { it > Workflow.CURRENT_SCHEMA_VERSION }
    }

    private fun requireUniqueLibraryIds(library: WorkflowLibrary) {
        require(library.workflows.map(Workflow::id).distinct().size == library.workflows.size) {
            "Workflow IDs must be unique"
        }
        require(library.folders.map(WorkflowFolder::id).distinct().size == library.folders.size) {
            "Folder IDs must be unique"
        }
    }

    private companion object {
        const val FILE_NAME = "workflows.json"
        const val BACKUP_FILE_NAME = "workflows.backup.json"
        const val VERSIONS_DIRECTORY_NAME = "workflow-versions"
        const val VERSION_FILE_EXTENSION = "json"
        const val MAX_VERSIONS_PER_WORKFLOW = 5
        const val MAX_LIBRARY_BYTES = 64L * 1024 * 1024
        const val MAX_VERSION_BYTES = 4L * 1024 * 1024
    }
}
