package com.example.gradule

import com.example.gradule.data.CalendarEvent
import com.example.gradule.data.allDayDate
import com.example.gradule.data.allDayHeadline
import com.example.gradule.data.allDayReminderLabel
import com.example.gradule.data.allDayReminderMinutes
import com.example.gradule.data.allDayReminderParts
import com.example.gradule.data.alertQueryEnd
import com.example.gradule.data.groupByDay
import com.example.gradule.data.parseDurationMillis
import com.example.gradule.data.reminderAnchorMillis
import com.example.gradule.data.reminderLabel
import com.example.gradule.data.remindersForAllDay
import com.example.gradule.data.remindersForTimed
import com.example.gradule.icons.DayIcon
import com.example.gradule.icons.IconPeriod
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarLogicTest {

    @Test fun durationSecondsAndDays() {
        assertEquals(3_600_000L, parseDurationMillis("P3600S"))
        assertEquals(2 * 86_400_000L, parseDurationMillis("P2D"))
    }

    @Test fun durationIsoForms() {
        // Sync adapters (Google, CalDAV, Exchange) write these in different shapes; all must read the same.
        assertEquals(3_600_000L, parseDurationMillis("PT1H"))
        assertEquals(3_600_000L, parseDurationMillis("PT3600S"))
        assertEquals(1_800_000L, parseDurationMillis("PT30M"))
        assertEquals(5_400_000L, parseDurationMillis("PT1H30M"))
        assertEquals(7 * 86_400_000L, parseDurationMillis("P1W"))
        assertEquals(86_400_000L + 2 * 3_600_000L + 30 * 60_000L, parseDurationMillis("P1DT2H30M"))
        assertEquals(3_600_000L, parseDurationMillis("+PT1H"))
        assertEquals(3_600_000L, parseDurationMillis(" PT1H "))
    }

    @Test fun durationBadInputIsZero() {
        assertEquals(0L, parseDurationMillis(null))
        assertEquals(0L, parseDurationMillis(""))
        assertEquals(0L, parseDurationMillis("P"))
        assertEquals(0L, parseDurationMillis("PxS"))
        assertEquals(0L, parseDurationMillis("-PT1H"))
        assertEquals(0L, parseDurationMillis("1H"))
        assertEquals(0L, parseDurationMillis("garbage"))
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

    // --- reminders -----------------------------------------------------------------------------------------------

    private val london = ZoneId.of("Europe/London")
    private val la = ZoneId.of("America/Los_Angeles")
    private fun utcMidnight(d: LocalDate) = d.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test fun timedReminderCountsFromStart() {
        assertEquals(12_345L, reminderAnchorMillis(12_345L, false, london))
    }

    @Test fun allDayReminderCountsFromLocalMidnight() {
        // Google's "1 day before at 9:00 AM" is stored as 900 minutes. It must ring at 09:00 the day before,
        // not 9 hours late (the old code counted from 09:00 on the day).
        val day = LocalDate.of(2026, 11, 2)
        for (zone in listOf(london, la, ZoneId.of("Pacific/Auckland"), ZoneId.of("Asia/Kolkata"))) {
            val anchor = reminderAnchorMillis(utcMidnight(day), true, zone)
            assertEquals(day.atStartOfDay(zone).toInstant().toEpochMilli(), anchor)
            val fire = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(anchor - 900 * 60_000L), zone)
            assertEquals(day.minusDays(1).atTime(9, 0), fire.toLocalDateTime())
        }
    }

    @Test fun allDayReminderPartsAndBack() {
        assertEquals(0 to 0, allDayReminderParts(0))
        assertEquals(1 to 1260, allDayReminderParts(180))
        assertEquals(1 to 540, allDayReminderParts(900))
        assertEquals(1 to 0, allDayReminderParts(1440))
        assertEquals(2 to 540, allDayReminderParts(2340))
        assertEquals(7 to 540, allDayReminderParts(9540))
        for (m in listOf(0, 180, 900, 1440, 2340, 9540, 40320)) {
            val (d, t) = allDayReminderParts(m)
            assertEquals(m, allDayReminderMinutes(d, t))
        }
    }

    @Test fun allDayReminderLabels() {
        val hm: (java.time.LocalTime) -> String = { "%02d:%02d".format(it.hour, it.minute) }
        assertEquals("On the day at 00:00", allDayReminderLabel(0, hm))
        assertEquals("1 day before at 21:00", allDayReminderLabel(180, hm))
        assertEquals("1 day before at 09:00", allDayReminderLabel(900, hm))
        assertEquals("2 days before at 09:00", allDayReminderLabel(2340, hm))
        assertEquals("1 week before at 09:00", allDayReminderLabel(9540, hm))
        assertEquals("2 weeks before at 09:00", allDayReminderLabel(14 * 1440 - 540, hm))
    }

    @Test fun allDayNotificationHeadline() {
        val today = LocalDate.of(2026, 11, 1)
        assertEquals("Today", allDayHeadline(today, today))
        assertEquals("Tomorrow", allDayHeadline(today.plusDays(1), today))
        assertEquals("In 3 days", allDayHeadline(today.plusDays(3), today))
        assertEquals("Today", allDayHeadline(today.minusDays(1), today))
    }

    @Test fun remindersFollowTheAllDaySwitch() {
        assertEquals(listOf(900), remindersForAllDay(listOf(10)))
        assertEquals(listOf(900), remindersForAllDay(listOf(0, 10, 30)))
        assertEquals(listOf(900), remindersForAllDay(listOf(1440)))
        assertEquals(listOf(9540), remindersForAllDay(listOf(10080)))
        assertEquals(listOf(900, 9540), remindersForAllDay(listOf(10, 10080)))
        assertEquals(emptyList<Int>(), remindersForAllDay(emptyList()))

        assertEquals(listOf(1440), remindersForTimed(listOf(900), 10))
        assertEquals(listOf(1440), remindersForTimed(listOf(180), 10))
        assertEquals(listOf(10), remindersForTimed(listOf(0), 10))
        assertEquals(listOf(10, 1440), remindersForTimed(listOf(900, 0), 10))
        assertEquals(emptyList<Int>(), remindersForTimed(emptyList(), 10))
    }

    @Test fun lookAheadCoversLongReminders() {
        val now = 1_000_000_000_000L
        val day = 86_400_000L
        val horizon = now + 14 * day
        // A 3-week reminder for an event 20 days out rings 1 day from now, inside the horizon, so that event's
        // instance has to be fetched even though it starts beyond the horizon.
        assertTrue(alertQueryEnd(horizon, 21 * 1440) >= now + 20 * day)
        // No long reminders: only a day of slack for all-day events.
        assertEquals(horizon + day, alertQueryEnd(horizon, 0))
        // A corrupt huge value is capped at 90 days.
        assertEquals(horizon + 90 * day + day, alertQueryEnd(horizon, Int.MAX_VALUE))
        assertEquals(horizon + day, alertQueryEnd(horizon, -5))
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
