package com.aiindexfinger.data

import com.aiindexfinger.model.Step
import com.aiindexfinger.model.Workflow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkflowImportMergerTest {
    @Test
    fun conflictingIdsAreRenamedWithoutChangingUniqueWorkflows() {
        val existing = listOf(workflow("same", "Existing"))
        val conflicting = workflow("same", "Imported")
        val unique = workflow("unique", "Unique")

        val normalized = normalizeImportedWorkflows(
            existing,
            listOf(conflicting, unique),
            newId = { "new-id" },
            importedName = { "$it (imported)" },
        )

        assertEquals("new-id", normalized[0].id)
        assertEquals("Imported (imported)", normalized[0].name)
        assertEquals(unique, normalized[1])
    }

    @Test
    fun libraryMergePreservesEmptyFoldersAndRemapsAssignments() {
        val existing = WorkflowLibrary(
            workflows = listOf(workflow("same", "Existing")),
            folders = listOf(WorkflowFolder("existing-folder", "Personal")),
        )
        val imported = WorkflowLibrary(
            workflows = listOf(workflow("same", "Imported")),
            folders = listOf(
                WorkflowFolder("imported-personal", "personal"),
                WorkflowFolder("empty", "Empty"),
            ),
            workflowFolderIds = mapOf("same" to "imported-personal"),
        )

        val merged = mergeImportedLibrary(
            existing,
            imported,
            newId = { "new-workflow" },
            importedName = { "$it (imported)" },
        )

        assertEquals(listOf("existing-folder", "empty"), merged.folders.map(WorkflowFolder::id))
        assertEquals("existing-folder", merged.folderIdFor("new-workflow"))
    }

    @Test
    fun repeatedWorkflowCollisionsEventuallyUseAUniqueId() {
        var attempts = 0
        val normalized = normalizeImportedWorkflows(
            existing = listOf(workflow("same", "Existing")),
            imported = listOf(workflow("same", "Imported")),
            newId = {
                attempts++
                if (attempts < MAX_UNIQUE_ID_ATTEMPTS) "same" else "unique"
            },
            importedName = { "$it (imported)" },
        )

        assertEquals(MAX_UNIQUE_ID_ATTEMPTS, attempts)
        assertEquals("unique", normalized.single().id)
    }

    @Test
    fun workflowCollisionExhaustionFailsAfterBoundedAttempts() {
        var attempts = 0

        assertThrows(IllegalStateException::class.java) {
            normalizeImportedWorkflows(
                existing = listOf(workflow("same", "Existing")),
                imported = listOf(workflow("same", "Imported")),
                newId = {
                    attempts++
                    "same"
                },
                importedName = { "$it (imported)" },
            )
        }

        assertEquals(MAX_UNIQUE_ID_ATTEMPTS, attempts)
    }

    @Test
    fun folderCollisionUsesBoundedAllocationAndRemapsAssignment() {
        val existing = WorkflowLibrary(
            workflows = emptyList(),
            folders = listOf(WorkflowFolder("same-folder", "Existing")),
        )
        val imported = WorkflowLibrary(
            workflows = listOf(workflow("imported-workflow", "Imported")),
            folders = listOf(WorkflowFolder("same-folder", "Different")),
            workflowFolderIds = mapOf("imported-workflow" to "same-folder"),
        )
        var attempts = 0

        val merged = mergeImportedLibrary(
            existing,
            imported,
            newId = {
                attempts++
                if (attempts < 3) "same-folder" else "new-folder"
            },
            importedName = { "$it (imported)" },
        )

        assertEquals(3, attempts)
        assertEquals("new-folder", merged.folderIdFor("imported-workflow"))
        assertEquals(listOf("same-folder", "new-folder"), merged.folders.map(WorkflowFolder::id))
    }

    @Test
    fun folderCollisionExhaustionFailsAfterBoundedAttempts() {
        val existing = WorkflowLibrary(
            workflows = emptyList(),
            folders = listOf(WorkflowFolder("same-folder", "Existing")),
        )
        val imported = WorkflowLibrary(
            workflows = emptyList(),
            folders = listOf(WorkflowFolder("same-folder", "Different")),
        )
        var attempts = 0

        assertThrows(IllegalStateException::class.java) {
            mergeImportedLibrary(
                existing,
                imported,
                newId = {
                    attempts++
                    "same-folder"
                },
                importedName = { "$it (imported)" },
            )
        }

        assertEquals(MAX_UNIQUE_ID_ATTEMPTS, attempts)
        assertEquals(listOf("same-folder"), existing.folders.map(WorkflowFolder::id))
    }

    @Test
    fun libraryWorkflowCollisionExhaustionFailsWithoutReturningPartialMerge() {
        val existing = WorkflowLibrary(workflows = listOf(workflow("same", "Existing")))
        val imported = WorkflowLibrary(workflows = listOf(workflow("same", "Imported")))
        var attempts = 0

        assertThrows(IllegalStateException::class.java) {
            mergeImportedLibrary(
                existing,
                imported,
                newId = {
                    attempts++
                    "same"
                },
                importedName = { "$it (imported)" },
            )
        }

        assertEquals(MAX_UNIQUE_ID_ATTEMPTS, attempts)
        assertEquals(listOf("same"), existing.workflows.map(Workflow::id))
    }

    private fun workflow(id: String, name: String) = Workflow(
        id = id,
        name = name,
        steps = listOf(Step.Delay("step-$id", 100)),
    )
}
