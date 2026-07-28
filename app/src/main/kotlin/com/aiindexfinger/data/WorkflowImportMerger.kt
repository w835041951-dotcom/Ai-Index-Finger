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