package com.aiindexfinger.data

import com.aiindexfinger.model.Workflow

internal fun normalizeImportedWorkflows(
    existing: List<Workflow>,
    imported: List<Workflow>,
    newId: () -> String,
): List<Workflow> {
    val usedIds = existing.mapTo(mutableSetOf()) { it.id }
    return imported.map { workflow ->
        if (usedIds.add(workflow.id)) {
            workflow
        } else {
            val replacementId = generateSequence(newId).first(usedIds::add)
            workflow.copy(id = replacementId, name = "${workflow.name} imported")
        }
    }
}

internal fun mergeImportedLibrary(
    existing: WorkflowLibrary,
    imported: WorkflowLibrary,
    newId: () -> String,
): WorkflowLibrary {
    val usedFolderIds = existing.folders.mapTo(mutableSetOf(), WorkflowFolder::id)
    val folders = existing.folders.toMutableList()
    val folderIdMap = mutableMapOf<String, String>()
    imported.folders.forEach { folder ->
        val matchingName = folders.firstOrNull { it.name.equals(folder.name, ignoreCase = true) }
        val targetId = when {
            matchingName != null -> matchingName.id
            usedFolderIds.add(folder.id) -> folder.id
            else -> generateSequence(newId).first(usedFolderIds::add)
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
            generateSequence(newId).first(usedWorkflowIds::add)
        }
        workflowIdMap[workflow.id] = targetId
        if (targetId == workflow.id) workflow else workflow.copy(id = targetId, name = "${workflow.name} imported")
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