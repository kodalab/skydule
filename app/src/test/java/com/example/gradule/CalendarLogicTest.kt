package com.example.gradule

import com.example.gradule.data.CalendarEvent
import com.example.gradule.data.allDayDate
import com.example.gradule.data.groupByDay
import com.example.gradule.data.parseDurationMillis
import com.example.gradule.data.reminderLabel
import com.example.gradule.icons.DayIcon
import com.example.gradule.icons.IconPeriod
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarLogicTest {

    @Test fun durationSecondsAndDays() {
        assertEquals(3_600_000L, parseDurationMillis("P3600S"))
        assertEquals(2 * 86_400_000L, parseDurationMillis("P2D"))
    }

    @Test fun durationBadInputIsZero() {
        assertEquals(0L, parseDurationMillis(null))
        assertEquals(0L, parseDurationMillis("PT1H"))
        assertEquals(0L, parseDurationMillis("PxS"))
    }

    @Test fun allDayDateIsReadInUtc() {
        // 2026-10-29 00:00 UTC must stay the 29th whatever the device zone is.
        val utcMidnight = LocalDate.of(2026, 10, 29).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 10, 29), allDayDate(utcMidnight))
    }

    @Test fun reminderLabels() {
        assertEquals("At time of event", reminderLabel(0))
        assertEquals("1 minute before", reminderLabel(1))
        assertEquals("10 minutes before", reminderLabel(10))
        assertEquals("1 hour before", reminderLabel(60))
        assertEquals("2 days before", reminderLabel(2880))
        assertEquals("1 week before", reminderLabel(10080))
    }

    @Test fun dayIconAliasNames() {
        assertEquals("A0M01D01", DayIcon.aliasFor(LocalDate.of(2026, 1, 1), IconPeriod.DAWN))
        assertEquals("A1M10D29", DayIcon.aliasFor(LocalDate.of(2026, 10, 29), IconPeriod.DAY))
        assertEquals("A3M12D31", DayIcon.aliasFor(LocalDate.of(2026, 12, 31), IconPeriod.NIGHT))
        // Leap day must have an icon too.
        assertEquals("A2M02D29", DayIcon.aliasFor(LocalDate.of(2028, 2, 29), IconPeriod.DUSK))
    }

    @Test fun iconPeriodsByHour() {
        assertEquals(IconPeriod.NIGHT, IconPeriod.at(0))
        assertEquals(IconPeriod.NIGHT, IconPeriod.at(4))
        assertEquals(IconPeriod.DAWN, IconPeriod.at(5))
        assertEquals(IconPeriod.DAWN, IconPeriod.at(7))
        assertEquals(IconPeriod.DAY, IconPeriod.at(8))
        assertEquals(IconPeriod.DAY, IconPeriod.at(16))
        assertEquals(IconPeriod.DUSK, IconPeriod.at(17))
        assertEquals(IconPeriod.DUSK, IconPeriod.at(19))
        assertEquals(IconPeriod.NIGHT, IconPeriod.at(20))
        assertEquals(IconPeriod.NIGHT, IconPeriod.at(23))
    }

    private fun event(id: Long, begin: Long, allDay: Boolean, start: LocalDate, end: LocalDate) = CalendarEvent(
        id = id, title = "e$id", begin = begin, end = begin + 1, allDay = allDay, location = null,
        color = 0, calendarName = "c", startDay = start, endDay = end,
    )

    @Test fun multiDayEventAppearsOnEveryDay() {
        val d = LocalDate.of(2026, 10, 29)
        val grouped = listOf(event(1, 10, true, d, d.plusDays(2))).groupByDay()
        assertEquals(setOf(d, d.plusDays(1), d.plusDays(2)), grouped.keys)
    }

    @Test fun allDayFirstThenByStart() {
        val d = LocalDate.of(2026, 10, 29)
        val list = listOf(
            event(1, 300, false, d, d),
            event(2, 100, false, d, d),
            event(3, 900, true, d, d),
        ).groupByDay().getValue(d)
        assertEquals(listOf(3L, 2L, 1L), list.map { it.id })
    }
}
