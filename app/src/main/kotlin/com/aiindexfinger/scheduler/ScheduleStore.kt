package com.aiindexfinger.scheduler

import android.content.Context
import com.aiindexfinger.data.AtomicFileWriter
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files

@Serializable
data class WorkflowSchedule(
    val workflowId: String,
    val workflowName: String,
    val scheduledAtMillis: Long,
)

class ScheduleStore(context: Context) {
    private val file = File(context.filesDir, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun load(): List<WorkflowSchedule> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(WorkflowSchedule.serializer()), file.readText())
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun put(schedule: WorkflowSchedule): List<WorkflowSchedule> {
        val updated = load().filterNot { it.workflowId == schedule.workflowId } + schedule
        save(updated)
        return updated
    }

    @Synchronized
    fun remove(workflowId: String): List<WorkflowSchedule> {
        val updated = load().filterNot { it.workflowId == workflowId }
        save(updated)
        return updated
    }

    @Synchronized
    fun removeMissingWorkflows(workflowIds: Set<String>): List<WorkflowSchedule> {
        val updated = load().filter { it.workflowId in workflowIds }
        save(updated)
        return updated
    }

    private fun save(schedules: List<WorkflowSchedule>) {
        if (schedules.isEmpty()) {
            Files.deleteIfExists(file.toPath())
            return
        }
        AtomicFileWriter.write(
            file,
            json.encodeToString(ListSerializer(WorkflowSchedule.serializer()), schedules),
        )
    }

    private companion object {
        const val FILE_NAME = "workflow-schedules.json"
    }
}