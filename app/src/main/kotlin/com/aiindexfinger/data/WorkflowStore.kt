package com.aiindexfinger.data

import android.content.Context
import com.aiindexfinger.model.Workflow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class WorkflowStore(context: Context) {
    private val fileStore = WorkflowFileStore(context.filesDir)

    fun load(): List<Workflow> = fileStore.load()

    fun save(workflows: List<Workflow>) = fileStore.save(workflows)
}

internal class WorkflowFileStore(directory: File) {
    private val file = File(directory, FILE_NAME)
    private val backupFile = File(directory, BACKUP_FILE_NAME)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun load(): List<Workflow> {
        return decode(file) ?: decode(backupFile) ?: emptyList()
    }

    fun save(workflows: List<Workflow>) {
        val content = json.encodeToString(ListSerializer(Workflow.serializer()), workflows)
        if (decode(file) != null) {
            Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        AtomicFileWriter.write(file, content)
    }

    private fun decode(source: File): List<Workflow>? {
        if (!source.exists()) return null
        return runCatching {
            json.decodeFromString(ListSerializer(Workflow.serializer()), source.readText())
        }.getOrNull()
    }

    private companion object {
        const val FILE_NAME = "workflows.json"
        const val BACKUP_FILE_NAME = "workflows.backup.json"
    }
}
