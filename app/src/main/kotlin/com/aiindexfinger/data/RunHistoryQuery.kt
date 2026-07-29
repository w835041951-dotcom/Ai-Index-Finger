package com.aiindexfinger.data

internal fun filterRunRecords(
    records: List<RunRecord>,
    workflowNameQuery: String,
    status: RunStatus?,
): List<RunRecord> {
    val normalizedQuery = workflowNameQuery.trim()
    return records.filter { record ->
        (normalizedQuery.isEmpty() || record.workflowName.contains(normalizedQuery, ignoreCase = true)) &&
            (status == null || record.status == status)
    }
}
