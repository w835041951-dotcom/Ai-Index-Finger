package com.aiindexfinger.data

import com.aiindexfinger.executor.RunResult
import com.aiindexfinger.executor.StepExecutionDiagnostic
import com.aiindexfinger.executor.StepExecutionOutcome
import com.aiindexfinger.model.Workflow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunResultMappingTest {
    private val workflow = Workflow(
        id = "workflow-1",
        name = "Test workflow",
        steps = emptyList(),
    )

    @Test
    fun failedResultKeepsOnlyDiagnosticFields() {
        val record = RunResult.Failed("step-2", "Target not found")
            .toRunRecord(workflow, startedAtMillis = 100, finishedAtMillis = 175)

        assertEquals(RunStatus.Failed, record.status)
        assertEquals(75, record.durationMillis)
        assertEquals("step-2", record.failedStepId)
        assertEquals("Target not found", record.failureMessage)
    }

    @Test
    fun cancelledResultHasNoFailureDetails() {
        val record = RunResult.Cancelled
            .toRunRecord(workflow, startedAtMillis = 200, finishedAtMillis = 150)

        assertEquals(RunStatus.Cancelled, record.status)
        assertEquals(0, record.durationMillis)
        assertNull(record.failedStepId)
        assertNull(record.failureMessage)
    }

    @Test
    fun executorDiagnosticsMapWithoutSensitiveStepData() {
        val record = RunResult.Completed.toRunRecord(
            workflow,
            startedAtMillis = 100,
            finishedAtMillis = 120,
            diagnostics = listOf(
                StepExecutionDiagnostic(0, "input", 20, 1, StepExecutionOutcome.Completed),
            ),
        )

        assertEquals(
            listOf(RunStepDiagnostic(0, "input", 20, 1, RunStepOutcome.Completed)),
            record.diagnostics,
        )
    }
}