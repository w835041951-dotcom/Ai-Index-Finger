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

    private fun workflow(id: String, name: String) = Workflow(
        id = id,
        name = name,
        steps = listOf(Step.Delay("step-$id", 100)),
    )
}