package com.aiindexfinger.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque
import kotlin.coroutines.CoroutineContext

class WorkflowPersistenceCoordinatorTest {
    @Test
    fun submissionOrderIsPreservedWhenDispatcherStartsNewestOperationFirst() = runBlocking {
        val dispatcher = LifoDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val coordinator = WorkflowPersistenceCoordinator(scope)
        val events = mutableListOf<String>()

        val first = coordinator.submit { events += "first" }
        val second = coordinator.submit { events += "second" }

        dispatcher.runNewest()
        assertEquals(emptyList<String>(), events)
        dispatcher.runNewest()
        dispatcher.runAll()

        first.await()
        second.await()
        assertEquals(listOf("first", "second"), events)
        scope.cancel()
    }

    @Test
    fun operationsFromDifferentClientsNeverOverlapAndKeepSubmissionOrder() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = WorkflowPersistenceCoordinator(scope)
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        val first = coordinator.submit {
            synchronized(events) { events += "first-start" }
            firstEntered.complete(Unit)
            releaseFirst.await()
            synchronized(events) { events += "first-end" }
        }
        firstEntered.await()
        val second = coordinator.submit {
            synchronized(events) { events += "second" }
            secondEntered.complete(Unit)
        }

        assertFalse(secondEntered.isCompleted)
        releaseFirst.complete(Unit)
        first.await()
        second.await()

        assertEquals(listOf("first-start", "first-end", "second"), events)
        scope.cancel()
    }

    @Test
    fun queuedLibraryTransformReadsThePrecedingEditorSave() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
        val coordinator = WorkflowPersistenceCoordinator(scope)
        val editorStarted = CompletableDeferred<Unit>()
        val allowEditorSave = CompletableDeferred<Unit>()
        var stored = WorkflowLibrary(workflows = listOf(workflow("existing")))

        val editorSave = coordinator.submit {
            editorStarted.complete(Unit)
            allowEditorSave.await()
            stored = stored.withWorkflow(workflow("edited"))
        }
        editorStarted.await()
        val folderUpdate = coordinator.submit {
            commitLibraryUpdate(
                current = { stored },
                update = { latest -> latest.withFolder(WorkflowFolder("folder", "Folder")) },
                save = { stored = it },
            )
        }

        allowEditorSave.complete(Unit)
        editorSave.await()

        assertEquals(
            setOf("existing", "edited"),
            folderUpdate.await().workflows.mapTo(mutableSetOf()) { it.id },
        )
        assertEquals(listOf(WorkflowFolder("folder", "Folder")), stored.folders)
        scope.cancel()
    }

    @Test
    fun queuedLoadObservesThePrecedingSave() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
        val coordinator = WorkflowPersistenceCoordinator(scope)
        val saveStarted = CompletableDeferred<Unit>()
        val allowSave = CompletableDeferred<Unit>()
        var stored = "old"

        val save = coordinator.submit {
            saveStarted.complete(Unit)
            allowSave.await()
            stored = "new"
        }
        saveStarted.await()
        val load = coordinator.submit { stored }

        allowSave.complete(Unit)
        save.await()

        assertEquals("new", load.await())
        scope.cancel()
    }

    @Test
    fun awaitedOperationPropagatesItsOriginalFailure() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = WorkflowPersistenceCoordinator(scope)

        val failure = runCatching {
            coordinator.submit<Unit> { error("save failed") }.await()
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals("save failed", failure?.message)
        scope.cancel()
    }

    @Test
    fun failedOperationDoesNotBlockTheNextSubmission() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = WorkflowPersistenceCoordinator(scope)

        val first = coordinator.submit<Unit> { error("save failed") }
        val second = coordinator.submit { "saved" }

        assertTrue(runCatching { first.await() }.isFailure)
        assertEquals("saved", second.await())
        scope.cancel()
    }

    @Test
    fun cancelledQueuedOperationIsSkippedWithoutBlockingTheNextSubmission() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = WorkflowPersistenceCoordinator(scope)
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var cancelledOperationRan = false

        val first = coordinator.submit {
            firstEntered.complete(Unit)
            releaseFirst.await()
        }
        firstEntered.await()
        val cancelled = coordinator.submit { cancelledOperationRan = true }
        val third = coordinator.submit { "saved" }
        cancelled.cancel()

        releaseFirst.complete(Unit)
        first.await()

        assertEquals("saved", third.await())
        assertFalse(cancelledOperationRan)
        scope.cancel()
    }

    @Test
    fun cancellingAwaiterDoesNotCancelApplicationScopedOperation() = runBlocking {
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = WorkflowPersistenceCoordinator(applicationScope)
        val operationStarted = CompletableDeferred<Unit>()
        val allowOperation = CompletableDeferred<Unit>()
        var saved = false
        val operation = coordinator.submit {
            operationStarted.complete(Unit)
            allowOperation.await()
            saved = true
        }
        operationStarted.await()
        val activityAwaiter = launch { operation.await() }

        activityAwaiter.cancel()
        activityAwaiter.join()

        assertFalse(operation.isCancelled)
        allowOperation.complete(Unit)
        operation.await()
        assertTrue(saved)
        applicationScope.cancel()
    }

    private class LifoDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            tasks.addLast(block)
        }

        fun runNewest() {
            tasks.removeLast().run()
        }

        fun runAll() {
            while (tasks.isNotEmpty()) runNewest()
        }
    }

    private fun workflow(id: String) = com.aiindexfinger.model.Workflow(
        id = id,
        name = id,
        steps = listOf(com.aiindexfinger.model.Step.Delay("step-$id", 1)),
    )
}