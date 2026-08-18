package com.aiindexfinger.scheduler

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

internal enum class ScheduleTimeError {
    NonexistentLocalTime,
    NotInFuture,
    TooFarInFuture,
}

internal class ScheduleTimeException(
    val error: ScheduleTimeError,
) : IllegalArgumentException(error.name)

internal fun localScheduleEpochMillis(
    date: LocalDate,
    time: LocalTime,
    zoneId: ZoneId,
): Long {
    val localDateTime = LocalDateTime.of(date, time)
    val validOffsets = zoneId.rules.getValidOffsets(localDateTime)
    if (validOffsets.isEmpty()) {
        throw ScheduleTimeException(ScheduleTimeError.NonexistentLocalTime)
    }
    return localDateTime.atOffset(validOffsets.first()).toInstant().toEpochMilli()
}

internal fun recurrenceLocalTimeMinutes(
    scheduledAtMillis: Long,
    recurrence: ScheduleRecurrence,
    zoneId: ZoneId,
): Int? = if (recurrence == ScheduleRecurrence.Once) {
    null
} else {
    Instant.ofEpochMilli(scheduledAtMillis).atZone(zoneId).let { local ->
        local.hour * 60 + local.minute
    }
}

internal fun nextOccurrenceEpochMillis(
    previousEpochMillis: Long,
    recurrence: ScheduleRecurrence,
    zoneId: ZoneId,
    afterEpochMillis: Long = previousEpochMillis,
    recurrenceLocalTimeMinutes: Int? = null,
): Long? {
    if (recurrence == ScheduleRecurrence.Once) return null
    var nextLocal = Instant.ofEpochMilli(previousEpochMillis).atZone(zoneId).toLocalDateTime()
    recurrenceLocalTimeMinutes?.let { minuteOfDay ->
        require(minuteOfDay in 0 until 24 * 60)
        nextLocal = LocalDateTime.of(
            nextLocal.toLocalDate(),
            LocalTime.of(minuteOfDay / 60, minuteOfDay % 60),
        )
    }
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
