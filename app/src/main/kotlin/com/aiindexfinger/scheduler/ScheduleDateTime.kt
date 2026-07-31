package com.aiindexfinger.scheduler

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

internal fun localScheduleEpochMillis(
    date: LocalDate,
    time: LocalTime,
    zoneId: ZoneId,
): Long {
    val localDateTime = LocalDateTime.of(date, time)
    val validOffsets = zoneId.rules.getValidOffsets(localDateTime)
    require(validOffsets.isNotEmpty()) { "由于夏令时调整，所选本地时间不存在" }
    return localDateTime.atOffset(validOffsets.first()).toInstant().toEpochMilli()
}

internal fun nextOccurrenceEpochMillis(
    previousEpochMillis: Long,
    recurrence: ScheduleRecurrence,
    zoneId: ZoneId,
    afterEpochMillis: Long = previousEpochMillis,
): Long? {
    if (recurrence == ScheduleRecurrence.Once) return null
    var nextLocal = Instant.ofEpochMilli(previousEpochMillis).atZone(zoneId).toLocalDateTime()
    while (true) {
        nextLocal = when (recurrence) {
            ScheduleRecurrence.Once -> return null
            ScheduleRecurrence.Daily -> nextLocal.plusDays(1)
            ScheduleRecurrence.Weekly -> nextLocal.plusWeeks(1)
        }
        val offsets = zoneId.rules.getValidOffsets(nextLocal)
        val nextEpochMillis = if (offsets.isNotEmpty()) {
            nextLocal.atOffset(offsets.first()).toInstant().toEpochMilli()
        } else {
            val transition = requireNotNull(zoneId.rules.getTransition(nextLocal))
            nextLocal.plus(transition.duration)
                .atOffset(transition.offsetAfter)
                .toInstant()
                .toEpochMilli()
        }
        if (nextEpochMillis > afterEpochMillis) return nextEpochMillis
    }
}
