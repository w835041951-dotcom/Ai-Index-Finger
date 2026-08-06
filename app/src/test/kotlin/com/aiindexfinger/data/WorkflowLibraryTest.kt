package com.aiindexfinger.data

import com.aiindexfinger.model.Step
import com.aiindexfinger.model.Workflow
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowLibraryTest {
    @Test
    fun upsertWorkflowPreservesOtherWorkflowsAndFolderAssignments() {
        val original = WorkflowLibrary(
            workflows = listOf(
                Workflow(id = "one", name = "One", steps = emptyList()),
                Workflow(id = "two", name = "Two", steps = emptyList()),
            ),
            folders = listOf(WorkflowFolder("folder", "Folder")),
            workflowFolderIds = mapOf("one" to "folder", "two" to "folder"),
        )

        val updated = original.withWorkflow(Workflow(id = "one", name = "Updated", steps = emptyList()))

        assertEquals(listOf("two", "one"), updated.workflows.map { it.id })
        assertEquals("Updated", updated.workflows.last().name)
        assertEquals(mapOf("one" to "folder", "two" to "folder"), updated.workflowFolderIds)
    }

    @Test
    fun addingWorkflowDoesNotChangeExistingLibraryMetadata() {
        val original = WorkflowLibrary(
            workflows = listOf(Workflow(id = "one", name = "One", steps = emptyList())),
            folders = listOf(WorkflowFolder("folder", "Folder")),
            workflowFolderIds = mapOf("one" to "folder"),
        )

        val updated = original.withWorkflow(Workflow(id = "new", name = "New", steps = emptyList()))

        assertEquals(listOf("one", "new"), updated.workflows.map { it.id })
        assertEquals(original.folders, updated.folders)
        assertEquals(original.workflowFolderIds, updated.workflowFolderIds)
    }

    @Test
    fun unchangedBaselineCanBeReplaced() {
        val baseline = Workflow(id = "one", name = "One", steps = emptyList())
        val library = WorkflowLibrary(workflows = listOf(baseline))

        val updated = library.withWorkflowIfUnchanged(
            baseline,
            baseline.copy(name = "Updated"),
        )

        assertEquals("Updated", updated.workflows.single().name)
    }

    @Test
    fun staleBaselineCannotOverwriteNewerWorkflow() {
        val baseline = Workflow(id = "one", name = "One", steps = emptyList())
        val newer = baseline.copy(name = "Floating update")
        val library = WorkflowLibrary(workflows = listOf(newer))

        assertThrows(WorkflowEditConflictException::class.java) {
            library.withWorkflowIfUnchanged(baseline, baseline.copy(name = "Regular update"))
        }
        assertEquals(newer, library.workflows.single())
    }

    @Test
    fun newWorkflowCannotReplaceCollidingId() {
        val existing = Workflow(id = "one", name = "Existing", steps = emptyList())
        val library = WorkflowLibrary(workflows = listOf(existing))

        assertThrows(WorkflowEditConflictException::class.java) {
            library.withWorkflowIfUnchanged(null, existing.copy(name = "New"))
        }
    }

    @Test
    fun folderNamesAreTrimmedAndUniqueIgnoringCase() {
        val library = library().withFolder(WorkflowFolder("first", " Personal "))

        assertEquals("Personal", library.folders.single().name)
        assertThrows(IllegalArgumentException::class.java) {
            library.withFolder(WorkflowFolder("second", "personal"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            library.withFolder(WorkflowFolder("second", "   "))
        }
    }

    @Test
    fun workflowCanMoveIntoFolderAndBackToUnfiled() {
        val folder = WorkflowFolder("folder", "Personal")
        val filed = library().withFolder(folder).moveWorkflow("workflow", folder.id)

        assertEquals(folder.id, filed.folderIdFor("workflow"))
        assertNull(filed.moveWorkflow("workflow", null).folderIdFor("workflow"))
    }

    @Test
    fun movingWorkflowToCurrentDestinationIsNoOp() {
        val folder = WorkflowFolder("folder", "Personal")
        val filed = library().withFolder(folder).moveWorkflow("workflow", folder.id)

        assertTrue(filed === filed.moveWorkflow("workflow", folder.id))
    }

    @Test
    fun deletingFolderMovesContainedWorkflowsToUnfiled() {
        val folder = WorkflowFolder("folder", "Personal")
        val library = library().withFolder(folder).moveWorkflow("workflow", folder.id)

        val updated = library.withoutFolder(folder.id)

        assertEquals(listOf(workflow()), updated.workflows)
        assertEquals(emptyList<WorkflowFolder>(), updated.folders)
        assertNull(updated.folderIdFor("workflow"))
    }

    @Test
    fun normalizationDropsOrphanAssignments() {
        val library = library().copy(
            workflowFolderIds = mapOf(
                "workflow" to "missing-folder",
                "missing-workflow" to "folder",
            ),
        )

        assertEquals(emptyMap<String, String>(), library.normalized().workflowFolderIds)
    }

    @Test
    fun folderFilterAndWorkflowSearchUseAndSemantics() {
        val personal = workflow().copy(name = "Personal morning")
        val work = workflow().copy(id = "work", name = "Work morning")
        val library = WorkflowLibrary(
            workflows = listOf(personal, work),
            folders = listOf(WorkflowFolder("personal", "Personal"), WorkflowFolder("work-folder", "Work")),
            workflowFolderIds = mapOf(personal.id to "personal", work.id to "work-folder"),
        )

        val result = filterWorkflows(
            library.workflows,
            library.workflowFolderIds,
            "morning",
            WorkflowFolderSelection.Folder("personal"),
        )

        assertEquals(listOf(personal), result)
        assertEquals(
            emptyList<Workflow>(),
            filterWorkflows(
                library.workflows,
                library.workflowFolderIds,
                "missing",
                WorkflowFolderSelection.Folder("personal"),
            ),
        )
    }

    @Test
    fun workflowDisplayOrderIsPredictableAcrossFiltersAndSearchWithoutMutatingStorageOrder() {
        val zulu = workflow().copy(id = "zulu", name = "Zulu task")
        val alphaLower = workflow().copy(id = "alpha-lower", name = "alpha task")
        val bravo = workflow().copy(id = "bravo", name = "Bravo task")
        val alphaUpper = workflow().copy(id = "alpha-upper", name = "Alpha task")
        val stored = listOf(zulu, alphaLower, bravo, alphaUpper)
        val folderIds = mapOf(zulu.id to "work", bravo.id to "work")

        assertEquals(
            listOf(alphaUpper, alphaLower, bravo, zulu),
            filterWorkflows(stored, folderIds, "", WorkflowFolderSelection.All, Locale.ENGLISH),
        )
        assertEquals(
            listOf(alphaUpper, alphaLower),
            filterWorkflows(stored, folderIds, "task", WorkflowFolderSelection.Unfiled, Locale.ENGLISH),
        )
        assertEquals(
            listOf(bravo, zulu),
            filterWorkflows(stored, folderIds, "task", WorkflowFolderSelection.Folder("work"), Locale.ENGLISH),
        )
        assertEquals(
            listOf(zulu),
            filterWorkflows(stored, folderIds, "zulu", WorkflowFolderSelection.All, Locale.ENGLISH),
        )
        assertEquals(listOf(zulu, alphaLower, bravo, alphaUpper), stored)
    }

    @Test
    fun foldersUseLocaleAwareDeterministicDisplayOrderWithoutMutatingStorageOrder() {
        val zulu = WorkflowFolder("zulu", "Zulu")
        val alphaLower = WorkflowFolder("alpha-lower", "alpha")
        val bravo = WorkflowFolder("bravo", "Bravo")
        val alphaUpper = WorkflowFolder("alpha-upper", "Alpha")
        val stored = listOf(zulu, alphaLower, bravo, alphaUpper)

        assertEquals(
            listOf(alphaUpper, alphaLower, bravo, zulu),
            sortedFolders(stored, Locale.ENGLISH),
        )
        assertEquals(listOf(zulu, alphaLower, bravo, alphaUpper), stored)
    }

    private fun library() = WorkflowLibrary(workflows = listOf(workflow()))

    private fun workflow() = Workflow(
        id = "workflow",
        name = "Workflow",
        steps = listOf(Step.Delay("step", 100)),
    )
}