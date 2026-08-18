package com.aiindexfinger.data

import com.aiindexfinger.model.Workflow
import kotlinx.coroutines.CancellationException

internal class WorkflowImportSaveException(cause: Throwable) :
    IllegalStateException("Imported workflow library could not be saved", cause)

internal suspend fun commitLibraryUpdate(
    current: suspend () -> WorkflowLibrary,
    update: (WorkflowLibrary) -> WorkflowLibrary,
    save: suspend (WorkflowLibrary) -> Unit,
): WorkflowLibrary {
    val updated = update(current()).normalized()
    save(updated)
    return updated
}

internal data class WorkflowLibraryCommit<T>(
    val library: WorkflowLibrary,
    val cleanupResult: T?,
    val cleanupError: Exception?,
)

internal data class WorkflowRollbackCommit<T>(
    val workflow: Workflow,
    val libraryCommit: WorkflowLibraryCommit<T>,
)

internal suspend fun <T> completeWorkflowLibraryCommit(
    library: WorkflowLibrary,
    cleanup: (suspend () -> T)?,
): WorkflowLibraryCommit<T> {
    val cleanupOperation = cleanup ?: return WorkflowLibraryCommit(library, null, null)
    return try {
        WorkflowLibraryCommit(library, cleanupOperation(), null)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        WorkflowLibraryCommit(library, null, error)
    }
}

internal suspend fun <T> commitLibraryUpdateWithCleanup(
    current: suspend () -> WorkflowLibrary,
    update: (WorkflowLibrary) -> WorkflowLibrary,
    save: suspend (WorkflowLibrary) -> Unit,
    cleanup: (suspend () -> T)?,
): WorkflowLibraryCommit<T> {
    val library = commitLibraryUpdate(current, update, save)
    return completeWorkflowLibraryCommit(library, cleanup)
}

internal suspend fun <T> commitWorkflowDeletion(
    current: suspend () -> WorkflowLibrary,
    workflowId: String,
    save: suspend (WorkflowLibrary) -> Unit,
    cleanup: suspend () -> T,
): WorkflowLibraryCommit<T> = commitLibraryUpdateWithCleanup(
        current = current,
        update = { latest ->
            latest.copy(workflows = latest.workflows.filterNot { it.id == workflowId })
        },
        save = save,
        cleanup = cleanup,
    )

internal suspend fun commitImportedLibrary(
    current: suspend () -> WorkflowLibrary,
    imported: WorkflowLibrary,
    newId: () -> String,
    importedName: (String) -> String,
    save: suspend (WorkflowLibrary) -> Unit,
): WorkflowLibrary {
    try {
        return commitLibraryUpdate(
            current = current,
            update = { latest -> mergeImportedLibrary(latest, imported, newId, importedName) },
            save = save,
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        throw WorkflowImportSaveException(error)
    }
}

internal suspend fun readAndCommitImportedLibrary(
    readImported: suspend () -> WorkflowLibrary,
    current: () -> WorkflowLibrary,
    newId: () -> String,
    importedName: (String) -> String,
    save: suspend (WorkflowLibrary) -> Unit,
): WorkflowLibrary {
    val imported = readImported()
    return commitImportedLibrary(current, imported, newId, importedName, save)
}