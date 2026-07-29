package com.aiindexfinger.scheduler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkflowScheduleTimingTest {
    @Test
    fun `returns exact millisecond delay for a future target`() {
        assertEquals(60_000, scheduleDelayMillis(1_700_000_060_001, 1_700_000_000_001))
        assertEquals(1, scheduleDelayMillis(101, 100))
    }

    @Test
    fun `rejects current and past targets`() {
        assertThrows(IllegalArgumentException::class.java) { scheduleDelayMillis(100, 100) }
        assertThrows(IllegalArgumentException::class.java) { scheduleDelayMillis(99, 100) }
    }

    @Test
    fun `accepts exactly one year and rejects one millisecond beyond`() {
        assertEquals(
            MAX_SCHEDULE_DELAY_MILLIS,
            scheduleDelayMillis(MAX_SCHEDULE_DELAY_MILLIS + 100, 100),
        )
        assertThrows(IllegalArgumentException::class.java) {
            scheduleDelayMillis(MAX_SCHEDULE_DELAY_MILLIS + 101, 100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            scheduleDelayMillis(Long.MAX_VALUE, 100)
        }
    }
}