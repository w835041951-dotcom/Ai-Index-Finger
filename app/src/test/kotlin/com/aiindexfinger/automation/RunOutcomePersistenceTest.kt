package com.aiindexfinger.automation

import com.aiindexfinger.data.RunRecord
import com.aiindexfinger.data.RunStatus
import com.aiindexfinger.executor.RunResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RunOutcomePersistenceTest {
    @Test
    fun persistenceFailureKeepsTheExecutionOutcome() {
        val record = RunRecord(
            id = "run",
            workflowId = "workflow",
            workflowName = "Workflow",
            startedAtMillis = 1,
            durationMillis = 2,
            status = RunStatus.Completed,
        )

        val outcome = persistRunOutcome(RunResult.Completed, record) {
            throw IllegalStateException("history corrupt")
        }

        assertEquals(RunResult.Completed, outcome.result)
        assertEquals(record, outcome.record)
        assertTrue(outcome.historyWriteFailed)
    }

    @Test
    fun cancellingWorkflowKeepsOwnershipUntilItsCompletionCleanup() {
        val ownership = WorkflowJobOwnership()
        val first = Job()
        val second = Job()

        assertTrue(ownership.claim(first))
        first.cancel()

        assertTrue(ownership.isOccupied())
        assertSame(first, ownership.current())
        assertTrue(ownership.owns(first))
        assertFalse(ownership.owns(second))
        assertFalse(ownership.claim(second))

        assertTrue(ownership.release(first))
        assertTrue(ownership.claim(second))
        assertFalse(ownership.release(first))
        assertFalse(ownership.owns(first))
        assertTrue(ownership.owns(second))
        assertSame(second, ownership.current())
    }

    @Test
    fun completedWorkflowCannotStartAStaleWatchdog() {
        val ownership = WorkflowJobOwnership()
        val owner = Job()
        val watchdog = CoroutineScope(Dispatchers.Unconfined).launch(start = CoroutineStart.LAZY) {
            awaitCancellation()
        }
        assertTrue(ownership.claim(owner))
        assertTrue(ownership.release(owner))

        assertFalse(startWorkflowWatchdogIfOwned(ownership, owner, watchdog))
        assertTrue(watchdog.isCancelled)
    }
}