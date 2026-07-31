package com.aiindexfinger.data

import com.aiindexfinger.model.Step
import com.aiindexfinger.model.Workflow
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkflowImportMergerTest {
    @Test
    fun conflictingIdsAreRenamedWithoutChangingUniqueWorkflows() {
        val existing = listOf(workflow("same", "Existing"))
        val conflicting = workflow("same", "Imported")
        val unique = workflow("unique", "Unique")

        val normalized = normalizeImportedWorkflows(existing, listOf(conflicting, unique)) { "new-id" }

        assertEquals("new-id", normalized[0].id)
        assertEquals("Imported imported", normalized[0].name)
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

        val merged = mergeImportedLibrary(existing, imported) { "new-workflow" }

        assertEquals(listOf("existing-folder", "empty"), merged.folders.map(WorkflowFolder::id))
        assertEquals("existing-folder", merged.folderIdFor("new-workflow"))
    }

    private fun workflow(id: String, name: String) = Workflow(
        id = id,
        name = name,
        steps = listOf(Step.Delay("step-$id", 100)),
    )
}