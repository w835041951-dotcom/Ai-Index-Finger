package com.aiindexfinger.data

import com.aiindexfinger.model.Step
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.WorkflowState
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkflowFileStoreTest {
    @Test
    fun legacyWorkflowArrayLoadsAsUnfiledLibrary() = withTemporaryDirectory { directory ->
        directory.resolve("workflows.json").writeText(
            """[{"id":"legacy","name":"Legacy","steps":[{"type":"delay","id":"step","durationMillis":100}]}]""",
        )

        val library = WorkflowFileStore(directory).loadLibrary()

        assertEquals(listOf("legacy"), library.workflows.map(Workflow::id))
        assertTrue(library.folders.isEmpty())
        assertTrue(library.workflowFolderIds.isEmpty())
    }

    @Test
    fun workflowLibraryRoundTripPreservesFoldersAndAssignments() = withTemporaryDirectory { directory ->
        val store = WorkflowFileStore(directory)
        val library = WorkflowLibrary(
            workflows = listOf(workflow("one")),
            folders = listOf(WorkflowFolder("folder", "Personal")),
            workflowFolderIds = mapOf("one" to "folder"),
        )

        store.saveLibrary(library)

        assertEquals(library, store.loadLibrary())
    }

    @Test
    fun movingWorkflowDoesNotCreateContentVersion() = withTemporaryDirectory { directory ->
        val store = WorkflowFileStore(directory)
        val folder = WorkflowFolder("folder", "Personal")
        val library = WorkflowLibrary(workflows = listOf(workflow("one")), folders = listOf(folder))
        store.saveLibrary(library)

        store.saveLibrary(library.moveWorkflow("one", folder.id))

        assertTrue(store.listVersions("one").isEmpty())
    }

    @Test
    fun rollbackPreservesCurrentFolderAssignment() = withTemporaryDirectory { directory ->
        var timestamp = 100L
        val store = WorkflowFileStore(directory) { timestamp++ }
        val folder = WorkflowFolder("folder", "Personal")
        val first = workflow("one", "First")
        store.saveLibrary(
            WorkflowLibrary(
                workflows = listOf(first),
                folders = listOf(folder),
                workflowFolderIds = mapOf("one" to folder.id),
            ),
        )
        store.saveLibrary(store.loadLibrary().copy(workflows = listOf(workflow("one", "Second"))))
        val version = store.listVersions("one").single()

        store.rollback("one", version.versionId)

        assertEquals(folder.id, store.loadLibrary().folderIdFor("one"))
    }

    @Test
    fun versionsAreBoundedToFiveNewestSnapshots() = withTemporaryDirectory { directory ->
        var timestamp = 100L
        val store = WorkflowFileStore(directory) { timestamp++ }
        store.save(listOf(workflow("one", "Version 0")))

        repeat(6) { version ->
            store.save(listOf(workflow("one", "Version ${version + 1}")))
        }

        val versions = store.listVersions("one")
        assertEquals(5, versions.size)
        assertEquals(listOf(105L, 104L, 103L, 102L, 101L), versions.map { it.createdAtEpochMillis })
        assertFalse(versions.any { it.workflow.name == "Version 0" })
    }

    @Test
    fun unchangedSaveDoesNotCreateVersion() = withTemporaryDirectory { directory ->
        val store = WorkflowFileStore(directory) { 100L }
        val workflow = workflow("one")

        store.save(listOf(workflow))
        store.save(listOf(workflow))

        assertTrue(store.listVersions("one").isEmpty())
    }

    @Test
    fun deletingWorkflowPurgesVersionsAndBackup() = withTemporaryDirectory { directory ->
        val store = WorkflowFileStore(directory) { 100L }
        store.save(listOf(workflow("one", "Sensitive")))
        store.save(listOf(workflow("one", "Current")))
        assertTrue(store.listVersions("one").isNotEmpty())

        store.save(emptyList())
        directory.resolve("workflows.json").writeText("corrupt")

        assertTrue(store.listVersions("one").isEmpty())
        assertTrue(store.loadDetailed() is WorkflowLoadResult.RecoveredFromBackup)
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun failedWorkflowDeletionPreservesCurrentDataVersionsAndBackup() =
        withTemporaryDirectory { directory ->
            val store = WorkflowFileStore(directory) { 100L }
            val first = workflow("one", "First")
            val current = workflow("one", "Current")
            store.save(listOf(first))
            store.save(listOf(current))
            assertTrue(store.listVersions("one").isNotEmpty())
            val blockedTemporary = directory.resolve("workflows.json.tmp").apply {
                mkdirs()
                resolve("blocker").writeText("prevent atomic write")
            }

            assertThrows(Exception::class.java) { store.save(emptyList()) }

            assertEquals(listOf(current), store.load())
            assertTrue(store.listVersions("one").isNotEmpty())
            blockedTemporary.deleteRecursively()
            directory.resolve("workflows.json").writeText("{corrupt")
            assertEquals(
                WorkflowLoadResult.RecoveredFromBackup(WorkflowLibrary(workflows = listOf(first))),
                store.loadDetailed(),
            )
        }

    @Test
    fun deletingWorkflowPurgesFolderAssignmentAfterProcessRestart() = withTemporaryDirectory { directory ->
        val folder = WorkflowFolder("private", "Private")
        val store = WorkflowFileStore(directory) { 100L }
        store.saveLibrary(
            WorkflowLibrary(
                workflows = listOf(workflow("one", "Sensitive")),
                folders = listOf(folder),
                workflowFolderIds = mapOf("one" to folder.id),
            ),
        )
        store.saveLibrary(
            WorkflowLibrary(
                workflows = listOf(workflow("one", "Current")),
                folders = listOf(folder),
                workflowFolderIds = mapOf("one" to folder.id),
            ),
        )

        store.saveLibrary(WorkflowLibrary(folders = listOf(folder)))

        assertEquals(
            WorkflowLoadResult.Loaded(WorkflowLibrary(folders = listOf(folder))),
            store.loadDetailed(),
        )
        assertTrue(
            directory.resolve("workflow-versions").walkTopDown().none { it.isFile },
        )
        directory.resolve("workflows.json").writeText("corrupt")

        val restartedStore = WorkflowFileStore(directory)
        val recovered = restartedStore.loadDetailed()

        assertEquals(
            WorkflowLoadResult.RecoveredFromBackup(WorkflowLibrary(folders = listOf(folder))),
            recovered,
        )
        assertTrue(restartedStore.listVersions("one").isEmpty())
        assertEquals(listOf(folder), restartedStore.loadLibrary().folders)
        assertTrue(restartedStore.loadLibrary().workflowFolderIds.isEmpty())
    }

    @Test
    fun rollbackSnapshotsCurrentWorkflowAndRestoresSelectedVersion() = withTemporaryDirectory { directory ->
        var timestamp = 100L
        val store = WorkflowFileStore(directory) { timestamp++ }
        val first = workflow("one", "First")
        val second = workflow("one", "Second")
        store.save(listOf(first))
        store.save(listOf(second))
        val firstVersion = store.listVersions("one").single()

        val restored = store.rollback("one", firstVersion.versionId)

        assertEquals(first, restored)
        assertEquals(listOf(first), store.load())
        assertTrue(store.listVersions("one").any { it.workflow == second })
    }

    @Test
    fun rollbackPreservesWorkflowListPosition() = withTemporaryDirectory { directory ->
        var timestamp = 100L
        val store = WorkflowFileStore(directory) { timestamp++ }
        val first = workflow("one", "First")
        store.save(listOf(workflow("before"), first, workflow("after")))
        store.save(listOf(workflow("before"), workflow("one", "Second"), workflow("after")))
        val firstVersion = store.listVersions("one").single()

        store.rollback("one", firstVersion.versionId)

        assertEquals(listOf("before", "one", "after"), store.load().map(Workflow::id))
    }

    @Test
    fun corruptVersionIsIgnoredAndCannotBeRolledBack() = withTemporaryDirectory { directory ->
        val store = WorkflowFileStore(directory) { 100L }
        store.save(listOf(workflow("one", "First")))
        store.save(listOf(workflow("one", "Second")))
        requireNotNull(directory.walkTopDown().first { it.isFile && it.parentFile?.name != directory.name && it.extension == "json" })
            .writeText("{corrupt")

        assertTrue(store.listVersions("one").isEmpty())
        assertThrows(IllegalArgumentException::class.java) { store.rollback("one", "100-0") }
        assertEquals("Second", store.load().single().name)
    }

    @Test
    fun futureWorkflowVersionIsIgnoredAndCannotOverwriteCurrentData() =
        withTemporaryDirectory { directory ->
            val store = WorkflowFileStore(directory) { 100L }
            store.save(listOf(workflow("one", "First")))
            store.save(listOf(workflow("one", "Current")))
            val versionFile = requireNotNull(
                directory.walkTopDown().first {
                    it.isFile && it.parentFile?.name != directory.name && it.extension == "json"
                },
            )
            versionFile.writeText(
                versionFile.readText()
                    .replace("\"schemaVersion\": 19", "\"schemaVersion\": 999")
                    .replace("\"state\":", "\"futureField\": true,\n        \"state\":"),
            )

            assertTrue(store.listVersions("one").isEmpty())
            assertThrows(IllegalArgumentException::class.java) { store.rollback("one", "100-0") }
            assertEquals("Current", store.load().single().name)
        }

    @Test
    fun versionForDifferentWorkflowCannotBeRolledBack() = withTemporaryDirectory { directory ->
        val store = WorkflowFileStore(directory) { 100L }
        store.save(listOf(workflow("one", "First")))
        store.save(listOf(workflow("one", "Second")))
        val versionFile = requireNotNull(
            directory.walkTopDown().first { it.isFile && it.parentFile?.name != directory.name && it.extension == "json" },
        )
        versionFile.writeText(versionFile.readText().replace("\"id\": \"one\"", "\"id\": \"other\""))

        assertTrue(store.listVersions("one").isEmpty())
        assertThrows(IllegalArgumentException::class.java) { store.rollback("one", "100-0") }
        assertEquals("one", store.load().single().id)
    }

    @Test
    fun invalidReadyVersionIsRestoredAsDraft() = withTemporaryDirectory { directory ->
        val store = WorkflowFileStore(directory) { 100L }
        val invalidDraft = Workflow(
            id = "one",
            name = "Invalid draft",
            steps = emptyList(),
            state = WorkflowState.Draft,
        )
        store.save(listOf(invalidDraft))
        store.save(listOf(workflow("one", "Valid current")))
        val versionFile = requireNotNull(
            directory.walkTopDown().first { it.isFile && it.parentFile?.name != directory.name && it.extension == "json" },
        )
        versionFile.writeText(versionFile.readText().replace("\"state\": \"Draft\"", "\"state\": \"Ready\""))
        val readyVersion = store.listVersions("one").single()

        val restored = store.rollback("one", readyVersion.versionId)

        assertEquals(WorkflowState.Draft, restored.state)
        assertTrue(com.aiindexfinger.model.WorkflowValidator.validate(restored).isNotEmpty())
    }

    @Test
    fun existingWorkflowFilesLoadWithoutVersionHistory() = withTemporaryDirectory { directory ->
        val baseline = workflow("legacy")
        WorkflowFileStore(directory).save(listOf(baseline))
        directory.resolve("workflow-versions").deleteRecursively()

        val store = WorkflowFileStore(directory)

        assertEquals(listOf(baseline), store.load())
        assertTrue(store.listVersions("legacy").isEmpty())
    }

    @Test
    fun latestValidSaveIsLoaded() = withTemporaryDirectory { directory ->
        val store = WorkflowFileStore(directory)
        val first = workflow("first")
        val second = workflow("second")

        store.save(listOf(first))
        store.save(listOf(second))

        assertEquals(listOf(second), store.load())
    }

    @Test
    fun ordinarySaveKeepsACompletePreviousLibraryAsBackup() = withTemporaryDirectory { directory ->
        val store = WorkflowFileStore(directory)
        val first = workflow("one", "First")
        val second = workflow("one", "Second")
        store.save(listOf(first))

        store.save(listOf(second))
        directory.resolve("workflows.json").writeText("{corrupt")

        assertEquals(
            WorkflowLoadResult.RecoveredFromBackup(WorkflowLibrary(workflows = listOf(first))),
            WorkflowFileStore(directory).loadDetailed(),
        )
        assertFalse(directory.resolve("workflows.backup.json.tmp").exists())
    }

    @Test
    fun loadRemovesInterruptedWorkflowTemporaryFiles() = withTemporaryDirectory { directory ->
        val store = WorkflowFileStore(directory)
        store.save(listOf(workflow("one")))
        val primaryTemporary = directory.resolve("workflows.json.tmp").apply {
            writeText("orphaned sensitive workflow")
        }
        val backupTemporary = directory.resolve("workflows.backup.json.tmp").apply {
            writeText("orphaned sensitive backup")
        }
        val versionTemporary = directory.resolve("workflow-versions/stale/1-0.json.tmp").apply {
            requireNotNull(parentFile).mkdirs()
            writeText("orphaned sensitive version")
        }

        assertEquals(listOf("one"), WorkflowFileStore(directory).load().map(Workflow::id))
        assertFalse(primaryTemporary.exists())
        assertFalse(backupTemporary.exists())
        assertFalse(versionTemporary.exists())
    }

    @Test
    fun corruptPrimaryRecoversPreviousRetainedWorkflowWithoutDeletedWorkflow() =
        withTemporaryDirectory { directory ->
        val store = WorkflowFileStore(directory)
        val deleted = workflow("deleted")
        val retained = workflow("retained", "Before")
        store.save(listOf(deleted, retained))
        store.save(listOf(workflow("retained", "After")))
        directory.resolve("workflows.json").writeText("{truncated")

        assertEquals(listOf(retained), store.load())
        assertEquals(
            WorkflowLoadResult.RecoveredFromBackup(WorkflowLibrary(workflows = listOf(retained))),
            store.loadDetailed(),
        )
        }

    @Test
    fun savingRecoveredBackupRepairsPrimaryWorkflowFile() = withTemporaryDirectory { directory ->
        val store = WorkflowFileStore(directory)
        val backupWorkflow = workflow("one", "Backup")
        store.save(listOf(backupWorkflow))
        store.save(listOf(workflow("one", "Current")))
        directory.resolve("workflows.json").writeText("{truncated")
        val recovered = store.loadDetailed() as WorkflowLoadResult.RecoveredFromBackup
        val repairedWorkflow = recovered.workflows.single().copy(name = "Repaired")

        store.saveLibrary(recovered.library.withWorkflow(repairedWorkflow))

        assertEquals(
            WorkflowLoadResult.Loaded(WorkflowLibrary(workflows = listOf(repairedWorkflow))),
            WorkflowFileStore(directory).loadDetailed(),
        )
    }

    @Test
    fun missingFilesAreDistinguishedFromCorruptFiles() = withTemporaryDirectory { directory ->
        val store = WorkflowFileStore(directory)

        assertEquals(WorkflowLoadResult.Missing, store.loadDetailed())

        directory.resolve("workflows.json").writeText("{truncated")
        directory.resolve("workflows.backup.json").writeText("also invalid")

        assertTrue(store.loadDetailed() is WorkflowLoadResult.Corrupt)
        assertThrows(IllegalStateException::class.java) {
            store.save(listOf(workflow("replacement")))
        }
        assertEquals("{truncated", directory.resolve("workflows.json").readText())
    }

    @Test
    fun futureSchemaWithUnknownStepIsUnsupportedRatherThanCorrupt() =
        withTemporaryDirectory { directory ->
            val file = directory.resolve("workflows.json")
            val content = """[{"schemaVersion":999,"id":"future","name":"Future","steps":[{"type":"future_action","id":"future-step"}]}]"""
            file.writeText(content)

            val result = WorkflowFileStore(directory).loadDetailed()

            assertEquals(WorkflowLoadResult.UnsupportedVersion(999), result)
            assertEquals(content, file.readText())
        }

    @Test
    fun duplicateStoredIdentitiesAreCorruptAndCannotBeOverwritten() =
        withTemporaryDirectory { directory ->
            val file = directory.resolve("workflows.json")
            val duplicateContent = """{"workflows":[{"id":"same","name":"One","steps":[]},{"id":"same","name":"Two","steps":[]}],"folders":[{"id":"folder","name":"One"},{"id":"folder","name":"Two"}]}"""
            file.writeText(duplicateContent)
            val store = WorkflowFileStore(directory)

            assertTrue(store.loadDetailed() is WorkflowLoadResult.Corrupt)
            assertThrows(IllegalStateException::class.java) {
                store.save(listOf(workflow("replacement")))
            }
            assertEquals(duplicateContent, file.readText())
        }

    @Test
    fun structurallyUnsafeStoredDraftIsCorruptAndCannotBeOverwritten() =
        withTemporaryDirectory { directory ->
            val file = directory.resolve("workflows.json")
            val unsafeContent = """[{"id":"unsafe","name":"Unsafe","state":"Draft","steps":[{"type":"delay","id":"same","durationMillis":1},{"type":"delay","id":"same","durationMillis":2}]}]"""
            file.writeText(unsafeContent)
            val store = WorkflowFileStore(directory)

            assertTrue(store.loadDetailed() is WorkflowLoadResult.Corrupt)
            assertThrows(IllegalStateException::class.java) {
                store.save(listOf(workflow("replacement")))
            }
            assertEquals(unsafeContent, file.readText())
        }

    @Test
    fun oversizedWorkflowLibraryIsPreservedAsCorruptWithoutReadingIt() =
        withTemporaryDirectory { directory ->
            val file = directory.resolve("workflows.json")
            java.io.RandomAccessFile(file, "rw").use { it.setLength(64L * 1024 * 1024 + 1) }
            val store = WorkflowFileStore(directory)

            assertTrue(store.loadDetailed() is WorkflowLoadResult.Corrupt)
            assertThrows(IllegalStateException::class.java) {
                store.save(listOf(workflow("replacement")))
            }
            assertEquals(64L * 1024 * 1024 + 1, file.length())
        }

    @Test
    fun deeplyNestedWorkflowJsonIsPreservedAsCorrupt() = withTemporaryDirectory { directory ->
        val file = directory.resolve("workflows.json")
        val content = buildString {
            append("""[{"id":"deep","name":"Deep","state":"Draft","steps":[""")
            repeat(2_000) { index ->
                append("""{"type":"repeat","id":"repeat-$index","times":1,"steps":[""")
            }
            append("""{"type":"delay","id":"leaf","durationMillis":1}""")
            repeat(2_000) { append("]}") }
            append("]}]")
        }
        file.writeText(content)

        assertTrue(WorkflowFileStore(directory).loadDetailed() is WorkflowLoadResult.Corrupt)
        assertEquals(content, file.readText())
    }

    @Test
    fun futureSchemaIsProtectedFromOverwrite() = withTemporaryDirectory { directory ->
        val store = WorkflowFileStore(directory)
        directory.resolve("workflows.json").writeText(
            """[{"schemaVersion":999,"id":"future","name":"Future","steps":[],"futureField":true}]""",
        )

        assertTrue(store.loadDetailed() is WorkflowLoadResult.UnsupportedVersion)
        assertThrows(IllegalStateException::class.java) { store.save(emptyList()) }
    }

    @Test
    fun invalidDraftIsPersisted() = withTemporaryDirectory { directory ->
        val store = WorkflowFileStore(directory)
        val draft = Workflow(
            id = "draft",
            name = "Draft",
            steps = emptyList(),
            state = WorkflowState.Draft,
        )

        store.save(listOf(draft))

        assertEquals(listOf(draft), store.load())
    }

    @Test
    fun invalidReadyWorkflowIsNotPersisted() = withTemporaryDirectory { directory ->
        val store = WorkflowFileStore(directory)
        val invalidReady = Workflow(
            id = "ready",
            name = "Ready",
            steps = emptyList(),
            state = WorkflowState.Ready,
        )

        assertThrows(IllegalArgumentException::class.java) {
            store.save(listOf(invalidReady))
        }
    }

    private fun workflow(id: String, name: String = id) = Workflow(
        id = id,
        name = name,
        steps = listOf(Step.Delay("step-$id", 100)),
    )

    private fun withTemporaryDirectory(block: (java.io.File) -> Unit) {
        val directory = Files.createTempDirectory("workflow-store-test").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}