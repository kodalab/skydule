package com.example.gradule.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

data class DeviceCalendar(
    val id: Long,
    val name: String,
    val account: String,
    val color: Int,
    val isLocal: Boolean,
    val writable: Boolean,
    val accountType: String = "",
)

/**
 * What the user has changed about calendars inside Skydule: which are hidden, and which colour each one shows.
 * Kept in Skydule's own settings; the Calendar Provider (and so every other app) is never touched.
 */
data class CalendarPrefs(
    val hidden: Set<Long> = emptySet(),
    /** Calendar id to ARGB colour chosen in Skydule. */
    val colors: Map<Long, Int> = emptyMap(),
) {
    fun isHidden(calendarId: Long) = calendarId in hidden

    /** The colour to show for a calendar: the user's choice, else the calendar's own. */
    fun colorFor(calendarId: Long, own: Int): Int = colors[calendarId] ?: own

    companion object { val NONE = CalendarPrefs() }
}

data class CalendarEvent(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String?,
    val color: Int,
    val calendarName: String,
    /** First day the event touches, in the device time zone (all-day events use their own calendar date). */
    val startDay: LocalDate,
    /** Last day the event touches (inclusive). */
    val endDay: LocalDate,
    val description: String? = null,
    /** The calendar this event lives in (-1 if unknown). Used to tell whether it can be edited or deleted. */
    val calendarId: Long = -1L,
)

/** A half-open range of dates [start, endExclusive) whose events are currently loaded. */
data class DateWindow(val start: LocalDate, val endExclusive: LocalDate)

/** Events as last loaded, and the window they were loaded for. [window] is null until the first load finishes. */
data class LoadedEvents(
    val window: DateWindow?,
    val byDay: Map<LocalDate, List<CalendarEvent>>,
) {
    /** True if this snapshot was loaded for a window that includes every day in [from]..[to]. */
    fun covers(from: LocalDate, to: LocalDate): Boolean =
        window != null && !from.isBefore(window.start) && to.isBefore(window.endExclusive)

    companion object { val NOT_LOADED = LoadedEvents(null, emptyMap()) }
}

/** The days loaded at start: 60 back, 270 ahead. */
fun defaultWindow(today: LocalDate) = DateWindow(today.minusDays(60), today.plusDays(270))

/** True once the loaded window has grown to more than twice its normal size (see [defaultWindow]). */
fun shouldResetWindow(w: DateWindow): Boolean =
    java.time.temporal.ChronoUnit.DAYS.between(w.start, w.endExclusive) > 660

/** How often a new event repeats. Rules are iCalendar RRULEs, as the Calendar Provider expects. */
enum class Repeat(val label: String, val rule: String?) {
    NONE("Does not repeat", null),
    DAILY("Every day", "FREQ=DAILY"),
    WEEKLY("Every week", "FREQ=WEEKLY"),
    MONTHLY("Every month", "FREQ=MONTHLY"),
    YEARLY("Every year", "FREQ=YEARLY"),

    /** An existing rule the presets can't express (e.g. every 2 weeks on Mon/Wed); kept as-is in [NewEvent.customRule]. */
    CUSTOM("Custom (keep as is)", null),
}

/** Values match CalendarContract.Events.ACCESS_*: default 0, private 2, public 3. */
enum class EventVisibility(val label: String, val value: Int) {
    DEFAULT("Default visibility", 0),
    PUBLIC("Public", 3),
    PRIVATE("Private", 2),
}

/** Everything the editor collects. Timed events use the millis; all-day events use the (inclusive) dates. */
data class NewEvent(
    val calendarId: Long,
    val title: String,
    val allDay: Boolean,
    val startMillis: Long,
    val endMillis: Long,
    val startDay: LocalDate,
    val endDay: LocalDate,
    val repeat: Repeat,
    val location: String?,
    val description: String?,
    /** Minutes before the start, one entry per notification. Empty means no notifications. */
    val reminders: List<Int>,
    val busy: Boolean,
    val visibility: EventVisibility,
    /** Set when editing an existing event (updates it in place); null when creating a new one. */
    val id: Long? = null,
    /** The raw RRULE to keep when [repeat] is [Repeat.CUSTOM]. */
    val customRule: String? = null,
    /** The time zone a TIMED event was created in; kept on edit so a trip's event doesn't slip to this phone's zone. */
    val timeZoneId: String? = null,
    /** The access level and availability exactly as stored, so an edit that doesn't touch them writes them back as read
     * (the editor only knows Default/Public/Private and Busy/Free; the provider also has Confidential and Tentative). */
    val storedAccessLevel: Int? = null,
    val storedAvailability: Int? = null,
)

