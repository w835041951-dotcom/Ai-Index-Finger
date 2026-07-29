package com.aiindexfinger.data

import com.aiindexfinger.model.NodeSelector
import com.aiindexfinger.model.NodeAttribute
import com.aiindexfinger.model.ComparisonOperator
import com.aiindexfinger.model.Condition
import com.aiindexfinger.model.Step
import com.aiindexfinger.model.TextMatchMode
import com.aiindexfinger.model.TextInputMethod
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.WorkflowState
import com.aiindexfinger.model.effectiveState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkflowTransferCodecTest {
    @Test
    fun validWorkflowRoundTrips() {
        val workflow = Workflow(
            id = "workflow-1",
            name = "Exported workflow",
            steps = listOf(Step.Delay("wait", 500)),
        )

        assertEquals(workflow, WorkflowTransferCodec.decode(WorkflowTransferCodec.encode(workflow)))
        assertEquals(listOf(workflow), WorkflowTransferCodec.decodeMany(WorkflowTransferCodec.encode(workflow)))
    }

    @Test
    fun workflowBundleRoundTrips() {
        val workflows = listOf(
            Workflow(id = "first", name = "First", steps = listOf(Step.Delay("wait-1", 100))),
            Workflow(id = "second", name = "Second", steps = listOf(Step.Delay("wait-2", 200))),
        )

        assertEquals(
            workflows,
            WorkflowTransferCodec.decodeMany(WorkflowTransferCodec.encodeBundle(workflows)),
        )
    }

    @Test
    fun bundleWithDuplicateWorkflowIdsIsRejected() {
        val workflows = listOf(
            Workflow(id = "same", name = "First", steps = listOf(Step.Delay("wait-1", 100))),
            Workflow(id = "same", name = "Second", steps = listOf(Step.Delay("wait-2", 200))),
        )

        assertThrows(IllegalArgumentException::class.java) {
            WorkflowTransferCodec.encodeBundle(workflows)
        }
    }

    @Test
    fun longClickRoundTripsInCurrentSchema() {
        val workflow = Workflow(
            id = "long-click",
            name = "Long click",
            steps = listOf(
                Step.LongClick(
                    "hold",
                    NodeSelector("com.example", viewId = "com.example:id/item"),
                ),
            ),
        )

        val decoded = WorkflowTransferCodec.decode(WorkflowTransferCodec.encode(workflow))

        assertEquals(Workflow.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(workflow, decoded)
    }

    @Test
    fun previousSchemaRemainsImportable() {
        val previous = """{"schemaVersion":1,"id":"old","name":"Old","steps":[{"type":"delay","id":"wait","durationMillis":100,"timeoutMillis":null,"failurePolicy":{"type":"stop"}}]}"""

        assertEquals(1, WorkflowTransferCodec.decode(previous).schemaVersion)
    }

    @Test
    fun schemaTwoRemainsImportable() {
        val previous = """{"schemaVersion":2,"id":"old-2","name":"Old 2","steps":[{"type":"delay","id":"wait","durationMillis":100,"timeoutMillis":null,"failurePolicy":{"type":"stop"}}]}"""

        assertEquals(2, WorkflowTransferCodec.decode(previous).schemaVersion)
    }

    @Test
    fun schemaThreeRemainsImportable() {
        val previous = """{"schemaVersion":3,"id":"old-3","name":"Old 3","steps":[{"type":"tap","id":"tap","x":100,"y":200,"timeoutMillis":null,"failurePolicy":{"type":"stop"}}]}"""

        assertEquals(3, WorkflowTransferCodec.decode(previous).schemaVersion)
    }

    @Test
    fun schemaFourRemainsImportable() {
        val previous = """{"schemaVersion":4,"id":"old-4","name":"Old 4","steps":[{"type":"delay","id":"wait","durationMillis":100,"timeoutMillis":null,"failurePolicy":{"type":"stop"}}]}"""

        assertEquals(4, WorkflowTransferCodec.decode(previous).schemaVersion)
    }

    @Test
    fun schemaFiveRemainsImportable() {
        val previous = """{"schemaVersion":5,"id":"old-5","name":"Old 5","steps":[{"type":"delay","id":"wait","durationMillis":100,"timeoutMillis":null,"failurePolicy":{"type":"stop"}}]}"""

        assertEquals(5, WorkflowTransferCodec.decode(previous).schemaVersion)
    }

    @Test
    fun schemaSixReadTextDefaultsToOriginalBehavior() {
        val previous = """{"schemaVersion":6,"id":"old-6","name":"Old 6","steps":[{"type":"read_node_text","id":"read","selector":{"packageName":"com.example","viewId":"com.example:id/title"},"variableName":"title","timeoutMillis":null,"failurePolicy":{"type":"stop"}}]}"""

        val step = WorkflowTransferCodec.decode(previous).steps.single() as Step.ReadNodeText

        assertEquals(NodeAttribute.TextOrDescription, step.attribute)
    }

    @Test
    fun schemaSevenSelectorDefaultsToExactMatching() {
        val previous = """{"schemaVersion":7,"id":"old-7","name":"Old 7","steps":[{"type":"click","id":"click","selector":{"packageName":"com.example","text":"Open"},"timeoutMillis":null,"failurePolicy":{"type":"stop"}}]}"""

        val selector = (WorkflowTransferCodec.decode(previous).steps.single() as Step.Click).selector

        assertEquals(TextMatchMode.Exact, selector.textMatchMode)
        assertEquals(TextMatchMode.Exact, selector.contentDescriptionMatchMode)
    }

    @Test
    fun schemaEightSelectorDefaultsToFirstMatch() {
        val previous = """{"schemaVersion":8,"id":"old-8","name":"Old 8","steps":[{"type":"click","id":"click","selector":{"packageName":"com.example","text":"Item","textMatchMode":"Contains"},"timeoutMillis":null,"failurePolicy":{"type":"stop"}}]}"""

        val selector = (WorkflowTransferCodec.decode(previous).steps.single() as Step.Click).selector

        assertEquals(0, selector.matchIndex)
    }

    @Test
    fun schemaNineConditionDefaultsToEquals() {
        val previous = """{"schemaVersion":9,"id":"old-9","name":"Old 9","steps":[{"type":"if_else","id":"if","condition":{"type":"equals","left":{"type":"literal","value":"a"},"right":{"type":"literal","value":"a"}},"whenTrue":[{"type":"delay","id":"wait","durationMillis":1,"timeoutMillis":null,"failurePolicy":{"type":"stop"}}],"whenFalse":[],"timeoutMillis":null,"failurePolicy":{"type":"stop"}}]}"""

        val condition = (WorkflowTransferCodec.decode(previous).steps.single() as Step.IfElse).condition

        assertEquals(ComparisonOperator.Equals, (condition as Condition.Equals).operator)
    }

    @Test
    fun schemaTenRemainsImportable() {
        val previous = """{"schemaVersion":10,"id":"old-10","name":"Old 10","steps":[{"type":"set_variable","id":"set","name":"status","value":{"type":"literal","value":"ready"},"timeoutMillis":null,"failurePolicy":{"type":"stop"}}]}"""

        assertEquals(10, WorkflowTransferCodec.decode(previous).schemaVersion)
    }

    @Test
    fun schemaElevenInputDefaultsToSetText() {
        val previous = """{"schemaVersion":11,"id":"old-11","name":"Old 11","steps":[{"type":"input_text","id":"input","selector":{"packageName":"com.example","viewId":"com.example:id/input"},"text":"hello","variableName":null,"timeoutMillis":null,"failurePolicy":{"type":"stop"}}]}"""

        val step = WorkflowTransferCodec.decode(previous).steps.single() as Step.InputText

        assertEquals(TextInputMethod.SetText, step.inputMethod)
    }

    @Test
    fun legacyEmptyWorkflowImportsAsDraft() {
        val invalid = """{"schemaVersion":1,"id":"empty","name":"Empty","steps":[]}"""

        assertEquals(WorkflowState.Draft, WorkflowTransferCodec.decode(invalid).effectiveState())
    }

    @Test
    fun explicitInvalidDraftRoundTrips() {
        val draft = Workflow(
            id = "draft",
            name = "Draft",
            steps = emptyList(),
            state = WorkflowState.Draft,
        )

        assertEquals(draft, WorkflowTransferCodec.decode(WorkflowTransferCodec.encode(draft)))
        assertEquals(listOf(draft), WorkflowTransferCodec.decodeMany(WorkflowTransferCodec.encodeBundle(listOf(draft))))
    }

    @Test
    fun explicitInvalidReadyWorkflowIsRejected() {
        val invalidReady = Workflow(
            id = "ready",
            name = "Ready",
            steps = emptyList(),
            state = WorkflowState.Ready,
        )

        assertThrows(IllegalArgumentException::class.java) {
            WorkflowTransferCodec.decode(WorkflowTransferCodec.encode(invalidReady))
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkflowTransferCodec.encodeBundle(listOf(invalidReady))
        }
    }

    @Test
    fun newerSchemaIsRejected() {
        val unsupported = """{"schemaVersion":999,"id":"future","name":"Future","steps":[]}"""

        assertThrows(IllegalArgumentException::class.java) {
            WorkflowTransferCodec.decode(unsupported)
        }
    }
}