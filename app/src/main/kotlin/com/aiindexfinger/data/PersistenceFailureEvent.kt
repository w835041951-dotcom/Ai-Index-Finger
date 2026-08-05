package com.aiindexfinger.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PersistenceFailureEvent(
    val sequence: Long,
    val message: String,
)

internal class PersistenceFailureEventController {
    private var nextSequence = 0L
    private val pendingEvents = ArrayDeque<PersistenceFailureEvent>()
    private val mutableEvent = MutableStateFlow<PersistenceFailureEvent?>(null)
    val event = mutableEvent.asStateFlow()

    fun publish(message: String) {
        pendingEvents += PersistenceFailureEvent(++nextSequence, message)
        publishNextIfIdle()
    }

    fun consume(sequence: Long) {
        if (mutableEvent.value?.sequence != sequence) return
        pendingEvents.removeFirstOrNull()
        mutableEvent.value = null
        publishNextIfIdle()
    }

    private fun publishNextIfIdle() {
        if (mutableEvent.value == null) mutableEvent.value = pendingEvents.firstOrNull()
    }
}