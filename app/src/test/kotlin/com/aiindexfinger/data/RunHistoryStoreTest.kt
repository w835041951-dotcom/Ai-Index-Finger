package com.aiindexfinger.data

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
    fun futureHistoryWithMoreRecordsIsBoundedReadOnlyAndPreserved() =
        withTemporaryDirectory { directory ->
            val file = directory.resolve("run-history.json")
            val records = List(101) { index -> record("record-$index", index.toLong()) }
            val content = kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(RunRecord.serializer()),
                records,
            )
            file.writeText(content)
            val store = RunHistoryStore(directory)

            val loaded = store.loadDetailed() as RunHistoryLoadResult.Loaded

            assertTrue(loaded.readOnly)
            assertEquals(100, loaded.records.size)
            assertThrows(RunHistoryStorageException::class.java) {
                store.append(record("new", 200))
            }
            assertEquals(content, file.readText())
        }

    @Test
    fun oversizedHistoryIsCorruptWithoutReadingOrOverwritingIt() =
        withTemporaryDirectory { directory ->
            val file = directory.resolve("run-history.json")
            java.io.RandomAccessFile(file, "rw").use { it.setLength(32L * 1024 * 1024 + 1) }
            val store = RunHistoryStore(directory)

            assertTrue(store.loadDetailed() is RunHistoryLoadResult.Corrupt)
            assertThrows(RunHistoryStorageException::class.java) {
                store.append(record("new", 1))
            }
            assertEquals(32L * 1024 * 1024 + 1, file.length())
        }

    @Test
    fun deeplyNestedHistoryDataIsSafelyPreserved() = withTemporaryDirectory { directory ->
        val file = directory.resolve("run-history.json")
        val content = buildString {
            append("""[{"id":"deep","workflowId":"workflow","workflowName":"Deep","startedAtMillis":1,"durationMillis":2,"status":"Failed","future":""")
            repeat(2_000) { append('[') }
            append('0')
            repeat(2_000) { append(']') }
            append("}]")
        }
        file.writeText(content)
        val store = RunHistoryStore(directory)

        val result = store.loadDetailed()
        assertTrue(
            result is RunHistoryLoadResult.Corrupt ||
                result is RunHistoryLoadResult.Loaded && result.readOnly,
        )
        assertThrows(RunHistoryStorageException::class.java) {
            store.append(record("new", 3))
        }
        assertEquals(content, file.readText())
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

    @Test
    fun futureRunStatusKeepsHistoryReadableAsUnknown() = withTemporaryDirectory { directory ->
        val file = directory.resolve("run-history.json")
        val content = """[{"id":"future","workflowId":"workflow","workflowName":"Future","startedAtMillis":1,"durationMillis":2,"status":"FutureSuccess"}]"""
        file.writeText(content)
        val store = RunHistoryStore(directory)

        val result = store.loadDetailed()

        assertEquals(RunHistoryLoadResult.Loaded::class, result::class)
        assertTrue((result as RunHistoryLoadResult.Loaded).readOnly)
        assertEquals(listOf(RunStatus.Unknown), result.records.map(RunRecord::status))
        assertThrows(RunHistoryStorageException::class.java) {
            store.append(record("new", 3))
        }
        assertEquals(content, file.readText())
    }

    @Test
    fun futureDiagnosticEnumsKeepHistoryReadableWithSafeDefaults() = withTemporaryDirectory { directory ->
        val file = directory.resolve("run-history.json")
        val content = """[{"id":"future","workflowId":"workflow","workflowName":"Future","startedAtMillis":1,"durationMillis":2,"status":"Failed","diagnostics":[{"sequence":0,"stepId":"step","durationMillis":1,"attemptCount":1,"outcome":"FutureOutcome","location":{"segments":[{"index":0,"branch":"FutureBranch"}]}}]}]"""
        file.writeText(content)
        val store = RunHistoryStore(directory)

        val result = store.loadDetailed()

        assertEquals(RunHistoryLoadResult.Loaded::class, result::class)
        assertTrue((result as RunHistoryLoadResult.Loaded).readOnly)
        assertEquals(RunStepOutcome.Unknown, result.records.single().diagnostics.single().outcome)
        assertEquals(null, result.records.single().diagnostics.single().location?.segments?.single()?.branch)
        assertThrows(RunHistoryStorageException::class.java) {
            store.append(record("new", 3))
        }
        assertEquals(content, file.readText())
    }

    @Test
    fun futureHistoryFieldsRemainReadableButCannotBeOverwritten() =
        withTemporaryDirectory { directory ->
            val file = directory.resolve("run-history.json")
            val content = """[{"id":"future","workflowId":"workflow","workflowName":"Future","startedAtMillis":1,"durationMillis":2,"status":"Failed","futureRecordField":true,"diagnostics":[{"sequence":0,"stepId":"step","durationMillis":1,"attemptCount":1,"outcome":"Failed","futureDiagnosticField":"value","location":{"segments":[{"index":0,"futureSegmentField":9}]}}]}]"""
            file.writeText(content)
            val store = RunHistoryStore(directory)

            val loaded = store.loadDetailed() as RunHistoryLoadResult.Loaded

            assertTrue(loaded.readOnly)
            assertEquals("future", loaded.records.single().id)
            assertThrows(RunHistoryStorageException::class.java) {
                store.append(record("new", 3))
            }
            assertEquals(content, file.readText())
        }

    @Test
    fun futureImageClickDiagnosticFieldsKeepHistoryReadOnly() = withTemporaryDirectory { directory ->
        val file = directory.resolve("run-history.json")
        val content = """[{"id":"future","workflowId":"workflow","workflowName":"Future","startedAtMillis":1,"durationMillis":2,"status":"Completed","diagnostics":[{"sequence":0,"stepId":"image","durationMillis":1,"attemptCount":1,"outcome":"Completed","imageClick":{"selectionMode":"BestMatch","candidateCount":1,"candidatesTruncated":false,"bestScorePermille":950,"bestScalePermille":1000,"plannedClickCount":1,"completedClickCount":1,"futureImageField":true}}]}]"""
        file.writeText(content)
        val store = RunHistoryStore(directory)

        val result = store.loadDetailed() as RunHistoryLoadResult.Loaded

        assertTrue(result.readOnly)
        assertEquals(
            RunImageClickSelectionMode.BestMatch,
            result.records.single().diagnostics.single().imageClick?.selectionMode,
        )
        assertThrows(RunHistoryStorageException::class.java) {
            store.append(record("new", 3))
        }
        assertEquals(content, file.readText())
    }

    @Test
    fun futureImageClickSelectionModeRemainsReadableAsUnknownAndReadOnly() = withTemporaryDirectory { directory ->
        val file = directory.resolve("run-history.json")
        val content = """[{"id":"future","workflowId":"workflow","workflowName":"Future","startedAtMillis":1,"durationMillis":2,"status":"Completed","diagnostics":[{"sequence":0,"stepId":"image","durationMillis":1,"attemptCount":1,"outcome":"Completed","imageClick":{"selectionMode":"FutureSelection","candidateCount":1,"candidatesTruncated":false,"bestScorePermille":950,"bestScalePermille":1000,"plannedClickCount":1,"completedClickCount":1}}]}]"""
        file.writeText(content)
        val store = RunHistoryStore(directory)

        val result = store.loadDetailed() as RunHistoryLoadResult.Loaded

        assertTrue(result.readOnly)
        assertEquals(
            RunImageClickSelectionMode.Unknown,
            result.records.single().diagnostics.single().imageClick?.selectionMode,
        )
        assertThrows(RunHistoryStorageException::class.java) {
            store.append(record("new", 3))
        }
        assertEquals(content, file.readText())
    }

    @Test
    fun clearRemovesCommittedAndInterruptedTemporaryHistory() = withTemporaryDirectory { directory ->
        val file = directory.resolve("run-history.json")
        val temporaryFile = directory.resolve("run-history.json.tmp")
        file.writeText("committed sensitive history")
        temporaryFile.writeText("temporary sensitive history")

        RunHistoryStore(directory).clear()

        assertFalse(file.exists())
        assertFalse(temporaryFile.exists())
    }

    @Test
    fun loadRemovesInterruptedTemporaryHistoryWithoutChangingCommittedData() =
        withTemporaryDirectory { directory ->
            val store = RunHistoryStore(directory)
            val committed = store.append(record("committed", 1))
            val temporaryFile = directory.resolve("run-history.json.tmp")
            temporaryFile.writeText("orphaned sensitive history")

            assertEquals(committed, RunHistoryStore(directory).load())
            assertFalse(temporaryFile.exists())
        }

    @Test
    fun failedAtomicMoveRemovesSensitiveTemporaryFile() = withTemporaryDirectory { directory ->
        val targetDirectory = directory.resolve("target.json").apply { mkdir() }
        val preservedFile = targetDirectory.resolve("preserved").apply { writeText("original") }

        assertThrows(Exception::class.java) {
            AtomicFileWriter.write(targetDirectory, "partial sensitive content")
        }

        assertFalse(directory.resolve("target.json.tmp").exists())
        assertEquals("original", preservedFile.readText())
    }

    @Test
    fun concurrentAtomicWritesProduceOneCompletePayload() = withTemporaryDirectory { directory ->
        val target = directory.resolve("shared.json")
        val payloads = List(12) { index -> "payload-$index:" + "$index".repeat(128_000) }
        val start = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        val executor = Executors.newFixedThreadPool(payloads.size)
        payloads.forEach { payload ->
            executor.execute {
                start.await()
                runCatching { AtomicFileWriter.write(target, payload) }
                    .onFailure { failure.compareAndSet(null, it) }
            }
        }

        start.countDown()
        executor.shutdown()

        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("Concurrent atomic write failed", it) }
        assertTrue(target.readText() in payloads)
        assertFalse(directory.resolve("shared.json.tmp").exists())
    }

    @Test
    fun concurrentStoreInstancesPreserveEveryAppend() = withTemporaryDirectory { directory ->
        val recordCount = 12
        val start = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        val executor = Executors.newFixedThreadPool(recordCount)
        repeat(recordCount) { index ->
            executor.execute {
                start.await()
                runCatching {
                    RunHistoryStore(directory).append(record("record-$index", index.toLong()))
                }.onFailure { failure.compareAndSet(null, it) }
            }
        }

        start.countDown()
        executor.shutdown()

        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("Concurrent history append failed", it) }
        assertEquals(
            (0 until recordCount).mapTo(mutableSetOf()) { "record-$it" },
            RunHistoryStore(directory).load().mapTo(mutableSetOf(), RunRecord::id),
        )
        assertFalse(directory.resolve("run-history.json.tmp").exists())
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