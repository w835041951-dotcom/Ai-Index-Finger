package com.aiindexfinger.data

import android.content.ContentResolver
import android.net.Uri
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.WorkflowState
import com.aiindexfinger.model.WorkflowValidator
import com.aiindexfinger.model.ValidationIssue
import com.aiindexfinger.model.normalizedForCurrentSchema
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream

@Serializable
private data class WorkflowBundle(
    val formatVersion: Int = CURRENT_BUNDLE_FORMAT_VERSION,
    val workflows: List<Workflow>,
    val folders: List<WorkflowFolder> = emptyList(),
    val workflowFolderIds: Map<String, String> = emptyMap(),
)

private const val CURRENT_BUNDLE_FORMAT_VERSION = 2
private const val MAX_BUNDLE_WORKFLOWS = 1_000
private const val MAX_BUNDLE_FOLDERS = 1_000
private const val MAX_TRANSFER_BYTES = 2 * 1024 * 1024

class InvalidWorkflowException(val issue: ValidationIssue) : IllegalArgumentException(issue.code.name)

enum class WorkflowTransferErrorCode {
    InvalidContent,
    TooManyWorkflows,
    TooManyFolders,
    DuplicateWorkflowIds,
    DuplicateFolderIds,
    RootNotObject,
    UnsupportedBundleVersion,
    BlankFolderName,
    DuplicateFolderNames,
    UnsupportedWorkflowVersion,
    FileUnavailable,
    FileTooLarge,
}

class WorkflowTransferException(
    val code: WorkflowTransferErrorCode,
    val arguments: Map<String, String> = emptyMap(),
    cause: Throwable? = null,
) : IllegalArgumentException(code.name, cause)

private fun transferRequire(
    condition: Boolean,
    code: WorkflowTransferErrorCode,
    arguments: Map<String, String> = emptyMap(),
) {
    if (!condition) throw WorkflowTransferException(code, arguments)
}

internal fun <T : Any> transferFileAccess(open: () -> T?): T = try {
    open() ?: throw WorkflowTransferException(WorkflowTransferErrorCode.FileUnavailable)
} catch (error: WorkflowTransferException) {
    throw error
} catch (error: Exception) {
    throw WorkflowTransferException(WorkflowTransferErrorCode.FileUnavailable, cause = error)
}

object WorkflowTransferCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun encode(workflow: Workflow): String {
        validate(workflow)
        return enforceTransferSize(
            json.encodeToString(Workflow.serializer(), workflow.normalizedForCurrentSchema()),
        )
    }

    fun encodeBundle(workflows: List<Workflow>): String = encodeLibrary(WorkflowLibrary(workflows = workflows))

    fun encodeLibrary(library: WorkflowLibrary): String {
        val normalizedInput = library.normalized()
        normalizedInput.workflows.forEach(::validate)
        val normalized = normalizedInput.copy(
            workflows = normalizedInput.workflows.map(Workflow::normalizedForCurrentSchema),
        )
        transferRequire(
            normalized.workflows.size <= MAX_BUNDLE_WORKFLOWS,
            WorkflowTransferErrorCode.TooManyWorkflows,
        )
        transferRequire(
            normalized.folders.size <= MAX_BUNDLE_FOLDERS,
            WorkflowTransferErrorCode.TooManyFolders,
        )
        transferRequire(
            normalized.workflows.map { it.id }.distinct().size == normalized.workflows.size,
            WorkflowTransferErrorCode.DuplicateWorkflowIds,
        )
        transferRequire(
            normalized.folders.map { it.id }.distinct().size == normalized.folders.size,
            WorkflowTransferErrorCode.DuplicateFolderIds,
        )
        normalized.workflows.forEach(::validate)
        return enforceTransferSize(json.encodeToString(
            WorkflowBundle.serializer(),
            WorkflowBundle(
                workflows = normalized.workflows,
                folders = normalized.folders,
                workflowFolderIds = normalized.workflowFolderIds,
            ),
        ))
    }

    fun decode(content: String): Workflow {
        requireImportSize(content)
        val root = parseObject(content)
        requireSupportedWorkflowVersion(root)
        val workflow = runCatching {
            json.decodeFromJsonElement(Workflow.serializer(), root)
        }.getOrElse { throw WorkflowTransferException(WorkflowTransferErrorCode.InvalidContent, cause = it) }
        val normalized = workflow.normalizedForCurrentSchema().copy(name = workflow.name.trim())
        validate(normalized)
        return normalized
    }

    fun decodeMany(content: String): List<Workflow> = decodeLibrary(content).workflows

    fun decodeLibrary(content: String): WorkflowLibrary {
        requireImportSize(content)
        val root = runCatching { json.parseToJsonElement(content) }
            .getOrElse { throw WorkflowTransferException(WorkflowTransferErrorCode.InvalidContent, cause = it) }
        val objectRoot = root as? JsonObject
            ?: throw WorkflowTransferException(WorkflowTransferErrorCode.RootNotObject)
        objectRoot["formatVersion"]?.jsonPrimitive?.intOrNull?.let { version ->
            transferRequire(
                version <= CURRENT_BUNDLE_FORMAT_VERSION,
                WorkflowTransferErrorCode.UnsupportedBundleVersion,
                mapOf("version" to version.toString()),
            )
        }
        if ("workflows" !in objectRoot) return WorkflowLibrary(workflows = listOf(decode(content)))
        (objectRoot["workflows"] as? JsonArray).orEmpty().forEach { element ->
            (element as? JsonObject)?.let(::requireSupportedWorkflowVersion)
        }
        val bundle = runCatching { json.decodeFromJsonElement(WorkflowBundle.serializer(), objectRoot) }
            .getOrElse { throw WorkflowTransferException(WorkflowTransferErrorCode.InvalidContent, cause = it) }
        transferRequire(
            bundle.formatVersion <= CURRENT_BUNDLE_FORMAT_VERSION,
            WorkflowTransferErrorCode.UnsupportedBundleVersion,
            mapOf("version" to bundle.formatVersion.toString()),
        )
        transferRequire(bundle.workflows.size <= MAX_BUNDLE_WORKFLOWS, WorkflowTransferErrorCode.TooManyWorkflows)
        transferRequire(bundle.folders.size <= MAX_BUNDLE_FOLDERS, WorkflowTransferErrorCode.TooManyFolders)
        transferRequire(
            bundle.workflows.map { it.id }.distinct().size == bundle.workflows.size,
            WorkflowTransferErrorCode.DuplicateWorkflowIds,
        )
        transferRequire(
            bundle.folders.map { it.id }.distinct().size == bundle.folders.size,
            WorkflowTransferErrorCode.DuplicateFolderIds,
        )
        transferRequire(
            bundle.folders.all { it.name.trim().isNotEmpty() },
            WorkflowTransferErrorCode.BlankFolderName,
        )
        transferRequire(
            bundle.folders.map { it.name.trim().lowercase() }.distinct().size == bundle.folders.size,
            WorkflowTransferErrorCode.DuplicateFolderNames,
        )
        val normalizedWorkflows = bundle.workflows.map { workflow ->
            workflow.normalizedForCurrentSchema().copy(name = workflow.name.trim())
        }
        normalizedWorkflows.forEach(::validate)
        return WorkflowLibrary(
            workflows = normalizedWorkflows,
            folders = bundle.folders.map { folder -> folder.copy(name = folder.name.trim()) },
            workflowFolderIds = bundle.workflowFolderIds,
        ).normalized()
    }

    private fun validate(workflow: Workflow) {
        transferRequire(
            workflow.schemaVersion <= Workflow.CURRENT_SCHEMA_VERSION,
            WorkflowTransferErrorCode.UnsupportedWorkflowVersion,
            mapOf("version" to workflow.schemaVersion.toString()),
        )
        val issues = WorkflowValidator.validate(workflow)
        val issue = if (workflow.state == WorkflowState.Ready) {
            issues.firstOrNull()
        } else {
            WorkflowValidator.structuralIssues(workflow).firstOrNull()
        }
        if (issue != null) throw InvalidWorkflowException(issue)
    }

    private fun parseObject(content: String): JsonObject = runCatching {
        json.parseToJsonElement(content) as? JsonObject
    }.getOrNull() ?: throw WorkflowTransferException(WorkflowTransferErrorCode.InvalidContent)

    private fun requireSupportedWorkflowVersion(root: JsonObject) {
        root["schemaVersion"]?.jsonPrimitive?.intOrNull?.let { version ->
            transferRequire(
                version <= Workflow.CURRENT_SCHEMA_VERSION,
                WorkflowTransferErrorCode.UnsupportedWorkflowVersion,
                mapOf("version" to version.toString()),
            )
        }
    }

    private fun enforceTransferSize(content: String): String {
        transferRequire(
            content.toByteArray(Charsets.UTF_8).size <= MAX_TRANSFER_BYTES,
            WorkflowTransferErrorCode.FileTooLarge,
        )
        return content
    }

    private fun requireImportSize(content: String) {
        transferRequire(
            content.toByteArray(Charsets.UTF_8).size <= MAX_TRANSFER_BYTES,
            WorkflowTransferErrorCode.FileTooLarge,
        )
    }
}

class WorkflowTransfer(
    private val contentResolver: ContentResolver,
) {
    fun write(uri: Uri, workflow: Workflow) {
        writeContent(uri, WorkflowTransferCodec.encode(workflow))
    }

    fun writeBundle(uri: Uri, workflows: List<Workflow>) {
        writeContent(uri, WorkflowTransferCodec.encodeBundle(workflows))
    }

    fun writeLibrary(uri: Uri, library: WorkflowLibrary) {
        writeContent(uri, WorkflowTransferCodec.encodeLibrary(library))
    }

    private fun writeContent(uri: Uri, content: String) {
        val output = transferFileAccess { contentResolver.openOutputStream(uri, "wt") }
        try {
            output.bufferedWriter(Charsets.UTF_8).use { writer -> writer.write(content) }
        } catch (error: Exception) {
            throw WorkflowTransferException(WorkflowTransferErrorCode.FileUnavailable, cause = error)
        }
    }

    fun read(uri: Uri): Workflow = readMany(uri).single()

    fun readMany(uri: Uri): List<Workflow> {
        return readLibrary(uri).workflows
    }

    fun readLibrary(uri: Uri): WorkflowLibrary {
        val input = transferFileAccess { contentResolver.openInputStream(uri) }
        val bytes = try {
            input.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    transferRequire(
                        output.size() + count <= MAX_TRANSFER_BYTES,
                        WorkflowTransferErrorCode.FileTooLarge,
                    )
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } catch (error: WorkflowTransferException) {
            throw error
        } catch (error: Exception) {
            throw WorkflowTransferException(WorkflowTransferErrorCode.FileUnavailable, cause = error)
        }
        return WorkflowTransferCodec.decodeLibrary(bytes.toString(Charsets.UTF_8))
    }

}