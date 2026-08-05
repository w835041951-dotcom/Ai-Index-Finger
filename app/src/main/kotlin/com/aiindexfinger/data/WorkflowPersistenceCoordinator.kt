package com.aiindexfinger.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async

internal class WorkflowPersistenceCoordinator(
    private val scope: CoroutineScope,
) {
    private val queueLock = Any()
    private var tail: Job? = null

    fun <T> submit(operation: suspend () -> T): Deferred<T> = synchronized(queueLock) {
        val predecessor = tail
        scope.async {
            predecessor?.join()
            operation()
        }.also { tail = it }
    }
}