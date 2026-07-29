package com.aiindexfinger.data

import com.aiindexfinger.model.Step
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.WorkflowState
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkflowFileStoreTest {
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
    fun corruptPrimaryFallsBackToPreviousValidSave() = withTemporaryDirectory { directory ->
        val store = WorkflowFileStore(directory)
        val first = workflow("first")
        val second = workflow("second")
        store.save(listOf(first))
        store.save(listOf(second))
        directory.resolve("workflows.json").writeText("{truncated")

        assertEquals(listOf(first), store.load())
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

    private fun workflow(id: String) = Workflow(
        id = id,
        name = id,
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