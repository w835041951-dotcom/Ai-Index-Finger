package com.aiindexfinger

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingDestinationTest {
    @Test
    fun originatingWorkflowCanConsumeRecording() {
        assertTrue(matchesRecordingDestination("workflow-a", "workflow-a"))
    }

    @Test
    fun anotherWorkflowCannotConsumeRecording() {
        assertFalse(matchesRecordingDestination("workflow-a", "workflow-b"))
    }
}