/** One notification that should fire at [fireAt] for an event, computed from its instances and reminders. */
data class UpcomingAlert(
    val eventId: Long,
    val title: String,
    val location: String?,
    val begin: Long,
    val allDay: Boolean,
    val minutesBefore: Int,
    val fireAt: Long,
    /** The event's colour (its own, else its calendar's); 0 if unknown. */
    val color: Int = 0,
)

/**
 * The first line of a timed reminder. A normal one says its lead time ("In 10 minutes"); a snoozed one that comes
 * back later says what is true now: time left, "Starting now", or how long ago the event started.
 */
fun timedHeadline(leadMinutes: Int, begin: Long, now: Long, snoozed: Boolean): String {
    fun span(m: Int) = reminderLabel(m).removeSuffix(" before")
    if (!snoozed) return if (leadMinutes == 0) "Starting now" else "In ${span(leadMinutes)}"
    val left = Math.ceil((begin - now) / 60_000.0).toInt()
    return when {
        left >= 1 -> "In ${span(left)}"
        now - begin < 60_000L -> "Starting now"
        else -> "Started ${span(((now - begin) / 60_000L).toInt())} ago"
    }
}

/** Human label for a reminder offset, in the style of Google Calendar ("10 minutes before", "1 day before"). */
fun reminderLabel(minutes: Int): String {
    if (minutes == 0) return "At time of event"
    if (minutes < 0) return "Calendar default"
    fun n(v: Int, unit: String) = "$v $unit${if (v == 1) "" else "s"} before"
    return when {
        minutes % 10080 == 0 -> n(minutes / 10080, "week")
        minutes % 1440 == 0 -> n(minutes / 1440, "day")
        minutes % 60 == 0 -> n(minutes / 60, "hour")
        else -> n(minutes, "minute")
    }
}

/** Buckets events by every day they touch, all-day first and then by start time. */
fun List<CalendarEvent>.groupByDay(): Map<LocalDate, List<CalendarEvent>> {
    val map = HashMap<LocalDate, MutableList<CalendarEvent>>()
    for (e in this) {
        var d = e.startDay
        var n = 0
        while (!d.isAfter(e.endDay) && n < 62) {
            map.getOrPut(d) { mutableListOf() }.add(e)
            d = d.plusDays(1)
            n++
        }
    }
    val order = compareByDescending<CalendarEvent> { it.allDay }.thenBy { it.begin }
    return map.mapValues { (_, list) -> list.sortedWith(order) }
}

private val ISO_DURATION =
    // Standard form is P[n]W[n]DT[n]H[n]M[n]S. Android and Google sync also write bare seconds without the T ("P3600S").
    Regex("^([+-])?P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?|(\\d+)S)?$")

/**
 * Parses an RFC 5545 / ISO 8601 duration as stored in the provider's DURATION column: "P3600S", "PT1H", "PT1H30M",
 * "P2D", "P1W", "P1DT2H". Sync adapters write any of these forms. Anything unreadable, or negative, counts as zero.
 */
internal fun parseDurationMillis(d: String?): Long {
    val m = ISO_DURATION.matchEntire(d?.trim().orEmpty()) ?: return 0L
    if (m.groupValues[1] == "-") return 0L
    fun part(i: Int) = m.groupValues[i].toLongOrNull() ?: 0L
    val seconds = part(2) * 604_800L + part(3) * 86_400L + part(4) * 3_600L + part(5) * 60L + part(6) + part(7)
    return seconds * 1000L
}

