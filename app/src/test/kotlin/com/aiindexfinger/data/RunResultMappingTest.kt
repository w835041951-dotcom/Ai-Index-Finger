package com.aiindexfinger.data

import com.aiindexfinger.executor.ExecutionError
import com.aiindexfinger.executor.ExecutionErrorCode
import com.aiindexfinger.executor.ImageClickExecutionDiagnostic
import com.aiindexfinger.executor.RunResult
import com.aiindexfinger.executor.StepExecutionDiagnostic
import com.aiindexfinger.executor.StepExecutionOutcome
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.Step
import com.aiindexfinger.model.Condition
import com.aiindexfinger.model.ImageClickSelectionMode
import com.aiindexfinger.model.Value
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunResultMappingTest {
    private val workflow = Workflow(
        id = "workflow-1",
        name = "Test workflow",
        steps = listOf(
            Step.Delay("top", 1),
            Step.Repeat("repeat", 1, listOf(Step.Delay("step-2", 1))),
        ),
    )

    @Test
    fun failedResultKeepsOnlyDiagnosticFields() {
        val record = RunResult.Failed("step-2", ExecutionError(ExecutionErrorCode.TargetNotClickable))
            .toRunRecord(workflow, startedAtMillis = 100, finishedAtMillis = 175)

        assertEquals(RunStatus.Failed, record.status)
        assertEquals(75, record.durationMillis)
        assertEquals("step-2", record.failedStepId)
        assertEquals(
            RunStepLocation(
                listOf(
                    RunStepLocationSegment(1, RunStepBranch.RepeatBody),
                    RunStepLocationSegment(0),
                ),
            ),
            record.failedStepLocation,
        )
        assertNull(record.failureMessage)
        assertEquals("execution.TargetNotClickable", record.failureCode)
        assertEquals(emptyMap<String, String>(), record.failureArguments)
    }

    @Test
    fun cancelledResultHasNoFailureDetails() {
        val record = RunResult.Cancelled
            .toRunRecord(workflow, startedAtMillis = 200, finishedAtMillis = 150)

        assertEquals(RunStatus.Cancelled, record.status)
        assertEquals(0, record.durationMillis)
        assertNull(record.failedStepId)
        assertNull(record.failureMessage)
        assertNull(record.failureCode)
    }

    @Test
    fun missingControlNotificationAddsStableCancellationReasonOnlyToCancelledRun() {
        val cancelled = RunResult.Cancelled
            .toRunRecord(workflow, startedAtMillis = 100, finishedAtMillis = 110)
        val completed = RunResult.Completed
            .toRunRecord(workflow, startedAtMillis = 100, finishedAtMillis = 110)

        assertEquals(
            RUN_FAILURE_CONTROL_NOTIFICATION_UNAVAILABLE,
            cancelled.withControlNotificationCancellation(
                RunResult.Cancelled,
                controlsUnavailable = true,
            ).failureCode,
        )
        assertNull(
            cancelled.withControlNotificationCancellation(
                RunResult.Cancelled,
                controlsUnavailable = false,
            ).failureCode,
        )
        assertNull(
            completed.withControlNotificationCancellation(
                RunResult.Completed,
                controlsUnavailable = true,
            ).failureCode,
        )
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

    @Test
    fun imageClickExecutorDiagnosticMapsToNumericRunHistoryFields() {
        val record = RunResult.Completed.toRunRecord(
            workflow,
            startedAtMillis = 100,
            finishedAtMillis = 120,
            diagnostics = listOf(
                StepExecutionDiagnostic(
                    sequence = 0,
                    stepId = "top",
                    durationMillis = 20,
                    attemptCount = 1,
                    outcome = StepExecutionOutcome.Completed,
                    imageClick = ImageClickExecutionDiagnostic(
                        selectionMode = ImageClickSelectionMode.BestMatch,
                        candidateCount = 2,
                        candidatesTruncated = false,
                        bestScorePermille = 950,
                        bestScalePermille = 1_000,
                        plannedClickCount = 1,
                        completedClickCount = 1,
                    ),
                ),
            ),
        )

        assertEquals(RunImageClickSelectionMode.BestMatch, record.diagnostics.single().imageClick?.selectionMode)
        assertEquals(2, record.diagnostics.single().imageClick?.candidateCount)
        assertEquals(1, record.diagnostics.single().imageClick?.completedClickCount)
    }

    @Test
    fun completedRunWithContinuedFailureMapsToWarningStatus() {
        val record = RunResult.Completed.toRunRecord(
            workflow,
            startedAtMillis = 100,
            finishedAtMillis = 120,
            diagnostics = listOf(
                StepExecutionDiagnostic(
                    sequence = 0,
                    stepId = "step-2",
                    durationMillis = 20,
                    attemptCount = 1,
                    outcome = StepExecutionOutcome.ContinuedAfterFailure,
                    error = ExecutionError(ExecutionErrorCode.TargetNotClickable),
                ),
            ),
        )

        assertEquals(RunStatus.CompletedWithWarnings, record.status)
        assertEquals("step-2", record.failedStepId)
        assertEquals("execution.TargetNotClickable", record.failureCode)
        assertEquals("execution.TargetNotClickable", record.diagnostics.single().failureCode)
        assertEquals(RunStepOutcome.ContinuedAfterFailure, record.diagnostics.single().outcome)
    }

    @Test
    fun diagnosticsKeepNestedLogicalLocation() {
        val record = RunResult.Completed.toRunRecord(
            workflow,
            startedAtMillis = 100,
            finishedAtMillis = 120,
            diagnostics = listOf(
                StepExecutionDiagnostic(0, "step-2", 20, 1, StepExecutionOutcome.Completed),
            ),
        )

        assertEquals(record.failedStepLocation, null)
        assertEquals(1, record.diagnostics.single().location?.segments?.first()?.index)
        assertEquals(RunStepBranch.RepeatBody, record.diagnostics.single().location?.segments?.first()?.branch)
    }

    @Test
    fun allDuplicateLocationsRemainAvailableForValidationDisplay() {
        val duplicateSteps = listOf(
            Step.Delay("same", 1),
            Step.IfElse(
                id = "condition",
                condition = Condition.Equals(Value.Literal("value"), Value.Literal("yes")),
                whenTrue = listOf(Step.Delay("same", 1)),
            ),
        )

        val locations = duplicateSteps.runLocationsTo("same")

        assertEquals(2, locations.size)
        assertEquals(0, locations[0].segments.single().index)
        assertTrue(locations[1].segments.first().branch == RunStepBranch.IfTrue)
        assertNull(duplicateSteps.uniqueRunLocationTo("same"))
    }
}