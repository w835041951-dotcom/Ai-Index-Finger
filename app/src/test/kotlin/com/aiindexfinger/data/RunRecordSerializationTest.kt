package com.aiindexfinger.data

import com.aiindexfinger.executor.StepExecutionDiagnostic
import com.aiindexfinger.executor.StepExecutionOutcome
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RunRecordSerializationTest {
    @Test
    fun runRecordRoundTripsWithoutWorkflowPayload() {
        val record = RunRecord(
            id = "run-1",
            workflowId = "workflow-1",
            workflowName = "Daily task",
            startedAtMillis = 1_000,
            durationMillis = 250,
            status = RunStatus.Failed,
            failedStepId = "click-submit",
            failedStepLocation = RunStepLocation(
                listOf(RunStepLocationSegment(1, RunStepBranch.IfTrue), RunStepLocationSegment(2)),
            ),
            failureCode = "execution.TargetNotClickable",
            diagnostics = listOf(
                RunStepDiagnostic(0, "click-submit", 25, 2, RunStepOutcome.Failed),
            ),
        )

        val encoded = Json.encodeToString(RunRecord.serializer(), record)
        val decoded = Json.decodeFromString(RunRecord.serializer(), encoded)

        assertEquals(record, decoded)
        assertFalse(encoded.contains("steps"))
        assertFalse(encoded.contains("variables"))
        assertFalse(encoded.contains("inputText"))
    }

    @Test
    fun oldRunRecordWithoutDiagnosticsRemainsReadable() {
        val encoded = """{"id":"old","workflowId":"workflow","workflowName":"Old","startedAtMillis":1,"durationMillis":2,"status":"Completed"}"""

        val decoded = Json.decodeFromString(RunRecord.serializer(), encoded)

        assertEquals(emptyList<RunStepDiagnostic>(), decoded.diagnostics)
        assertEquals(null, decoded.failedStepLocation)
    }

    @Test
    fun imageClickDiagnosticRoundTripsWithoutScreenContentOrCoordinates() {
        val record = RunRecord(
            id = "image",
            workflowId = "workflow",
            workflowName = "Image",
            startedAtMillis = 1,
            durationMillis = 2,
            diagnostics = listOf(
                RunStepDiagnostic(
                    sequence = 0,
                    stepId = "image",
                    durationMillis = 2,
                    attemptCount = 1,
                    imageClick = RunImageClickDiagnostic(
                        selectionMode = RunImageClickSelectionMode.AllMatches,
                        candidateCount = 20,
                        candidatesTruncated = true,
                        bestScorePermille = 975,
                        bestScalePermille = 1_050,
                        plannedClickCount = 20,
                        completedClickCount = 4,
                        failedClickIndex = 5,
                        retrySuppressed = true,
                    ),
                ),
            ),
        )

        val encoded = Json.encodeToString(RunRecord.serializer(), record)

        assertEquals(record, Json.decodeFromString(RunRecord.serializer(), encoded))
        assertFalse(encoded.contains("template"))
        assertFalse(encoded.contains("screenshot"))
        assertFalse(encoded.contains("coordinate"))
    }

    @Test
    fun warningRecordKeepsPolicyOwnerAndFailedLeaf() {
        val leafLocation = RunStepLocation(
            listOf(RunStepLocationSegment(0, RunStepBranch.RepeatBody), RunStepLocationSegment(0)),
        )
        val record = RunRecord(
            id = "warning",
            workflowId = "workflow",
            workflowName = "Warning",
            startedAtMillis = 1,
            durationMillis = 2,
            status = RunStatus.CompletedWithWarnings,
            failedStepId = "leaf",
            failedStepLocation = leafLocation,
            failureCode = "execution.TargetNotClickable",
            diagnostics = listOf(
                RunStepDiagnostic(
                    sequence = 0,
                    stepId = "repeat",
                    durationMillis = 2,
                    attemptCount = 1,
                    outcome = RunStepOutcome.ContinuedAfterFailure,
                    failureCode = "execution.TargetNotClickable",
                    failedStepId = "leaf",
                    failedStepLocation = leafLocation,
                ),
            ),
        )

        assertEquals(
            record,
            Json.decodeFromString(RunRecord.serializer(), Json.encodeToString(RunRecord.serializer(), record)),
        )
    }

    @Test
    fun legacyFailureMessageRemainsReadable() {
        val encoded = """{"id":"old","workflowId":"workflow","workflowName":"Old","startedAtMillis":1,"durationMillis":2,"status":"Failed","failureMessage":"Legacy failure"}"""

        val decoded = Json.decodeFromString(RunRecord.serializer(), encoded)

        assertEquals("Legacy failure", decoded.failureMessage)
        assertEquals(null, decoded.failureCode)
        assertEquals(emptyMap<String, String>(), decoded.failureArguments)
    }

    @Test
    fun malformedStructuredFailureRemainsReadableForUiFallback() {
        val encoded = """{"id":"bad","workflowId":"workflow","workflowName":"Bad","startedAtMillis":1,"durationMillis":2,"status":"Failed","failureCode":"execution.ExecutionLimitExceeded","failureArguments":{"limit":"not-a-number"}}"""

        val decoded = Json.decodeFromString(RunRecord.serializer(), encoded)

        assertEquals("execution.ExecutionLimitExceeded", decoded.failureCode)
        assertEquals(mapOf("limit" to "not-a-number"), decoded.failureArguments)
    }
}