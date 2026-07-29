package com.aiindexfinger.scheduler

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
    require(validOffsets.isNotEmpty()) { "The selected local time does not exist due to daylight saving time" }
    return localDateTime.atOffset(validOffsets.first()).toInstant().toEpochMilli()
}
