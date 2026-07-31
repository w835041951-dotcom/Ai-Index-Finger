package com.aiindexfinger.data

import com.aiindexfinger.model.Step
import com.aiindexfinger.model.Workflow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowLibraryTest {
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

    private fun library() = WorkflowLibrary(workflows = listOf(workflow()))

    private fun workflow() = Workflow(
        id = "workflow",
        name = "Workflow",
        steps = listOf(Step.Delay("step", 100)),
    )
}