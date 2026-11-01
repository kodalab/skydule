package com.example.gradule

import android.provider.CalendarContract.Events
import com.example.gradule.data.DeviceCalendar
import com.example.gradule.data.EventVisibility
import com.example.gradule.data.NewEvent
import com.example.gradule.data.Repeat
import com.example.gradule.data.canModifyEvent
import com.example.gradule.data.eventValueMap
import com.example.gradule.data.parseDurationMillis
import com.example.gradule.reminders.ReminderJobs
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What an edit writes to the Calendar Provider: no calendar moves, blanks really clear, durations survive a round trip. */
class EventValuesTest {

    private val start = 1_800_000_000_000L
    private val day = LocalDate.of(2026, 11, 2)

    private fun event(
        id: Long? = null,
        calendarId: Long = 7,
        location: String? = null,
        description: String? = null,
        repeat: Repeat = Repeat.NONE,
        allDay: Boolean = false,
        endMillis: Long = start + 3_600_000L,
        endDay: LocalDate = day,
        timeZoneId: String? = null,
    ) = NewEvent(
        calendarId = calendarId, title = "t", allDay = allDay, startMillis = start, endMillis = endMillis,
        startDay = day, endDay = endDay, repeat = repeat, location = location, description = description,
        reminders = emptyList(), busy = true, visibility = EventVisibility.DEFAULT, id = id,
        timeZoneId = timeZoneId,
    )

    private fun values(e: NewEvent) = eventValueMap(e, "Europe/London")

    // --- #1: an edit never moves the event ---

    @Test fun newEventChoosesItsCalendar() {
        assertEquals(7L, values(event(id = null, calendarId = 7))[Events.CALENDAR_ID])
    }

    @Test fun editNeverWritesTheCalendar() {
        // Even if the editor were handed a different calendar id, an update must not carry CALENDAR_ID.
        assertFalse(values(event(id = 42, calendarId = 99)).containsKey(Events.CALENDAR_ID))
    }

    @Test fun onlyKnownWritableCalendarsCanBeModified() {
        val cals = listOf(
            DeviceCalendar(1, "Mine", "me@x", 0, isLocal = false, writable = true),
            DeviceCalendar(2, "Holidays", "g", 0, isLocal = false, writable = false),
        )
        assertTrue(canModifyEvent(cals, 1))
        assertFalse(canModifyEvent(cals, 2))
        assertFalse(canModifyEvent(cals, 3)) // unknown calendar
        assertFalse(canModifyEvent(emptyList(), 1)) // calendars not loaded yet
    }

    // --- #2: clearing a field saves ---

    @Test fun editWithBlankLocationAndNotesClearsThem() {
        val v = values(event(id = 42, location = null, description = null))
        assertTrue(v.containsKey(Events.EVENT_LOCATION))
        assertNull(v[Events.EVENT_LOCATION])
        assertTrue(v.containsKey(Events.DESCRIPTION))
        assertNull(v[Events.DESCRIPTION])
    }

    @Test fun editWithTextWritesIt() {
        val v = values(event(id = 42, location = "Home", description = "Bring cake"))
        assertEquals("Home", v[Events.EVENT_LOCATION])
        assertEquals("Bring cake", v[Events.DESCRIPTION])
    }

    @Test fun newEventWithBlankFieldsLeavesThemOut() {
        val v = values(event(id = null))
        assertFalse(v.containsKey(Events.EVENT_LOCATION))
        assertFalse(v.containsKey(Events.DESCRIPTION))
    }

    // --- #3: durations survive an edit ---

    @Test fun repeatingEventKeepsItsLengthWhateverFormatTheSyncAdapterUsed() {
        for ((stored, expectedSeconds) in listOf(
            "PT1H" to 3600L, "PT3600S" to 3600L, "P3600S" to 3600L, "PT1H30M" to 5400L, "P1DT2H" to 93_600L,
        )) {
            val len = parseDurationMillis(stored)
            val v = values(event(id = 42, repeat = Repeat.WEEKLY, endMillis = start + len))
            assertEquals("P${expectedSeconds}S", v[Events.DURATION])
            assertNotEquals("P60S", v[Events.DURATION])
        }
    }

    @Test fun weeklyAllDayEventKeepsAWeek() {
        // "P1W" used to parse as zero and shrink the event to a single day.
        val end = day.plusDays(parseDurationMillis("P1W") / 86_400_000L - 1)
        val v = values(event(id = 42, repeat = Repeat.WEEKLY, allDay = true, endDay = end))
        assertEquals("P7D", v[Events.DURATION])
    }

    @Test fun repeatingEventHasNoEndAndOneOffHasNoRule() {
        val repeating = values(event(repeat = Repeat.DAILY))
        assertNull(repeating[Events.DTEND])
        assertEquals("FREQ=DAILY", repeating[Events.RRULE])
        val oneOff = values(event())
        assertNull(oneOff[Events.RRULE])
        assertNull(oneOff[Events.DURATION])
        assertEquals(start + 3_600_000L, oneOff[Events.DTEND])
    }

    // --- #4: the content-triggered job re-arms itself without cancelling itself ---

    @Test fun contentJobsAlternate() {
        assertEquals(ReminderJobs.ID_CONTENT_B, ReminderJobs.otherContentJobId(ReminderJobs.ID_CONTENT_A))
        assertEquals(ReminderJobs.ID_CONTENT_A, ReminderJobs.otherContentJobId(ReminderJobs.ID_CONTENT_B))
        assertNotEquals(ReminderJobs.ID_CONTENT_A, ReminderJobs.otherContentJobId(ReminderJobs.ID_CONTENT_A))
    }

    // --- #12: editing keeps the zone a timed event was created in ---

    @Test fun editKeepsTheEventsOwnTimeZone() {
        assertEquals("America/New_York", values(event(id = 5, timeZoneId = "America/New_York"))[Events.EVENT_TIMEZONE])
    }

    @Test fun newOrZonelessEventsUseThePhonesZone() {
        assertEquals("Europe/London", values(event(id = null, timeZoneId = "America/New_York"))[Events.EVENT_TIMEZONE])
        assertEquals("Europe/London", values(event(id = 5, timeZoneId = null))[Events.EVENT_TIMEZONE])
        assertEquals("Europe/London", values(event(id = 5, timeZoneId = " "))[Events.EVENT_TIMEZONE])
    }

    @Test fun allDayIsAlwaysUtcEvenWithAStoredZone() {
        assertEquals("UTC", values(event(id = 5, allDay = true, timeZoneId = "America/New_York"))[Events.EVENT_TIMEZONE])
    }

    // --- #11: the loaded window drops back to normal size ---

    @Test fun windowResetsOnlyWhenItHasGrown() {
        val today = LocalDate.of(2026, 11, 2)
        val normal = com.example.gradule.data.defaultWindow(today)
        assertFalse(com.example.gradule.data.shouldResetWindow(normal))
        assertFalse(com.example.gradule.data.shouldResetWindow(normal.copy(endExclusive = normal.endExclusive.plusDays(120))))
        assertTrue(com.example.gradule.data.shouldResetWindow(normal.copy(endExclusive = normal.endExclusive.plusDays(240), start = normal.start.minusDays(120))))
    }
}
