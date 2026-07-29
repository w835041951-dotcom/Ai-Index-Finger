package com.aiindexfinger.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RunHistoryQueryTest {
    private val records = listOf(
        record("newest", "Daily Check", RunStatus.Completed),
        record("middle", "Account Login", RunStatus.Failed),
        record("oldest", "daily archive", RunStatus.Failed),
    )

    @Test
    fun nameFilterIsTrimmedAndCaseInsensitive() {
        assertEquals(
            listOf("newest", "oldest"),
            filterRunRecords(records, "  DAILY ", null).map { it.id },
        )
    }

    @Test
    fun statusAndNameFiltersCombineWithoutChangingOrder() {
        assertEquals(
            listOf("oldest"),
            filterRunRecords(records, "daily", RunStatus.Failed).map { it.id },
        )
    }

    @Test
    fun emptyFiltersReturnEveryRecordInOriginalOrder() {
        assertEquals(records, filterRunRecords(records, "", null))
    }

    @Test
    fun unmatchedFiltersReturnEmptyList() {
        assertEquals(emptyList<RunRecord>(), filterRunRecords(records, "missing", RunStatus.Rejected))
    }

    private fun record(id: String, workflowName: String, status: RunStatus) = RunRecord(
        id = id,
        workflowId = "workflow-$id",
        workflowName = workflowName,
        startedAtMillis = 1,
        durationMillis = 2,
        status = status,
    )
}