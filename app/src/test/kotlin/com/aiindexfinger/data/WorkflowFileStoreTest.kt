package com.aiindexfinger.data

import com.aiindexfinger.model.Step
import com.aiindexfinger.model.Workflow
import java.nio.file.Files
import org.junit.Assert.assertEquals
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