// --- Reminder timing ---------------------------------------------------------------------------------------------

/** Longest reminder lead we plan ahead for, so a corrupt value can't make us query years of instances. */
internal const val MAX_LEAD_MINUTES = 90 * 1440

/**
 * The moment a reminder's "minutes before" is counted back from. Timed events: their start. All-day events: local
 * midnight at the start of the event's date, which is how Android and Google Calendar store them ("1 day before at
 * 9:00 AM" is 900 minutes, because 00:00 minus 15 hours is 09:00 the day before).
 */
internal fun reminderAnchorMillis(begin: Long, allDay: Boolean, zone: ZoneId): Long =
    if (allDay) allDayDate(begin).atStartOfDay(zone).toInstant().toEpochMilli() else begin

/**
 * How far past the planning horizon event instances must be fetched: a reminder fires [maxLeadMinutes] before its
 * event, so an event that far beyond the horizon can still have a reminder inside it. Plus a day of slack each way
 * for all-day events, whose reminders count from local midnight rather than the instance's UTC start.
 */
internal fun alertQueryEnd(toMillis: Long, maxLeadMinutes: Int): Long =
    toMillis + maxLeadMinutes.coerceIn(0, MAX_LEAD_MINUTES) * 60_000L + 86_400_000L

/** The default notification for a new all-day event: one day before at 9:00 AM. */
internal const val ALL_DAY_DEFAULT_REMINDER = 900

/** Splits an all-day reminder into whole days before the event's date and the minute of that earlier day it rings. */
internal fun allDayReminderParts(minutes: Int): Pair<Int, Int> {
    val m = minutes.coerceAtLeast(0)
    val days = (m + 1439) / 1440
    return days to (days * 1440 - m)
}

/** The inverse of [allDayReminderParts]; only meaningful when the result is zero or more. */
internal fun allDayReminderMinutes(daysBefore: Int, minuteOfDay: Int): Int = daysBefore * 1440 - minuteOfDay

private fun shortTime(t: LocalTime): String = t.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

/** Label for an all-day reminder, in Google Calendar's style: "1 day before at 9:00 AM". */
fun allDayReminderLabel(minutes: Int, time: (LocalTime) -> String = ::shortTime): String {
    if (minutes < 0) return "Calendar default" // the provider's -1: no fixed time to describe
    val (days, minuteOfDay) = allDayReminderParts(minutes)
    val at = time(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))
    return when {
        days == 0 -> "On the day at $at"
        days % 7 == 0 -> "${days / 7} week${if (days == 7) "" else "s"} before at $at"
        days == 1 -> "1 day before at $at"
        else -> "$days days before at $at"
    }
}

/** First line of an all-day event's notification: when the event is, relative to the day the notification shows. */
internal fun allDayHeadline(eventDate: LocalDate, today: LocalDate): String =
    when (val d = ChronoUnit.DAYS.between(today, eventDate)) {
        in Long.MIN_VALUE..0L -> "Today"
        1L -> "Tomorrow"
        else -> "In $d days"
    }

/** Carries an event's notifications over when the editor's All-day switch is turned on. Timed lead times don't mean the same thing for a whole day. */
internal fun remindersForAllDay(timed: List<Int>): List<Int> =
    timed.map { m -> if (m < 0) m else if (m >= 1440) (m / 1440) * 1440 - 540 else ALL_DAY_DEFAULT_REMINDER }.distinct().sorted()

/** Carries an event's notifications over when the All-day switch is turned off again. */
internal fun remindersForTimed(allDay: List<Int>, fallback: Int): List<Int> =
    allDay.map { m ->
        if (m < 0) return@map m // "calendar default" stays the calendar's default
        val days = allDayReminderParts(m).first
        if (days >= 1) days * 1440 else fallback
    }.distinct().sorted()

/** All-day events are stored as UTC midnights, so their calendar date is read in UTC, never the device zone. */
internal fun allDayDate(utcMillis: Long): LocalDate =
    java.time.Instant.ofEpochMilli(utcMillis).atZone(java.time.ZoneOffset.UTC).toLocalDate()
