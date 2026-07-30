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
    require(validOffsets.isNotEmpty()) { "由于夏令时调整，所选本地时间不存在" }
    return localDateTime.atOffset(validOffsets.first()).toInstant().toEpochMilli()
}
