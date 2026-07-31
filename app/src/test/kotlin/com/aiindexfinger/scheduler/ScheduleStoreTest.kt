package com.aiindexfinger.scheduler

import java.nio.file.Files
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ScheduleStoreTest {
    @Test
    fun missingFileLoadsEmptyAndAcceptsFirstSchedule() = withTemporaryDirectory { directory ->
        val file = directory.resolve("workflow-schedules.json")
        val store = ScheduleStore.forFile(file)
        val schedule = schedule("one")

        assertEquals(emptyList<WorkflowSchedule>(), store.load())
        assertEquals(listOf(schedule), store.put(schedule))
        assertEquals(listOf(schedule), store.load())
    }

    @Test
    fun corruptFileCannotBeOverwrittenByPut() = withTemporaryDirectory { directory ->
        val file = directory.resolve("workflow-schedules.json")
        file.writeText("{truncated")
        val store = ScheduleStore.forFile(file)

        assertThrows(IllegalStateException::class.java) { store.put(schedule("replacement")) }
        assertEquals("{truncated", file.readText())
    }

    @Test
    fun corruptFileCannotBeDeletedByRemove() = withTemporaryDirectory { directory ->
        val file = directory.resolve("workflow-schedules.json")
        file.writeText("{truncated")
        val store = ScheduleStore.forFile(file)

        assertThrows(IllegalStateException::class.java) { store.remove("missing") }
        assertEquals("{truncated", file.readText())
    }

    @Test
    fun corruptFileCannotBeModifiedByReconciliationOrWorkerUpdates() = withTemporaryDirectory { directory ->
        val file = directory.resolve("workflow-schedules.json")
        val mutations = listOf<(ScheduleStore) -> Unit>(
            { it.removeMissingWorkflows(setOf("one")) },
            { it.consumeMissedOccurrence("one") },
            { it.completeOccurrence("one", 100, 200, ZoneId.of("UTC")) },
            { it.missOccurrence("one", 100, 200, ZoneId.of("UTC")) },
        )

        mutations.forEach { mutation ->
            file.writeText("{truncated")
            val store = ScheduleStore.forFile(file)

            assertThrows(ScheduleStorageException::class.java) { mutation(store) }
            assertEquals("{truncated", file.readText())
        }
    }

    private fun schedule(id: String) = WorkflowSchedule(
        workflowId = id,
        workflowName = "Workflow $id",
        scheduledAtMillis = 123_456_789,
    )

    private fun withTemporaryDirectory(block: (java.io.File) -> Unit) {
        val directory = Files.createTempDirectory("schedule-store-test").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}