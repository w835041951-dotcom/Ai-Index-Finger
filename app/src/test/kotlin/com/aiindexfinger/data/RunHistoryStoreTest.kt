package com.aiindexfinger.data

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RunHistoryStoreTest {
    @Test
    fun appendPlacesNewRecordFirstAndRemovesOlderDuplicate() = withTemporaryDirectory { directory ->
        val store = RunHistoryStore(directory)
        store.append(record("duplicate", 1))
        store.append(record("other", 2))

        val updated = store.append(record("duplicate", 3))

        assertEquals(listOf("duplicate", "other"), updated.map { it.id })
        assertEquals(3, updated.first().startedAtMillis)
        assertEquals(updated, store.load())
    }

    @Test
    fun appendRetainsOnlyOneHundredNewestRecords() = withTemporaryDirectory { directory ->
        val store = RunHistoryStore(directory)

        repeat(101) { index -> store.append(record("record-$index", index.toLong())) }

        val records = store.load()
        assertEquals(100, records.size)
        assertEquals("record-100", records.first().id)
        assertEquals("record-1", records.last().id)
    }

    @Test
    fun corruptHistoryCannotBeOverwrittenByAppend() = withTemporaryDirectory { directory ->
        val file = directory.resolve("run-history.json")
        val corruptBytes = "{truncated".toByteArray()
        file.writeBytes(corruptBytes)
        val store = RunHistoryStore(directory)

        assertThrows(RunHistoryStorageException::class.java) {
            store.append(record("replacement", 1))
        }
        assertEquals(corruptBytes.toList(), file.readBytes().toList())
    }

    @Test
    fun detailedLoadReportsCorruptHistoryAndPreservesFile() = withTemporaryDirectory { directory ->
        val file = directory.resolve("run-history.json")
        val corruptBytes = "{truncated".toByteArray()
        file.writeBytes(corruptBytes)

        val result = RunHistoryStore(directory).loadDetailed()

        assertEquals(emptyList<RunRecord>(), result.records)
        assertEquals(RunHistoryLoadResult.Corrupt::class, result::class)
        assertEquals(corruptBytes.toList(), file.readBytes().toList())
    }

    private fun record(id: String, startedAtMillis: Long) = RunRecord(
        id = id,
        workflowId = "workflow-$id",
        workflowName = id,
        startedAtMillis = startedAtMillis,
        durationMillis = 1,
        status = RunStatus.Completed,
    )

    private fun withTemporaryDirectory(block: (java.io.File) -> Unit) {
        val directory = Files.createTempDirectory("run-history-store-test").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}