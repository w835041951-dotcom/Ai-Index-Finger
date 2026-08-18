package com.aiindexfinger.data

import com.aiindexfinger.model.Workflow

internal const val MAX_UNIQUE_ID_ATTEMPTS = 16

private fun allocateUniqueId(
    usedIds: MutableSet<String>,
    newId: () -> String,
): String {
    repeat(MAX_UNIQUE_ID_ATTEMPTS) {
        val candidate = newId()
        if (usedIds.add(candidate)) return candidate
    }
    throw IllegalStateException("Unable to allocate a unique import ID")
}

internal fun normalizeImportedWorkflows(
    existing: List<Workflow>,
    imported: List<Workflow>,
    newId: () -> String,
    importedName: (String) -> String,
): List<Workflow> {
    val usedIds = existing.mapTo(mutableSetOf()) { it.id }
    return imported.map { workflow ->
        if (usedIds.add(workflow.id)) {
            workflow
        } else {
            val replacementId = allocateUniqueId(usedIds, newId)
            workflow.copy(id = replacementId, name = importedName(workflow.name))
        }
    }
}

internal fun mergeImportedLibrary(
    existing: WorkflowLibrary,
    imported: WorkflowLibrary,
    newId: () -> String,
    importedName: (String) -> String,
): WorkflowLibrary {
    val usedFolderIds = existing.folders.mapTo(mutableSetOf(), WorkflowFolder::id)
    val folders = existing.folders.toMutableList()
    val folderIdMap = mutableMapOf<String, String>()
    imported.folders.forEach { folder ->
        val matchingName = folders.firstOrNull { it.name.equals(folder.name, ignoreCase = true) }
        val targetId = when {
            matchingName != null -> matchingName.id
            usedFolderIds.add(folder.id) -> folder.id
            else -> allocateUniqueId(usedFolderIds, newId)
        }
        folderIdMap[folder.id] = targetId
        if (folders.none { it.id == targetId }) folders += folder.copy(id = targetId)
    }

    val usedWorkflowIds = existing.workflows.mapTo(mutableSetOf(), Workflow::id)
    val workflowIdMap = mutableMapOf<String, String>()
    val importedWorkflows = imported.workflows.map { workflow ->
        val targetId = if (usedWorkflowIds.add(workflow.id)) {
            workflow.id
        } else {
            allocateUniqueId(usedWorkflowIds, newId)
        }
        workflowIdMap[workflow.id] = targetId
        if (targetId == workflow.id) workflow else workflow.copy(
            id = targetId,
            name = importedName(workflow.name),
        )
    }
    val importedAssignments = imported.workflowFolderIds.mapNotNull { (workflowId, folderId) ->
        val targetWorkflowId = workflowIdMap[workflowId] ?: return@mapNotNull null
        val targetFolderId = folderIdMap[folderId] ?: return@mapNotNull null
        targetWorkflowId to targetFolderId
    }.toMap()
    return WorkflowLibrary(
        workflows = existing.workflows + importedWorkflows,
        folders = folders,
        workflowFolderIds = existing.workflowFolderIds + importedAssignments,
    ).normalized()
}
