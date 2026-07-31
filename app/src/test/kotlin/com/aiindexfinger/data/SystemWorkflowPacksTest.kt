package com.aiindexfinger.data

import com.aiindexfinger.model.Step
import com.aiindexfinger.model.WorkflowState
import com.aiindexfinger.model.WorkflowValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemWorkflowPacksTest {
    @Test
    fun settingsClockAndFilesInstallTwentyOneDistinctDraftsInThreeFolders() {
        var library = WorkflowLibrary()
        val packs = listOf(
            Triple(SettingsWorkflowPack.definition, "Settings", List(15) { "Settings $it" }),
            Triple(ClockWorkflowPack.definition, "Clock", List(3) { "Clock $it" }),
            Triple(FilesWorkflowPack.definition, "Files", List(3) { "Files $it" }),
        )
        packs.forEach { (pack, folderName, names) ->
            library = pack.install(library, folderName, names).library
        }

        assertEquals(21, library.workflows.size)
        assertEquals(21, library.workflows.map { it.id }.distinct().size)
        assertEquals(setOf("Settings", "Clock", "Files"), library.folders.map { it.name }.toSet())
        assertTrue(library.workflows.all { it.state == WorkflowState.Draft })
        assertTrue(library.workflows.all { WorkflowValidator.validate(it).isEmpty() })
        assertTrue(library.workflows.flatMap { it.steps }.filterIsInstance<Step.WaitForNode>().all {
            it.selector.viewId?.startsWith("${it.selector.packageName}:id/") == true
        })
    }

    @Test
    fun allPacksAreIdempotentAndPreserveExistingWorkflows() {
        val packs = listOf(
            SettingsWorkflowPack.definition,
            ClockWorkflowPack.definition,
            FilesWorkflowPack.definition,
        )
        var library = WorkflowLibrary()
        packs.forEach { pack ->
            library = pack.install(library, pack.id, List(pack.workflowIds.size) { "Name $it" }).library
        }
        val customized = library.copy(
            workflows = library.workflows.mapIndexed { index, workflow ->
                if (index == 0) workflow.copy(name = "User edit") else workflow
            },
        )

        var reinstalled = customized
        var added = 0
        packs.forEach { pack ->
            val result = pack.install(reinstalled, pack.id, List(pack.workflowIds.size) { "Name $it" })
            reinstalled = result.library
            added += result.addedWorkflowCount
        }

        assertEquals(0, added)
        assertEquals(customized, reinstalled)
    }

}