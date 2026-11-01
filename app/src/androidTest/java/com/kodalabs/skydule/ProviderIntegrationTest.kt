package com.kodalabs.skydule

import android.Manifest
import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.kodalabs.skydule.data.CalendarPrefs
import com.kodalabs.skydule.data.CalendarRepository
import com.kodalabs.skydule.data.DateWindow
import com.kodalabs.skydule.data.EventVisibility
import com.kodalabs.skydule.data.NewEvent
import com.kodalabs.skydule.data.Repeat
import com.kodalabs.skydule.data.SettingsStore
import com.kodalabs.skydule.data.queryCalendarKeys
import com.kodalabs.skydule.reminders.ReminderReceiver
import com.kodalabs.skydule.reminders.ReminderScheduler
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Skydule's data layer against the REAL Calendar Provider on an emulator: repository, settings, and the reminder
 * scheduler with the real AlarmManager. These check what the provider actually does (batches, expected counts,
 * durations, all-day dates, reminder methods), which a mock cannot. No UI is involved.
 * Each test works in a throwaway on-device calendar that is removed afterwards.
 */
class ProviderIntegrationTest {

    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        *buildList {
            add(Manifest.permission.READ_CALENDAR)
            add(Manifest.permission.WRITE_CALENDAR)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray(),
    )

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver get() = context.contentResolver
    private val repo by lazy { CalendarRepository(resolver) }
    private val zone: ZoneId = ZoneId.systemDefault()
    private val created = mutableListOf<Long>()
    private var calendar = -1L

    @Before fun setUp() {
        calendar = newCalendar("UI integration calendar")
    }

