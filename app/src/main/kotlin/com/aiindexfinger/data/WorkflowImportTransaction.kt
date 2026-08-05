package com.aiindexfinger.data

internal suspend fun commitImportedLibrary(
    current: WorkflowLibrary,
    imported: WorkflowLibrary,
    newId: () -> String,
    save: suspend (WorkflowLibrary) -> Unit,
): WorkflowLibrary {
    val merged = mergeImportedLibrary(current, imported, newId)
    save(merged)
    return merged
}

internal suspend fun readAndCommitImportedLibrary(
    readImported: suspend () -> WorkflowLibrary,
    current: () -> WorkflowLibrary,
    newId: () -> String,
    save: suspend (WorkflowLibrary) -> Unit,
): WorkflowLibrary {
    val imported = readImported()
    return commitImportedLibrary(current(), imported, newId, save)
}