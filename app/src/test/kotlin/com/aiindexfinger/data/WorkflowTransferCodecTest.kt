package com.aiindexfinger.data

import com.aiindexfinger.model.AncestorSelector
import com.aiindexfinger.model.NodeSelector
import com.aiindexfinger.model.NodeAttribute
import com.aiindexfinger.model.RecordedBounds
import com.aiindexfinger.model.RecordedClickFallbackCause
import com.aiindexfinger.model.RecordedClickTargetMode
import com.aiindexfinger.model.RecordedControl
import com.aiindexfinger.model.ComparisonOperator
import com.aiindexfinger.model.Condition
import com.aiindexfinger.model.Step
import com.aiindexfinger.model.TextMatchMode
import com.aiindexfinger.model.TextInputMethod
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.WorkflowState
import com.aiindexfinger.model.ValidationIssueCode
import com.aiindexfinger.model.effectiveState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
    fun currentSchemaVersionIsExplicitInSingleAndBundleExports() {
        val workflow = Workflow(id = "schema", name = "Schema", steps = emptyList())

        assertTrue(WorkflowTransferCodec.encode(workflow).contains("\"schemaVersion\": 19"))
        assertTrue(WorkflowTransferCodec.encodeBundle(listOf(workflow)).contains("\"schemaVersion\": 19"))
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
    fun workflowLibraryRoundTripsFoldersAndAssignments() {
        val workflow = Workflow(id = "first", name = "First", steps = listOf(Step.Delay("wait", 100)))
        val library = WorkflowLibrary(
            workflows = listOf(workflow),
            folders = listOf(WorkflowFolder("folder", "Personal"), WorkflowFolder("empty", "Empty")),
            workflowFolderIds = mapOf(workflow.id to "folder"),
        )

        assertEquals(library, WorkflowTransferCodec.decodeLibrary(WorkflowTransferCodec.encodeLibrary(library)))
    }

    @Test
    fun importedFolderNamesUseTheSameTrimmedFormAsLocalFolders() {
        val library = WorkflowTransferCodec.decodeLibrary(
            """{"formatVersion":2,"workflows":[],"folders":[{"id":"folder","name":"  Personal  "}],"workflowFolderIds":{}}""",
        )

        assertEquals(listOf(WorkflowFolder("folder", "Personal")), library.folders)
    }

    @Test
    fun importedWorkflowNamesUseTheSameTrimmedFormAsLocalEditor() {
        val single = WorkflowTransferCodec.decode(
            """{"id":"single","name":"  Single  ","steps":[],"state":"Draft"}""",
        )
        val bundle = WorkflowTransferCodec.decodeLibrary(
            """{"formatVersion":2,"workflows":[{"id":"bundle","name":"  Bundle  ","steps":[],"state":"Draft"}],"folders":[],"workflowFolderIds":{}}""",
        )

        assertEquals("Single", single.name)
        assertEquals("Bundle", bundle.workflows.single().name)
    }

    @Test
    fun formatOneBundleImportsWorkflowsAsUnfiled() {
        val content = """{"formatVersion":1,"workflows":[{"id":"legacy","name":"Legacy","steps":[]}]}"""

        val library = WorkflowTransferCodec.decodeLibrary(content)

        assertEquals(listOf("legacy"), library.workflows.map(Workflow::id))
        assertEquals(emptyList<WorkflowFolder>(), library.folders)
        assertEquals(emptyMap<String, String>(), library.workflowFolderIds)
    }

    @Test
    fun bundleWithDuplicateWorkflowIdsIsRejected() {
        val workflows = listOf(
            Workflow(id = "same", name = "First", steps = listOf(Step.Delay("wait-1", 100))),
            Workflow(id = "same", name = "Second", steps = listOf(Step.Delay("wait-2", 200))),
        )

        val error = assertThrows(WorkflowTransferException::class.java) {
            WorkflowTransferCodec.encodeBundle(workflows)
        }

        assertEquals(WorkflowTransferErrorCode.DuplicateWorkflowIds, error.code)
    }

    @Test
    fun packageWithTooManyFoldersIsRejectedOnExportAndImport() {
        val folders = List(1_001) { index -> WorkflowFolder("folder-$index", "Folder $index") }

        val exportError = assertThrows(WorkflowTransferException::class.java) {
            WorkflowTransferCodec.encodeLibrary(WorkflowLibrary(folders = folders))
        }
        val importError = assertThrows(WorkflowTransferException::class.java) {
            WorkflowTransferCodec.decodeLibrary(
                buildString {
                    append("""{"formatVersion":2,"workflows":[],"folders":[""")
                    folders.forEachIndexed { index, folder ->
                        if (index > 0) append(',')
                        append("""{"id":"${folder.id}","name":"${folder.name}"}""")
                    }
                    append("],\"workflowFolderIds\":{}}")
                },
            )
        }

        assertEquals(WorkflowTransferErrorCode.TooManyFolders, exportError.code)
        assertEquals(WorkflowTransferErrorCode.TooManyFolders, importError.code)
    }

    @Test
    fun malformedAndUnsupportedBundlesHaveStableErrorCodes() {
        val invalid = assertThrows(WorkflowTransferException::class.java) {
            WorkflowTransferCodec.decodeLibrary("{")
        }
        val wrongRoot = assertThrows(WorkflowTransferException::class.java) {
            WorkflowTransferCodec.decodeLibrary("[]")
        }
        val future = assertThrows(WorkflowTransferException::class.java) {
            WorkflowTransferCodec.decodeLibrary(
                """{"formatVersion":999,"workflows":[],"folders":[],"workflowFolderIds":{}}""",
            )
        }

        assertEquals(WorkflowTransferErrorCode.InvalidContent, invalid.code)
        assertEquals(WorkflowTransferErrorCode.RootNotObject, wrongRoot.code)
        assertEquals(WorkflowTransferErrorCode.UnsupportedBundleVersion, future.code)
        assertEquals("999", future.arguments["version"])
    }

    @Test
    fun declaredFutureVersionsWinOverUnknownFutureContent() {
        val futureSingle = assertThrows(WorkflowTransferException::class.java) {
            WorkflowTransferCodec.decode(
                """{"schemaVersion":999,"id":"future","name":"Future","steps":[{"type":"future_action","id":"step"}]}""",
            )
        }
        val futureWorkflowInBundle = assertThrows(WorkflowTransferException::class.java) {
            WorkflowTransferCodec.decodeLibrary(
                """{"formatVersion":2,"workflows":[{"schemaVersion":999,"id":"future","name":"Future","steps":[{"type":"future_action","id":"step"}]}]}""",
            )
        }
        val futureBundle = assertThrows(WorkflowTransferException::class.java) {
            WorkflowTransferCodec.decodeLibrary(
                """{"formatVersion":999,"workflows":[{"schemaVersion":999,"id":"future","name":"Future","steps":[{"type":"future_action","id":"step"}]}]}""",
            )
        }
        val unknownCurrent = assertThrows(WorkflowTransferException::class.java) {
            WorkflowTransferCodec.decode(
                """{"schemaVersion":${Workflow.CURRENT_SCHEMA_VERSION},"id":"current","name":"Current","steps":[{"type":"future_action","id":"step"}]}""",
            )
        }

        assertEquals(WorkflowTransferErrorCode.UnsupportedWorkflowVersion, futureSingle.code)
        assertEquals("999", futureSingle.arguments["version"])
        assertEquals(WorkflowTransferErrorCode.UnsupportedWorkflowVersion, futureWorkflowInBundle.code)
        assertEquals(WorkflowTransferErrorCode.UnsupportedBundleVersion, futureBundle.code)
        assertEquals(WorkflowTransferErrorCode.InvalidContent, unknownCurrent.code)
    }

    @Test
    fun fileAccessFailuresHaveAStableErrorCode() {
        val missing = assertThrows(WorkflowTransferException::class.java) {
            transferFileAccess<Any> { null }
        }
        val denied = assertThrows(WorkflowTransferException::class.java) {
            transferFileAccess<Any> { throw SecurityException("provider detail") }
        }

        assertEquals(WorkflowTransferErrorCode.FileUnavailable, missing.code)
        assertEquals(WorkflowTransferErrorCode.FileUnavailable, denied.code)
    }

    @Test
    fun exportNeverCreatesAFileLargerThanTheImportLimit() {
        val oversized = Workflow(
            id = "oversized",
            name = "Oversized",
            steps = listOf(
                Step.InputText(
                    id = "input",
                    selector = NodeSelector("com.example", text = "Input"),
                    text = "x".repeat(2 * 1024 * 1024),
                ),
            ),
        )

        val error = assertThrows(WorkflowTransferException::class.java) {
            WorkflowTransferCodec.encode(oversized)
        }

        assertEquals(WorkflowTransferErrorCode.FileTooLarge, error.code)
    }

    @Test
    fun codecRejectsOversizedImportBeforeParsing() {
        val oversized = "{" + "x".repeat(2 * 1024 * 1024)

        val singleError = assertThrows(WorkflowTransferException::class.java) {
            WorkflowTransferCodec.decode(oversized)
        }
        val bundleError = assertThrows(WorkflowTransferException::class.java) {
            WorkflowTransferCodec.decodeLibrary(oversized)
        }

        assertEquals(WorkflowTransferErrorCode.FileTooLarge, singleError.code)
        assertEquals(WorkflowTransferErrorCode.FileTooLarge, bundleError.code)
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
    fun activeWindowSelectorRoundTripsWithoutAPackage() {
        val workflow = Workflow(
            id = "active-window",
            name = "Active window",
            steps = listOf(Step.Click("click", NodeSelector("", text = "Continue"))),
        )

        val decoded = WorkflowTransferCodec.decode(WorkflowTransferCodec.encode(workflow))

        assertEquals(workflow, decoded)
        assertEquals("", (decoded.steps.single() as Step.Click).selector.packageName)
    }

    @Test
    fun ancestorScopedSelectorRoundTripsInCurrentSchema() {
        val selector = NodeSelector(
            packageName = "com.example",
            text = "Delete",
            ancestor = AncestorSelector(text = "Alice"),
        )
        val workflow = Workflow(
            id = "ancestor-selector",
            name = "Ancestor selector",
            steps = listOf(Step.Click("click", selector)),
        )

        val decoded = WorkflowTransferCodec.decode(WorkflowTransferCodec.encode(workflow))

        assertEquals(Workflow.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(selector, (decoded.steps.single() as Step.Click).selector)
    }

    @Test
    fun schemaSixteenSelectorDefaultsToNoAncestorConstraint() {
        val previous = """{"schemaVersion":16,"id":"old-16","name":"Old 16","steps":[{"type":"click","id":"click","selector":{"packageName":"com.example","text":"Delete"},"timeoutMillis":null,"failurePolicy":{"type":"stop"}}]}"""

        val selector = (WorkflowTransferCodec.decode(previous).steps.single() as Step.Click).selector

        assertEquals(null, selector.ancestor)
    }

    @Test
    fun recordedClickRoundTripsWithBothTargetsAndFullControlSnapshot() {
        val workflow = Workflow(
            id = "recorded-click",
            name = "Recorded click",
            steps = listOf(
                Step.RecordedClick(
                    id = "recorded",
                    x = 160,
                    y = 260,
                    selector = NodeSelector("com.example", viewId = "com.example:id/save"),
                    control = RecordedControl(
                        packageName = "com.example",
                        viewId = "com.example:id/save",
                        text = "保存",
                        contentDescription = "Save changes",
                        className = "android.widget.Button",
                        bounds = RecordedBounds(100, 200, 220, 320),
                        clickable = true,
                        enabled = true,
                        longClickable = true,
                        scrollable = false,
                    ),
                    targetMode = RecordedClickTargetMode.Coordinates,
                    fallbackCause = RecordedClickFallbackCause.SelectorNotUnique,
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
    fun structurallyUnsafeDraftCannotBeImportedOrExported() {
        val duplicateIds = Workflow(
            id = "unsafe-draft",
            name = "Unsafe draft",
            steps = listOf(Step.Delay("same", 1), Step.Delay("same", 2)),
            state = WorkflowState.Draft,
        )

        val exportError = assertThrows(InvalidWorkflowException::class.java) {
            WorkflowTransferCodec.encode(duplicateIds)
        }
        val importError = assertThrows(InvalidWorkflowException::class.java) {
            WorkflowTransferCodec.decode(
                """{"id":"unsafe-draft","name":"Unsafe draft","steps":[{"type":"delay","id":"same","durationMillis":1},{"type":"delay","id":"same","durationMillis":2}],"state":"Draft"}""",
            )
        }

        assertEquals(ValidationIssueCode.DuplicateStepId, exportError.issue.code)
        assertEquals(ValidationIssueCode.DuplicateStepId, importError.issue.code)
    }

    @Test
    fun deeplyNestedDraftIsRejectedWithoutLeakingStackOverflow() {
        val content = buildString {
            append("""{"id":"deep","name":"Deep","state":"Draft","steps":[""")
            repeat(2_000) { index ->
                append("""{"type":"repeat","id":"repeat-$index","times":1,"steps":[""")
            }
            append("""{"type":"delay","id":"leaf","durationMillis":1}""")
            repeat(2_000) { append("]}") }
            append("]}")
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            WorkflowTransferCodec.decode(content)
        }

        assertTrue(
            error is WorkflowTransferException &&
                error.code == WorkflowTransferErrorCode.InvalidContent ||
                error is InvalidWorkflowException &&
                error.issue.code == ValidationIssueCode.NestingLimitExceeded,
        )
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