    @After fun tearDown() {
        created.forEach { id ->
            val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "Skydule")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                .build()
            resolver.delete(uri, "${CalendarContract.Calendars._ID} = ?", arrayOf(id.toString()))
        }
    }

    // --- helpers ---

    private fun newCalendar(name: String): Long =
        (runBlocking { repo.createLocalCalendar(name, 0xFF3B82F6.toInt()) } ?: error("calendar not created")).also { created += it }

    private fun at(day: LocalDate, hour: Int) = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private val tomorrow: LocalDate get() = LocalDate.now().plusDays(1)

    private fun draft(
        title: String = "Event",
        day: LocalDate = tomorrow,
        calendarId: Long = calendar,
        repeat: Repeat = Repeat.NONE,
        allDay: Boolean = false,
        location: String? = null,
        reminders: List<Int> = listOf(10),
        id: Long? = null,
        startDay: LocalDate = day,
    ): NewEvent {
        val start = if (allDay) at(startDay, 0) else at(startDay, 9)
        return NewEvent(
            calendarId = calendarId, title = title, allDay = allDay, startMillis = start,
            endMillis = if (allDay) start else start + 3_600_000L, startDay = startDay, endDay = startDay,
            repeat = repeat, location = location, description = null, reminders = reminders,
            busy = true, visibility = EventVisibility.DEFAULT, id = id,
        )
    }

    private fun save(e: NewEvent): Long = runBlocking { repo.saveEvent(e) } ?: error("save failed for ${e.title}")

    private fun reminderRows(eventId: Long): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        resolver.query(
            Reminders.CONTENT_URI, arrayOf(Reminders.MINUTES, Reminders.METHOD),
            "${Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()), null,
        )?.use { c -> while (c.moveToNext()) out += c.getInt(0) to c.getInt(1) }
        return out
    }

    private fun eventUri(id: Long): Uri = ContentUris.withAppendedId(Events.CONTENT_URI, id)

    private fun eventColumn(id: Long, column: String): Int? =
        resolver.query(eventUri(id), arrayOf(column), null, null, null)?.use { if (it.moveToFirst()) it.getInt(0) else null }

    private fun windowAround(day: LocalDate = LocalDate.now()) = DateWindow(day.minusDays(1), day.plusDays(35))

    private fun alertsFor(id: Long, prefs: CalendarPrefs = CalendarPrefs.NONE) = runBlocking {
        val now = System.currentTimeMillis()
        repo.upcomingAlerts(now, now + 5 * 86_400_000L, prefs)
    }.filter { it.eventId == id }

    // --- repository: save, edit, delete ---

    @Test fun newEventRoundTripsThroughTheProvider() {
        val id = save(draft(title = "Round trip", location = "The office", reminders = listOf(10, 60)))
        val back = runBlocking { repo.draftFor(id) }!!
        assertEquals("Round trip", back.title)
        assertEquals("The office", back.location)
        assertEquals(calendar, back.calendarId)
        assertEquals(listOf(10, 60), back.reminders)
        assertEquals(3_600_000L, back.endMillis - back.startMillis)
    }

    @Test fun editNeverMovesTheEventAndBlankLocationReallyClears() {
        val other = newCalendar("UI integration other")
        val id = save(draft(title = "Stays put", location = "Somewhere"))
        // The edit names a different calendar; the provider must keep the original one.
        save(draft(title = "Stays put 2", location = null, calendarId = other, id = id))
        val back = runBlocking { repo.draftFor(id) }!!
        assertEquals(calendar, back.calendarId)
        assertNull(back.location)
        assertEquals("Stays put 2", back.title)
    }

    @Test fun editKeepsEmailRemindersAndReplacesTheAlertOnes() {
        val id = save(draft(reminders = listOf(10)))
        resolver.insert(Reminders.CONTENT_URI, ContentValues().apply {
            put(Reminders.EVENT_ID, id); put(Reminders.MINUTES, 30); put(Reminders.METHOD, Reminders.METHOD_EMAIL)
        })
        save(draft(title = "Edited", reminders = listOf(60), id = id))
        val rows = reminderRows(id)
        assertTrue("email reminder survived", rows.any { it.first == 30 && it.second == Reminders.METHOD_EMAIL })
        assertTrue("new alert present", rows.any { it.first == 60 && it.second != Reminders.METHOD_EMAIL })
        assertFalse("old alert replaced", rows.any { it.first == 10 })
    }

    @Test fun calendarDefaultReminderStaysTheDefaultAfterAnEdit() {
        val id = save(draft(reminders = emptyList()))
        resolver.insert(Reminders.CONTENT_URI, ContentValues().apply {
            put(Reminders.EVENT_ID, id); put(Reminders.MINUTES, Reminders.MINUTES_DEFAULT); put(Reminders.METHOD, Reminders.METHOD_DEFAULT)
        })
        val d = runBlocking { repo.draftFor(id) }!!
        assertEquals(listOf(Reminders.MINUTES_DEFAULT), d.reminders)
        save(d.copy(title = "Renamed"))
        val rows = reminderRows(id)
        assertEquals(listOf(Reminders.MINUTES_DEFAULT to Reminders.METHOD_DEFAULT), rows)
    }

    @Test fun editKeepsConfidentialAndTentativeWhenUntouched() {
        val id = save(draft())
        resolver.update(eventUri(id), ContentValues().apply {
            put(Events.ACCESS_LEVEL, Events.ACCESS_CONFIDENTIAL)
            put(Events.AVAILABILITY, Events.AVAILABILITY_TENTATIVE)
        }, null, null)
        val d = runBlocking { repo.draftFor(id) }!!
        save(d.copy(title = "Renamed only"))
        assertEquals(Events.ACCESS_CONFIDENTIAL, eventColumn(id, Events.ACCESS_LEVEL))
        assertEquals(Events.AVAILABILITY_TENTATIVE, eventColumn(id, Events.AVAILABILITY))
    }

    @Test fun savingAnEventThatIsGoneFailsAndWritesNoReminders() {
        val id = save(draft())
        assertTrue(runBlocking { repo.deleteEvent(id) })
        val result = runBlocking { repo.saveEvent(draft(title = "Ghost", reminders = listOf(10, 30), id = id)) }
        assertNull(result)
        assertTrue("no orphan reminders", reminderRows(id).isEmpty())
    }

    @Test fun deleteRemovesTheEventOnce() {
        val id = save(draft(title = "Delete me"))
        assertTrue(runBlocking { repo.deleteEvent(id) })
        assertFalse(runBlocking { repo.deleteEvent(id) })
        assertNull(runBlocking { repo.draftFor(id) })
    }

    @Test fun deletingAChangedOccurrenceCancelsItAndTheOriginalDoesNotReturn() {
        val series = save(draft(title = "Series", repeat = Repeat.WEEKLY, startDay = LocalDate.now().minusWeeks(1)))
        val occurrence = at(LocalDate.now().plusWeeks(1), 9) // next week's instance
        // Change that one occurrence (an exception event pointing at the series).
        val exceptionUri = resolver.insert(Events.CONTENT_URI, ContentValues().apply {
            put(Events.CALENDAR_ID, calendar)
            put(Events.TITLE, "Series (changed)")
            put(Events.DTSTART, occurrence + 3_600_000L)
            put(Events.DTEND, occurrence + 7_200_000L)
            put(Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
            put(Events.ORIGINAL_ID, series)
            put(Events.ORIGINAL_INSTANCE_TIME, occurrence)
        })
        val exceptionId = ContentUris.parseId(exceptionUri!!)
        fun onThatDay() = runBlocking { repo.events(windowAround()) }
            .filter { (it.id == series || it.id == exceptionId) && it.begin / 86_400_000L == occurrence / 86_400_000L }

        assertTrue(runBlocking { repo.isModifiedOccurrence(exceptionId) })
        assertFalse(runBlocking { repo.isModifiedOccurrence(series) })
        assertEquals(listOf("Series (changed)"), onThatDay().map { it.title })

        assertTrue(runBlocking { repo.deleteEvent(exceptionId) })
        assertTrue("occurrence gone, original did not return: ${onThatDay().map { it.title }}", onThatDay().isEmpty())
        // The series itself is untouched: its other weeks are still there.
        assertTrue(runBlocking { repo.events(windowAround()) }.count { it.id == series } >= 3)
    }

    @Test fun editingARepeatingEventKeepsItsRuleAndDuration() {
        val id = save(draft(title = "Weekly", repeat = Repeat.WEEKLY, startDay = LocalDate.now().minusWeeks(3)))
        save(runBlocking { repo.draftFor(id) }!!.copy(title = "Weekly renamed"))
        val mine = runBlocking { repo.events(windowAround()) }.filter { it.id == id }
        assertTrue("occurrences still generated (${mine.size})", mine.size >= 4)
        assertTrue(mine.all { it.title == "Weekly renamed" && it.end - it.begin == 3_600_000L })
        assertEquals(Repeat.WEEKLY, runBlocking { repo.draftFor(id) }!!.repeat)
    }

    @Test fun allDayEventsKeepTheirCalendarDate() {
        val day = LocalDate.now().plusDays(3)
        val id = save(draft(title = "Holiday", allDay = true, startDay = day, reminders = emptyList()))
        val e = runBlocking { repo.events(windowAround()) }.single { it.id == id }
        assertTrue(e.allDay)
        assertEquals(day, e.startDay)
        assertEquals(day, e.endDay)
    }

    // --- upcoming alerts ---

    @Test fun timedAlertFiresMinutesBeforeStart() {
        val id = save(draft(reminders = listOf(10)))
        val alert = alertsFor(id).single()
        assertEquals(at(tomorrow, 9), alert.begin)
        assertEquals(at(tomorrow, 9) - 10 * 60_000L, alert.fireAt)
    }

    @Test fun allDayAlertCountsBackFromLocalMidnight() {
        // "1 day before at 9:00 AM" is stored as 900 minutes: it must ring at 9:00 the day before.
        val day = LocalDate.now().plusDays(3)
        val id = save(draft(allDay = true, startDay = day, reminders = listOf(900)))
        val alert = alertsFor(id).single()
        assertEquals(day.atStartOfDay(zone).toInstant().toEpochMilli() - 900 * 60_000L, alert.fireAt)
    }

    @Test fun declinedAndCancelledEventsDoNotRing() {
        val declined = save(draft(title = "Declined"))
        val cancelled = save(draft(title = "Cancelled"))
        assertEquals(1, alertsFor(declined).size)
        resolver.update(eventUri(declined), ContentValues().apply { put(Events.SELF_ATTENDEE_STATUS, CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) }, null, null)
        resolver.update(eventUri(cancelled), ContentValues().apply { put(Events.STATUS, Events.STATUS_CANCELED) }, null, null)
        assertTrue("declined", alertsFor(declined).isEmpty())
        assertTrue("cancelled", alertsFor(cancelled).isEmpty())
    }

    @Test fun hiddenCalendarsDoNotRing() {
        val id = save(draft())
        assertTrue(alertsFor(id, CalendarPrefs(hidden = setOf(calendar))).isEmpty())
    }

    // --- settings: choices follow the calendar, not its local id ---

    @Test fun hiddenCalendarIsStoredByKeyAndResolvedBack() = runBlocking {
        val settings = SettingsStore(context)
        val key = queryCalendarKeys(resolver)[calendar]
        assertNotNull(key)
        assertTrue("key is name based for an on-device calendar: $key", key!!.endsWith("name:UI integration calendar"))
        try {
            settings.setCalendarHidden(calendar, true)
            assertTrue(calendar in settings.calendarPrefs.first().hidden)
            settings.setCalendarHidden(calendar, false)
            assertFalse(calendar in settings.calendarPrefs.first().hidden)
        } finally {
            settings.setCalendarHidden(calendar, false)
        }
    }

    // --- reminder scheduler with the real AlarmManager ---

    private fun alarmPending(key: String): PendingIntent? = PendingIntent.getBroadcast(
        context, key.hashCode(),
        Intent(context, ReminderReceiver::class.java)
            .setAction("com.kodalabs.skydule.action.FIRE_REMINDER")
            .setData(Uri.parse("skydule://reminder/$key")),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun scheduledKeys(): Set<String> =
        context.getSharedPreferences("skydule_reminders", Context.MODE_PRIVATE).getStringSet("scheduled", emptySet()).orEmpty()

    @Test fun rescheduleArmsAnAlarmAndCancelsItWhenTheEventIsDeleted() {
        val id = save(draft(title = "Alarm me", reminders = listOf(10)))
        val key = "$id:${at(tomorrow, 9)}:10"

        runBlocking { ReminderScheduler.reschedule(context) }
        assertTrue("planned", key in scheduledKeys())
        assertNotNull("alarm armed", alarmPending(key))

        assertTrue(runBlocking { repo.deleteEvent(id) })
        runBlocking { ReminderScheduler.reschedule(context) }
        assertFalse("no longer planned", key in scheduledKeys())
        assertNull("alarm cancelled", alarmPending(key))
    }
}
