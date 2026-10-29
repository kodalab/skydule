package com.example.gradule.data

import java.time.LocalDate

data class DeviceCalendar(
    val id: Long,
    val name: String,
    val account: String,
    val color: Int,
    val isLocal: Boolean,
    val writable: Boolean,
    val accountType: String = "",
)

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
)

/** A half-open range of dates [start, endExclusive) whose events are currently loaded. */
data class DateWindow(val start: LocalDate, val endExclusive: LocalDate)

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

/** Human label for a reminder offset, in the style of Google Calendar ("10 minutes before", "1 day before"). */
fun reminderLabel(minutes: Int): String {
    if (minutes == 0) return "At time of event"
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

/** Parses the provider's DURATION strings: "P3600S" (seconds) or "P3D" (days). Anything else counts as zero. */
internal fun parseDurationMillis(d: String?): Long = when {
    d == null -> 0L
    d.endsWith("S") -> (d.removePrefix("P").removeSuffix("S").toLongOrNull() ?: 0L) * 1000L
    d.endsWith("D") -> (d.removePrefix("P").removeSuffix("D").toLongOrNull() ?: 0L) * 86_400_000L
    else -> 0L
}

/** All-day events are stored as UTC midnights, so their calendar date is read in UTC, never the device zone. */
internal fun allDayDate(utcMillis: Long): LocalDate =
    java.time.Instant.ofEpochMilli(utcMillis).atZone(java.time.ZoneOffset.UTC).toLocalDate()
