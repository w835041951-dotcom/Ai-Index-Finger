package com.aiindexfinger.data

import com.aiindexfinger.model.Step
import com.aiindexfinger.model.Workflow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowImportTransactionTest {
    @Test
    fun mergedLibraryIsNotReturnedBeforeSaveCompletes() = runBlocking {
        val saveStarted = CompletableDeferred<Unit>()
        val allowSave = CompletableDeferred<Unit>()
        val current = library("existing")
        val imported = library("imported")
        val result = async {
            commitImportedLibrary(
                current = { current },
                imported,
                newId = { "replacement" },
                importedName = { "$it (imported)" },
            ) {
                saveStarted.complete(Unit)
                allowSave.await()
            }
        }

        saveStarted.await()
        assertFalse(result.isCompleted)

        allowSave.complete(Unit)
        assertEquals(listOf("existing", "imported"), result.await().workflows.map(Workflow::id))
    }

    @Test
    fun saveFailureReturnsNoMergedLibraryAndLeavesCurrentUnchanged() = runBlocking {
        val current = library("existing")
        val imported = library("imported")

        val failure = runCatching {
            commitImportedLibrary(
                current = { current },
                imported,
                newId = { "replacement" },
                importedName = { "$it (imported)" },
            ) { throw IllegalStateException("disk full") }
        }.exceptionOrNull()

        assertTrue(failure is WorkflowImportSaveException)
        assertEquals(listOf("existing"), current.workflows.map(Workflow::id))
    }

    @Test
    fun currentLibraryIsCapturedAfterImportReadCompletes() = runBlocking {
        val allowRead = CompletableDeferred<Unit>()
        var current = library("existing")
        val result = async {
            readAndCommitImportedLibrary(
                readImported = {
                    allowRead.await()
                    library("imported")
                },
                current = { current },
                newId = { "replacement" },
                importedName = { "$it (imported)" },
                save = {},
            )
        }

        current = WorkflowLibrary(
            workflows = current.workflows + library("edited").workflows,
        )
        allowRead.complete(Unit)

        assertEquals(
            listOf("existing", "edited", "imported"),
            result.await().workflows.map(Workflow::id),
        )
    }

    @Test
    fun queuedImportMergesAfterThePrecedingEditorSave() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
        val coordinator = WorkflowPersistenceCoordinator(scope)
        val editStarted = CompletableDeferred<Unit>()
        val allowEdit = CompletableDeferred<Unit>()
        var stored = library("existing")
        try {
            val edit = coordinator.submit {
                editStarted.complete(Unit)
                allowEdit.await()
                stored = WorkflowLibrary(workflows = stored.workflows + library("edited").workflows)
            }
            editStarted.await()
            val import = coordinator.submit {
                commitImportedLibrary(
                    current = { stored },
                    imported = library("imported"),
                    newId = { "replacement" },
                    importedName = { "$it (imported)" },
                    save = { stored = it },
                )
            }

            allowEdit.complete(Unit)
            edit.await()

            assertEquals(
                listOf("existing", "edited", "imported"),
                import.await().workflows.map(Workflow::id),
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun workflowDeletionSavesBeforeCleanup() = runBlocking {
        var stored = library("existing")
        val events = mutableListOf<String>()

        val result = commitWorkflowDeletion(
            current = { stored },
            workflowId = "existing",
            save = {
                events += "save"
                stored = it
            },
            cleanup = {
                assertTrue(stored.workflows.isEmpty())
                events += "cleanup"
                "cleaned"
            },
        )

        assertEquals(listOf("save", "cleanup"), events)
        assertEquals(emptyList<Workflow>(), result.library.workflows)
        assertEquals("cleaned", result.cleanupResult)
        assertNull(result.cleanupError)
    }

    @Test
    fun libraryUpdateCanCommitWithoutCleanup() = runBlocking {
        val current = library("existing")

        val result = commitLibraryUpdateWithCleanup<Unit>(
            current = { current },
            update = { it.withWorkflow(workflow("ready")) },
            save = {},
            cleanup = null,
        )

        assertEquals(listOf("existing", "ready"), result.library.workflows.map(Workflow::id))
        assertNull(result.cleanupResult)
        assertNull(result.cleanupError)
    }

    @Test
    fun failedWorkflowDeletionNeverCleansUpSchedule() = runBlocking {
        val current = library("existing")
        var cleanupCalled = false

        val failure = runCatching {
            commitWorkflowDeletion(
                current = { current },
                workflowId = "existing",
                save = { throw IllegalStateException("disk full") },
                cleanup = {
                    cleanupCalled = true
                    Unit
                },
            )
        }.exceptionOrNull()

        assertEquals("disk full", failure?.message)
        assertFalse(cleanupCalled)
        assertEquals(listOf("existing"), current.workflows.map(Workflow::id))
    }

    @Test
    fun cleanupFailureKeepsCommittedWorkflowDeletion() = runBlocking {
        var stored = library("existing")

        val result = commitWorkflowDeletion(
            current = { stored },
            workflowId = "existing",
            save = { stored = it },
            cleanup = { throw IllegalStateException("schedule corrupt") },
        )

        assertTrue(stored.workflows.isEmpty())
        assertTrue(result.library.workflows.isEmpty())
        assertNull(result.cleanupResult)
        assertEquals("schedule corrupt", result.cleanupError?.message)
    }

    private fun library(id: String) = WorkflowLibrary(
        workflows = listOf(
            workflow(id),
        ),
    )

    private fun workflow(id: String) = Workflow(
        id = id,
        name = id,
        steps = listOf(Step.Delay("step-$id", 100)),
    )
}