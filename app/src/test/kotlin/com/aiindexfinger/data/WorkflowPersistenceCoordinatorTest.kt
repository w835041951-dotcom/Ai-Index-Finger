package com.aiindexfinger.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
}