package com.example.gradule

import com.example.gradule.data.CalendarPrefs
import com.example.gradule.data.formatCalendarColors
import com.example.gradule.data.parseCalendarColors
import com.example.gradule.ui.hexOf
import com.example.gradule.ui.parseHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarPrefsTest {
    @Test fun colourChoiceWinsOverTheCalendarsOwn() {
        val prefs = CalendarPrefs(colors = mapOf(7L to 0xFF112233.toInt()))
        assertEquals(0xFF112233.toInt(), prefs.colorFor(7L, 0xFFAAAAAA.toInt()))
        assertEquals(0xFFAAAAAA.toInt(), prefs.colorFor(8L, 0xFFAAAAAA.toInt()))
    }

    @Test fun hiddenIsPerCalendar() {
        val prefs = CalendarPrefs(hidden = setOf(3L))
        assertTrue(prefs.isHidden(3L))
        assertFalse(prefs.isHidden(4L))
        assertFalse(CalendarPrefs.NONE.isHidden(3L))
    }

    @Test fun colourMapRoundTrips() {
        val map = mapOf(1L to 0xFFFF9800.toInt(), 22L to 0xFF2F63DD.toInt())
        assertEquals(map, parseCalendarColors(formatCalendarColors(map)))
    }

    @Test fun badStoredColoursAreSkippedNotFatal() {
        assertEquals(mapOf(5L to 12), parseCalendarColors("x:1,5:12,6:nope,7,"))
        assertEquals(emptyMap<Long, Int>(), parseCalendarColors(null))
        assertEquals(emptyMap<Long, Int>(), parseCalendarColors(""))
    }

    @Test fun hexParsingAndFormatting() {
        assertEquals(0xFFFF9800.toInt(), parseHex("#FF9800"))
        assertEquals(0xFFFF9800.toInt(), parseHex("ff9800"))
        assertNull(parseHex("#FF98"))
        assertNull(parseHex("#GGGGGG"))
        assertEquals("#FF9800", hexOf(0xFFFF9800.toInt()))
    }
}
