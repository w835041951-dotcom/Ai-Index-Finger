package com.aiindexfinger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersistenceFailureEventTest {
    @Test
    fun identicalFailuresArePublishedAsDistinctEvents() {
        val controller = PersistenceFailureEventController()

        controller.publish("Save failed")
        val first = requireNotNull(controller.event.value)
        controller.consume(first.sequence)
        controller.publish("Save failed")
        val second = requireNotNull(controller.event.value)

        assertEquals(first.message, second.message)
        assertNotEquals(first.sequence, second.sequence)
    }

    @Test
    fun consumedFailureIsNotReplayedToANewCollector() {
        val controller = PersistenceFailureEventController()
        controller.publish("Save failed")

        controller.consume(requireNotNull(controller.event.value).sequence)

        assertNull(controller.event.value)
    }

    @Test
    fun staleConsumerCannotClearANewerFailure() {
        val controller = PersistenceFailureEventController()
        controller.publish("First")
        val first = requireNotNull(controller.event.value)
        controller.publish("Second")

        controller.consume(first.sequence)
        controller.consume(first.sequence)

        assertEquals("Second", controller.event.value?.message)
    }

    @Test
    fun failuresPublishedBeforeConsumptionRemainOrdered() {
        val controller = PersistenceFailureEventController()
        controller.publish("First")
        controller.publish("Second")

        val first = requireNotNull(controller.event.value)
        controller.consume(first.sequence)

        assertEquals("Second", controller.event.value?.message)
    }
}