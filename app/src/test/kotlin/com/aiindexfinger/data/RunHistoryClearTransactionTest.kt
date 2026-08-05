package com.aiindexfinger.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunHistoryClearTransactionTest {
    @Test
    fun transactionDoesNotCompleteBeforeDeletion() = runBlocking {
        val allowDelete = CompletableDeferred<Unit>()
        val result = async {
            clearRunHistory { allowDelete.await() }
        }

        assertFalse(result.isCompleted)
        allowDelete.complete(Unit)
        result.await()
        assertTrue(result.isCompleted)
    }

    @Test
    fun deletionFailureIsPropagated() = runBlocking {
        val failure = runCatching {
            clearRunHistory { error("delete failed") }
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
    }
}