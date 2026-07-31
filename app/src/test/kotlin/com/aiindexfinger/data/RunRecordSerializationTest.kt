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
            failureMessage = "Target node was not clickable",
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
    }
}