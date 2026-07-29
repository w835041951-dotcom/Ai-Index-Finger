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
)

private const val CURRENT_BUNDLE_FORMAT_VERSION = 1
private const val MAX_BUNDLE_WORKFLOWS = 1_000

object WorkflowTransferCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun encode(workflow: Workflow): String = json.encodeToString(Workflow.serializer(), workflow)

    fun encodeBundle(workflows: List<Workflow>): String {
        require(workflows.size <= MAX_BUNDLE_WORKFLOWS) { "Bundle contains too many workflows" }
        require(workflows.map { it.id }.distinct().size == workflows.size) {
            "Bundle contains duplicate workflow IDs"
        }
        workflows.forEach(::validate)
        return json.encodeToString(WorkflowBundle.serializer(), WorkflowBundle(workflows = workflows))
    }

    fun decode(content: String): Workflow {
        val workflow = json.decodeFromString(Workflow.serializer(), content)
        validate(workflow)
        return workflow
    }

    fun decodeMany(content: String): List<Workflow> {
        val root = json.parseToJsonElement(content)
        val objectRoot = root as? JsonObject ?: error("Workflow file must contain a JSON object")
        if ("workflows" !in objectRoot) return listOf(decode(content))
        val bundle = json.decodeFromJsonElement(WorkflowBundle.serializer(), objectRoot)
        require(bundle.formatVersion <= CURRENT_BUNDLE_FORMAT_VERSION) {
            "Bundle format ${bundle.formatVersion} is newer than this app supports"
        }
        require(bundle.workflows.size <= MAX_BUNDLE_WORKFLOWS) { "Bundle contains too many workflows" }
        require(bundle.workflows.map { it.id }.distinct().size == bundle.workflows.size) {
            "Bundle contains duplicate workflow IDs"
        }
        bundle.workflows.forEach(::validate)
        return bundle.workflows
    }

    private fun validate(workflow: Workflow) {
        require(workflow.schemaVersion <= Workflow.CURRENT_SCHEMA_VERSION) {
            "Workflow schema ${workflow.schemaVersion} is newer than this app supports"
        }
        if (workflow.state == WorkflowState.Ready) {
            val issue = WorkflowValidator.validate(workflow).firstOrNull()
            require(issue == null) { issue?.message ?: "Ready workflow is invalid" }
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

    private fun writeContent(uri: Uri, content: String) {
        val output = contentResolver.openOutputStream(uri, "wt")
            ?: error("The selected file cannot be opened for writing")
        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(content)
        }
    }

    fun read(uri: Uri): Workflow = readMany(uri).single()

    fun readMany(uri: Uri): List<Workflow> {
        val input = contentResolver.openInputStream(uri)
            ?: error("The selected file cannot be opened")
        val bytes = input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_IMPORT_BYTES) { "Workflow file is larger than 2 MiB" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        return WorkflowTransferCodec.decodeMany(bytes.toString(Charsets.UTF_8))
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 2 * 1024 * 1024
    }
}