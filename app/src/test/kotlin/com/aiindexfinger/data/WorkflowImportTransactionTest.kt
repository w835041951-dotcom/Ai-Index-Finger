package com.aiindexfinger.data

import com.aiindexfinger.model.Step
import com.aiindexfinger.model.Workflow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            commitImportedLibrary(current, imported, { "replacement" }) {
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
            commitImportedLibrary(current, imported, { "replacement" }) {
                throw IllegalStateException("disk full")
            }
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
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

    private fun library(id: String) = WorkflowLibrary(
        workflows = listOf(
            Workflow(
                id = id,
                name = id,
                steps = listOf(Step.Delay("step-$id", 100)),
            ),
        ),
    )
}