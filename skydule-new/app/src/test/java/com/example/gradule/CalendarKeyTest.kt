package com.example.gradule

import com.example.gradule.data.calendarKey
import com.example.gradule.data.formatKeyedColors
import com.example.gradule.data.parseKeyedColors
import com.example.gradule.data.resolveCalendarPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hidden calendars and colours must follow the calendar, not the phone's local id (so a restore lands correctly). */
class CalendarKeyTest {
    private val work = calendarKey("com.google", "greg@gmail.com", "greg@gmail.com", "Greg")
    private val holidays = calendarKey("com.google", "greg@gmail.com", "en.uk#holiday@group.v.calendar.google.com", "UK holidays")
    private val local = calendarKey("LOCAL", "On this phone", null, "On this phone")

    @Test fun googleCalendarsKeyOnSyncIdAndLocalOnesOnName() {
        assertTrue(holidays.endsWith("holiday@group.v.calendar.google.com"))
        assertTrue(local.endsWith("name:On this phone"))
        assertFalse(work == holidays)
    }

    @Test fun sameChoicesLandOnTheRightCalendarsWithDifferentIds() {
        val hidden = setOf(holidays)
        val colors = mapOf(local to 0xFF112233.toInt())
        val oldPhone = resolveCalendarPrefs(hidden, colors, mapOf(1L to work, 2L to holidays, 3L to local))
        val newPhone = resolveCalendarPrefs(hidden, colors, mapOf(10L to local, 11L to work, 12L to holidays))
        assertEquals(setOf(2L), oldPhone.hidden)
        assertEquals(setOf(12L), newPhone.hidden)
        assertEquals(mapOf(10L to 0xFF112233.toInt()), newPhone.colors)
    }

    @Test fun keysWithNoCalendarHereAreNotApplied() {
        val p = resolveCalendarPrefs(setOf(holidays), mapOf(holidays to 5), mapOf(1L to work))
        assertTrue(p.hidden.isEmpty()); assertTrue(p.colors.isEmpty())
    }

    @Test fun keyedColorsRoundTripAndTolerateJunk() {
        val m = mapOf(work to 7, local to -16711936)
        assertEquals(m, parseKeyedColors(formatKeyedColors(m)))
        assertTrue(parseKeyedColors("not json").isEmpty())
        assertTrue(parseKeyedColors(null).isEmpty())
    }
}
