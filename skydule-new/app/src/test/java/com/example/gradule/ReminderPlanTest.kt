package com.example.gradule

import android.provider.CalendarContract.Events
import com.example.gradule.data.EventVisibility
import com.example.gradule.data.NewEvent
import com.example.gradule.data.Repeat
import com.example.gradule.data.UpcomingAlert
import com.example.gradule.data.accessLevelToWrite
import com.example.gradule.data.availabilityToWrite
import com.example.gradule.data.allDayReminderLabel
import com.example.gradule.data.legacyIdsAreTrusted
import com.example.gradule.data.reminderLabel
import com.example.gradule.data.remindersForAllDay
import com.example.gradule.data.remindersForTimed
import com.example.gradule.data.shouldRing
import com.example.gradule.data.timedHeadline
import com.example.gradule.reminders.alertKey
import com.example.gradule.reminders.planReminders
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPlanTest {
    private fun alert(id: Long, fireAt: Long, begin: Long = fireAt + 600_000) =
        UpcomingAlert(id, "t", null, begin, false, 10, fireAt, 0)

    @Test fun dueButStillPendingAlarmIsKeptNotRearmedNotCancelled() {
        val late = alert(1, fireAt = 900)
        val plan = planReminders(setOf(alertKey(late)), listOf(late), now = 1_000, max = 10) { true }
        assertTrue(plan.arm.isEmpty())
        assertEquals(setOf(alertKey(late)), plan.keep)
        assertTrue(plan.cancel.isEmpty())
    }

    @Test fun dueAndNoLongerPendingIsNeverRearmed() {
        val fired = alert(1, fireAt = 900)
        val plan = planReminders(setOf(alertKey(fired)), listOf(fired), now = 1_000, max = 10) { false }
        assertTrue(plan.arm.isEmpty())
        assertFalse(alertKey(fired) in plan.keep)
    }

    @Test fun deletedEventsAlarmIsCancelledAndStaysAreNot() {
        val stay = alert(1, 5_000); val gone = alert(2, 6_000)
        val plan = planReminders(setOf(alertKey(stay), alertKey(gone)), listOf(stay), now = 1_000, max = 10) { true }
        assertEquals(listOf(stay), plan.arm)
        assertEquals(setOf(alertKey(gone)), plan.cancel)
    }

    @Test fun capsTheFutureAlarms() {
        val many = (1L..5L).map { alert(it, 2_000 + it) }
        assertEquals(3, planReminders(emptySet(), many, now = 1_000, max = 3) { false }.arm.size)
    }

    // --- declined / cancelled ---
    @Test fun declinedAndCancelledDontRing() {
        assertFalse(shouldRing(2, Events.STATUS_CONFIRMED))      // declined
        assertFalse(shouldRing(0, Events.STATUS_CANCELED))
        assertTrue(shouldRing(1, Events.STATUS_CONFIRMED))       // accepted
        assertTrue(shouldRing(0, Events.STATUS_TENTATIVE))
    }

    // --- stored values survive an untouched edit ---
    private fun ev(busy: Boolean, vis: EventVisibility, access: Int?, avail: Int?, id: Long? = 5) = NewEvent(
        calendarId = 1, title = "t", allDay = false, startMillis = 0, endMillis = 1, startDay = LocalDate.now(),
        endDay = LocalDate.now(), repeat = Repeat.NONE, location = null, description = null, reminders = emptyList(),
        busy = busy, visibility = vis, id = id, storedAccessLevel = access, storedAvailability = avail,
    )

    @Test fun confidentialAndTentativeAreWrittenBackWhenUntouched() {
        val e = ev(busy = true, vis = EventVisibility.DEFAULT, access = Events.ACCESS_CONFIDENTIAL, avail = Events.AVAILABILITY_TENTATIVE)
        assertEquals(Events.ACCESS_CONFIDENTIAL, accessLevelToWrite(e))
        assertEquals(Events.AVAILABILITY_TENTATIVE, availabilityToWrite(e))
    }

    @Test fun changingTheChoiceWins() {
        val e = ev(busy = false, vis = EventVisibility.PRIVATE, access = Events.ACCESS_CONFIDENTIAL, avail = Events.AVAILABILITY_TENTATIVE)
        assertEquals(Events.ACCESS_PRIVATE, accessLevelToWrite(e))
        assertEquals(Events.AVAILABILITY_FREE, availabilityToWrite(e))
    }

    @Test fun newEventsUseTheChoice() {
        val e = ev(busy = true, vis = EventVisibility.PUBLIC, access = null, avail = null, id = null)
        assertEquals(Events.ACCESS_PUBLIC, accessLevelToWrite(e))
        assertEquals(Events.AVAILABILITY_BUSY, availabilityToWrite(e))
    }

    // --- snoozed reminders say what is true now ---
    private val begin = 10_000_000L

    @Test fun normalReminderKeepsItsLeadTime() {
        assertEquals("In 10 minutes", timedHeadline(10, begin, begin - 600_000, snoozed = false))
        assertEquals("Starting now", timedHeadline(0, begin, begin, snoozed = false))
    }

    @Test fun snoozedReminderShowsTimeLeftNotTheOldLeadTime() {
        // Fired 10 min before, snoozed 5 min: 5 min are left, not "In 10 minutes".
        assertEquals("In 5 minutes", timedHeadline(10, begin, begin - 5 * 60_000, snoozed = true))
        assertEquals("In 1 minute", timedHeadline(10, begin, begin - 30_000, snoozed = true))
    }

    @Test fun snoozedReminderAfterStartSaysSoOrHowLongAgo() {
        assertEquals("Starting now", timedHeadline(10, begin, begin + 20_000, snoozed = true))
        assertEquals("Started 5 minutes ago", timedHeadline(10, begin, begin + 5 * 60_000, snoozed = true))
        assertEquals("Started 1 hour ago", timedHeadline(10, begin, begin + 60 * 60_000, snoozed = true))
    }

    // --- calendar-default reminders (-1) are kept as such ---
    @Test fun calendarDefaultHasItsOwnLabelAndSurvivesAllDayConversion() {
        assertEquals("Calendar default", reminderLabel(-1))
        assertEquals(listOf(-1, 900), remindersForAllDay(listOf(-1, 10)))
        assertEquals(listOf(-1), remindersForTimed(listOf(-1), 10))
    }

    @Test fun calendarDefaultIsLabelledOnAllDayEventsToo() {
        assertEquals("Calendar default", allDayReminderLabel(-1))
        assertEquals("1 day before at 9:00", allDayReminderLabel(900) { "%d:%02d".format(it.hour, it.minute) })
    }

    @Test fun oldCalendarIdsAreTrustedOnlyOnAnUpdatedInstall() {
        assertTrue(legacyIdsAreTrusted(firstInstallTime = 1_000, lastUpdateTime = 900_000))
        assertFalse("fresh install: values came from a backup", legacyIdsAreTrusted(firstInstallTime = 1_000, lastUpdateTime = 1_200))
    }
}
