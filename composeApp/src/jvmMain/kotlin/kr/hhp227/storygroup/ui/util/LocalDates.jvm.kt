package kr.hhp227.storygroup.ui.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId

actual fun todayLocal(): LocalStamp {
    val now = LocalDateTime.now()
    return LocalStamp(now.year, now.monthValue, now.dayOfMonth, now.hour, now.minute)
}

actual fun monthLength(year: Int, month: Int): Int = YearMonth.of(year, month).lengthOfMonth()

// java.time DayOfWeek는 월=1…일=7 — 캘린더 규약(일=0…토=6)으로 변환
actual fun firstDayOfWeekOfMonth(year: Int, month: Int): Int =
    LocalDate.of(year, month, 1).dayOfWeek.value % 7

actual fun isoToLocal(iso: String): LocalStamp? = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
}.getOrNull()?.let { LocalStamp(it.year, it.monthValue, it.dayOfMonth, it.hour, it.minute) }

actual fun localToIso(year: Int, month: Int, day: Int, hour: Int, minute: Int): String =
    LocalDateTime.of(year, month, day, hour, minute)
        .atZone(ZoneId.systemDefault())
        .toOffsetDateTime()
        .toString()
