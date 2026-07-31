package com.aiindexfinger.data

import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.matchesSearch
import java.text.Collator
import java.util.Locale
import kotlinx.serialization.Serializable

@Serializable
data class WorkflowFolder(
    val id: String,
    val name: String,
)

@Serializable
data class WorkflowLibrary(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val workflows: List<Workflow> = emptyList(),
    val folders: List<WorkflowFolder> = emptyList(),
    val workflowFolderIds: Map<String, String> = emptyMap(),
) {
    fun normalized(): WorkflowLibrary {
        val workflowIds = workflows.mapTo(mutableSetOf(), Workflow::id)
        val folderIds = folders.mapTo(mutableSetOf(), WorkflowFolder::id)
        return copy(
            workflowFolderIds = workflowFolderIds.filter { (workflowId, folderId) ->
                workflowId in workflowIds && folderId in folderIds
            },
        )
    }

    fun withFolder(folder: WorkflowFolder): WorkflowLibrary {
        val normalizedName = normalizeFolderName(folder.name)
        require(folders.none { it.id != folder.id && it.name.equals(normalizedName, ignoreCase = true) }) {
            "Folder names must be unique"
        }
        return copy(
            folders = folders.filterNot { it.id == folder.id } + folder.copy(name = normalizedName),
        )
    }

    fun withoutFolder(folderId: String): WorkflowLibrary = copy(
        folders = folders.filterNot { it.id == folderId },
        workflowFolderIds = workflowFolderIds.filterValues { it != folderId },
    )

    fun moveWorkflow(workflowId: String, folderId: String?): WorkflowLibrary {
        require(workflows.any { it.id == workflowId }) { "Workflow does not exist" }
        require(folderId == null || folders.any { it.id == folderId }) { "Folder does not exist" }
        if (folderIdFor(workflowId) == folderId) return this
        return copy(
            workflowFolderIds = if (folderId == null) {
                workflowFolderIds - workflowId
            } else {
                workflowFolderIds + (workflowId to folderId)
            },
        )
    }

    fun folderIdFor(workflowId: String): String? = workflowFolderIds[workflowId]
        ?.takeIf { folderId -> folders.any { it.id == folderId } }

    companion object {
        const val CURRENT_FORMAT_VERSION = 1

        fun normalizeFolderName(name: String): String = name.trim().also {
            require(it.isNotEmpty()) { "Folder name cannot be blank" }
        }
    }
}

sealed interface WorkflowFolderSelection {
    data object All : WorkflowFolderSelection
    data object Unfiled : WorkflowFolderSelection
    data class Folder(val id: String) : WorkflowFolderSelection
}

fun sortedFolders(
    folders: List<WorkflowFolder>,
    locale: Locale = Locale.getDefault(),
): List<WorkflowFolder> {
    val collator = primaryCollator(locale)
    return folders.sortedWith { left, right ->
        compareDisplayText(collator, left.name, right.name)
            ?: left.id.compareTo(right.id)
    }
}

fun filterWorkflows(
    workflows: List<Workflow>,
    workflowFolderIds: Map<String, String>,
    query: String,
    selection: WorkflowFolderSelection,
    locale: Locale = Locale.getDefault(),
): List<Workflow> {
    val collator = primaryCollator(locale)
    return workflows.filter { workflow ->
        val matchesFolder = when (selection) {
            WorkflowFolderSelection.All -> true
            WorkflowFolderSelection.Unfiled -> workflowFolderIds[workflow.id] == null
            is WorkflowFolderSelection.Folder -> workflowFolderIds[workflow.id] == selection.id
        }
        matchesFolder && workflow.matchesSearch(query)
    }
        .sortedWith { left, right ->
            compareDisplayText(collator, left.name, right.name)
                ?: left.id.compareTo(right.id)
        }
}

private fun primaryCollator(locale: Locale): Collator =
    Collator.getInstance(locale).apply { strength = Collator.PRIMARY }

private fun compareDisplayText(collator: Collator, left: String, right: String): Int? =
    collator.compare(left, right).takeIf { it != 0 }
        ?: left.compareTo(right).takeIf { it != 0 }