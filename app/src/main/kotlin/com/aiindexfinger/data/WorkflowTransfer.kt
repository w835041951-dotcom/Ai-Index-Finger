package com.aiindexfinger.data

import android.content.ContentResolver
import android.net.Uri
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.WorkflowState
import com.aiindexfinger.model.WorkflowValidator
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
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

object WorkflowTransferCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun encode(workflow: Workflow): String = json.encodeToString(Workflow.serializer(), workflow)

    fun encodeBundle(workflows: List<Workflow>): String = encodeLibrary(WorkflowLibrary(workflows = workflows))

    fun encodeLibrary(library: WorkflowLibrary): String {
        val normalized = library.normalized()
        require(normalized.workflows.size <= MAX_BUNDLE_WORKFLOWS) { "工作流包包含的工作流过多" }
        require(normalized.workflows.map { it.id }.distinct().size == normalized.workflows.size) {
            "工作流包包含重复的工作流 ID"
        }
        require(normalized.folders.map { it.id }.distinct().size == normalized.folders.size) {
            "工作流包包含重复的文件夹 ID"
        }
        normalized.workflows.forEach(::validate)
        return json.encodeToString(
            WorkflowBundle.serializer(),
            WorkflowBundle(
                workflows = normalized.workflows,
                folders = normalized.folders,
                workflowFolderIds = normalized.workflowFolderIds,
            ),
        )
    }

    fun decode(content: String): Workflow {
        val workflow = json.decodeFromString(Workflow.serializer(), content)
        validate(workflow)
        return workflow
    }

    fun decodeMany(content: String): List<Workflow> = decodeLibrary(content).workflows

    fun decodeLibrary(content: String): WorkflowLibrary {
        val root = json.parseToJsonElement(content)
        val objectRoot = root as? JsonObject ?: error("工作流文件必须包含 JSON 对象")
        if ("workflows" !in objectRoot) return WorkflowLibrary(workflows = listOf(decode(content)))
        val bundle = json.decodeFromJsonElement(WorkflowBundle.serializer(), objectRoot)
        require(bundle.formatVersion <= CURRENT_BUNDLE_FORMAT_VERSION) {
            "工作流包格式 ${bundle.formatVersion} 高于此应用支持的版本"
        }
        require(bundle.workflows.size <= MAX_BUNDLE_WORKFLOWS) { "工作流包包含的工作流过多" }
        require(bundle.workflows.map { it.id }.distinct().size == bundle.workflows.size) {
            "工作流包包含重复的工作流 ID"
        }
        require(bundle.folders.map { it.id }.distinct().size == bundle.folders.size) {
            "工作流包包含重复的文件夹 ID"
        }
        require(bundle.folders.all { it.name.trim().isNotEmpty() }) { "工作流包包含空白文件夹名称" }
        require(bundle.folders.map { it.name.trim().lowercase() }.distinct().size == bundle.folders.size) {
            "工作流包包含重复的文件夹名称"
        }
        bundle.workflows.forEach(::validate)
        return WorkflowLibrary(
            workflows = bundle.workflows,
            folders = bundle.folders,
            workflowFolderIds = bundle.workflowFolderIds,
        ).normalized()
    }

    private fun validate(workflow: Workflow) {
        require(workflow.schemaVersion <= Workflow.CURRENT_SCHEMA_VERSION) {
            "工作流架构版本 ${workflow.schemaVersion} 高于此应用支持的版本"
        }
        if (workflow.state == WorkflowState.Ready) {
            val issue = WorkflowValidator.validate(workflow).firstOrNull()
            require(issue == null) { issue?.message ?: "就绪工作流无效" }
        }
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
        val output = contentResolver.openOutputStream(uri, "wt")
            ?: error("无法打开所选文件进行写入")
        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(content)
        }
    }

    fun read(uri: Uri): Workflow = readMany(uri).single()

    fun readMany(uri: Uri): List<Workflow> {
        return readLibrary(uri).workflows
    }

    fun readLibrary(uri: Uri): WorkflowLibrary {
        val input = contentResolver.openInputStream(uri)
            ?: error("无法打开所选文件")
        val bytes = input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_IMPORT_BYTES) { "工作流文件大于 2 MiB" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        return WorkflowTransferCodec.decodeLibrary(bytes.toString(Charsets.UTF_8))
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 2 * 1024 * 1024
    }
}