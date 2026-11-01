package com.kodalabs.skydule.data

import android.content.ContentValues
import android.provider.CalendarContract.Events
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.TimeZone

/**
 * The Calendar Provider columns [CalendarRepository.saveEvent] writes for [e]. A null value means "write NULL".
 * Kept free of Android calls so the rules can be unit tested:
 *  - only a new event (id == null) writes CALENDAR_ID; an edit never moves an event between calendars;
 *  - on an edit, a blank location or description is written as NULL so it really clears;
 *  - recurring events carry RRULE + DURATION and no DTEND; one-off events the reverse.
 */
internal fun eventValueMap(e: NewEvent, timeZoneId: String = TimeZone.getDefault().id): Map<String, Any?> {
    val rule = if (e.repeat == Repeat.CUSTOM) e.customRule else e.repeat.rule
    val v = LinkedHashMap<String, Any?>()
    if (e.id == null) v[Events.CALENDAR_ID] = e.calendarId
    v[Events.TITLE] = e.title
    if (e.location != null) v[Events.EVENT_LOCATION] = e.location
    else if (e.id != null) v[Events.EVENT_LOCATION] = null
    if (e.description != null) v[Events.DESCRIPTION] = e.description
    else if (e.id != null) v[Events.DESCRIPTION] = null
    v[Events.AVAILABILITY] = availabilityToWrite(e)
    v[Events.ACCESS_LEVEL] = accessLevelToWrite(e)
    v[Events.HAS_ALARM] = if (e.reminders.isNotEmpty()) 1 else 0

    if (e.allDay) {
        v[Events.ALL_DAY] = 1
        v[Events.EVENT_TIMEZONE] = "UTC"
        v[Events.DTSTART] = e.startDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        if (rule != null) {
            val days = ChronoUnit.DAYS.between(e.startDay, e.endDay) + 1
            v[Events.RRULE] = rule
            v[Events.DURATION] = "P${days}D"
        } else {
            v[Events.DTEND] = e.endDay.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
    } else {
        v[Events.EVENT_TIMEZONE] = if (e.id != null) e.timeZoneId?.takeIf { it.isNotBlank() } ?: timeZoneId else timeZoneId
        v[Events.DTSTART] = e.startMillis
        if (rule != null) {
            val seconds = maxOf(60L, (e.endMillis - e.startMillis) / 1000)
            v[Events.RRULE] = rule
            v[Events.DURATION] = "P${seconds}S"
        } else {
            v[Events.DTEND] = e.endMillis
        }
    }
    // Editing may switch between repeating and one-off, so clear whichever form is no longer used.
    if (rule == null) {
        v[Events.RRULE] = null
        v[Events.DURATION] = null
    } else {
        v[Events.DTEND] = null
    }
    return v
}

internal fun Map<String, Any?>.toContentValues(): ContentValues = ContentValues().also { cv ->
    for ((key, value) in this) {
        when (value) {
            null -> cv.putNull(key)
            is String -> cv.put(key, value)
            is Int -> cv.put(key, value)
            is Long -> cv.put(key, value)
            else -> error("Unsupported value for $key")
        }
    }
}

/** An event can be edited or deleted only if its calendar is known and writable. Unknown counts as read-only. */
internal fun canModifyEvent(calendars: List<DeviceCalendar>, calendarId: Long): Boolean =
    calendars.firstOrNull { it.id == calendarId }?.writable == true

/** The editor shows Busy/Free; Tentative (and others) stay as stored unless the user changed the choice. */
internal fun availabilityToWrite(e: NewEvent): Int {
    val stored = e.storedAvailability
    if (e.id != null && stored != null && (stored != Events.AVAILABILITY_FREE) == e.busy) return stored
    return if (e.busy) Events.AVAILABILITY_BUSY else Events.AVAILABILITY_FREE
}

/** The editor shows Default/Public/Private; Confidential stays as stored unless the user changed the choice. */
internal fun accessLevelToWrite(e: NewEvent): Int {
    val stored = e.storedAccessLevel
    if (e.id != null && stored != null) {
        val shown = EventVisibility.entries.firstOrNull { it.value == stored } ?: EventVisibility.DEFAULT
        if (shown == e.visibility) return stored
    }
    return e.visibility.value
}

/** False for an event you declined or that was cancelled ([selfStatus] is Attendees status, [status] is Events.STATUS). */
internal fun shouldRing(selfStatus: Int, status: Int): Boolean =
    selfStatus != android.provider.CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED &&
        status != Events.STATUS_CANCELED
