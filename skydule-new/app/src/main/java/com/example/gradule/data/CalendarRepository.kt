package com.example.gradule.data

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import android.database.Cursor
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.TimeZone

/**
 * Thin wrapper around Android's Calendar Provider.
 *
 * Google-account calendars (synced by the phone) and on-device calendars all live in
 * this provider, so reading/writing here covers both with no OAuth or API keys.
 * Writes to Google calendars are synced upstream by Android's own sync adapter.
 */
class CalendarRepository(private val resolver: ContentResolver) {

    /** Emits once immediately, then whenever anything in the calendar store changes. */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    /**
     * Queries [base] plus [optional] columns. The optional ones (event status, your response) should be available
     * through Instances, but if a phone's provider refuses them the query is repeated with [base] alone, so a missing
     * extra column can never blank out the calendar or the reminders. Check `c.columnCount > base.size` before reading them.
     */
    private fun queryInstances(uri: Uri, base: Array<String>, optional: Array<String>, sort: String? = null): Cursor? =
        try {
            resolver.query(uri, base + optional, null, null, sort)
        } catch (e: IllegalArgumentException) {
            Diag.record("instancesOptionalColumns", e)
            resolver.query(uri, base, null, null, sort)
        } catch (e: android.database.sqlite.SQLiteException) {
            Diag.record("instancesOptionalColumns", e)
            resolver.query(uri, base, null, null, sort)
        }

    suspend fun calendars(): List<DeviceCalendar> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            Calendars._ID,
            Calendars.CALENDAR_DISPLAY_NAME,
            Calendars.ACCOUNT_NAME,
            Calendars.ACCOUNT_TYPE,
            Calendars.CALENDAR_COLOR,
            Calendars.CALENDAR_ACCESS_LEVEL,
        )
        val out = mutableListOf<DeviceCalendar>()
        try {
            resolver.query(Calendars.CONTENT_URI, projection, null, null, Calendars.ACCOUNT_NAME)?.use { c ->
                while (c.moveToNext()) {
                    out += DeviceCalendar(
                        id = c.getLong(0),
                        name = c.getString(1) ?: "Calendar",
                        account = c.getString(2) ?: "",
                        color = c.getInt(4),
                        accountType = c.getString(3).orEmpty(),
                        isLocal = c.getString(3) == CalendarContract.ACCOUNT_TYPE_LOCAL,
                        writable = c.getInt(5) >= Calendars.CAL_ACCESS_CONTRIBUTOR,
                    )
                }
            }
        } catch (_: SecurityException) {
            // permission revoked while running
        }
        out
    }

    /**
     * All event instances (recurrences expanded) that touch any day in [window].
     *
     * The provider stores all-day events as UTC midnight-to-midnight, so their calendar dates are
     * read in UTC; timed events are read in the device time zone.
     */
    suspend fun events(window: DateWindow, prefs: CalendarPrefs = CalendarPrefs.NONE): List<CalendarEvent> = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        // Pad the query by a day each side so all-day events near the edges are not missed.
        val queryStart = window.start.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val queryEnd = window.endExclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, queryStart)
            ContentUris.appendId(it, queryEnd)
        }.build()

        val projection = arrayOf(
            Instances.EVENT_ID,
            Instances.TITLE,
            Instances.BEGIN,
            Instances.END,
            Instances.ALL_DAY,
            Instances.EVENT_LOCATION,
            Instances.EVENT_COLOR,
            Instances.CALENDAR_COLOR,
            Instances.CALENDAR_DISPLAY_NAME,
            Instances.CALENDAR_ID,
        )
        val out = mutableListOf<CalendarEvent>()
        try {
            queryInstances(uri, projection, arrayOf(Instances.STATUS), "${Instances.BEGIN} ASC")?.use { c ->
                val hasStatus = c.columnCount > projection.size
                while (c.moveToNext()) {
                    val calendarId = c.getLong(9)
                    if (prefs.isHidden(calendarId)) continue
                    // A cancelled occurrence (a deleted one in a repeating series) stays in the provider but not on screen.
                    if (hasStatus && c.getInt(projection.size) == Events.STATUS_CANCELED) continue
                    val begin = c.getLong(2)
                    val end = c.getLong(3)
                    val allDay = c.getInt(4) == 1

                    val startDay: LocalDate
                    val rawEndDay: LocalDate
                    if (allDay) {
                        startDay = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
                        rawEndDay = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate().minusDays(1)
                    } else {
                        startDay = Instant.ofEpochMilli(begin).atZone(zone).toLocalDate()
                        rawEndDay = Instant.ofEpochMilli(maxOf(begin, end - 1)).atZone(zone).toLocalDate()
                    }
                    val endDay = if (rawEndDay.isBefore(startDay)) startDay else rawEndDay

                    if (endDay.isBefore(window.start) || !startDay.isBefore(window.endExclusive)) continue

                    val eventColor = c.getInt(6)
                    out += CalendarEvent(
                        id = c.getLong(0),
                        title = c.getString(1).orEmpty(),
                        begin = begin,
                        end = end,
                        allDay = allDay,
                        location = c.getString(5)?.takeIf { it.isNotBlank() },
                        // An event's own colour wins; otherwise the colour the user chose for its calendar, else the calendar's.
                        color = if (eventColor != 0) eventColor else prefs.colorFor(calendarId, c.getInt(7)),
                        calendarName = c.getString(8).orEmpty(),
                        startDay = startDay,
                        endDay = endDay,
                        calendarId = calendarId,
                    )
                }
            }
        } catch (_: SecurityException) {
        }
        out
    }

    /**
     * Creates an event with everything the editor offers. Per the provider's contract, all-day events are
     * stored as UTC midnight to the next UTC midnight, and recurring events carry a DURATION instead of DTEND.
     */
    suspend fun saveEvent(e: NewEvent): Long? = withContext(Dispatchers.IO) {
        // What gets written, and why, is in eventValueMap (unit tested): no calendar change on edits, blanks clear.
        val values = eventValueMap(e).toContentValues()
        try {
            val existing = e.id
            val minutesList = e.reminders.distinct()
            if (existing == null) {
                // One batch: the event and its reminders go in together, each reminder pointing back at the new event.
                val ops = ArrayList<ContentProviderOperation>()
                ops += ContentProviderOperation.newInsert(Events.CONTENT_URI).withValues(values).build()
                for (minutes in minutesList) {
                    ops += ContentProviderOperation.newInsert(Reminders.CONTENT_URI)
                        .withValueBackReference(Reminders.EVENT_ID, 0)
                        .withValue(Reminders.MINUTES, minutes)
                        .withValue(Reminders.METHOD, if (minutes == Reminders.MINUTES_DEFAULT) Reminders.METHOD_DEFAULT else Reminders.METHOD_ALERT)
                        .build()
                }
                resolver.applyBatch(CalendarContract.AUTHORITY, ops)[0].uri?.lastPathSegment?.toLongOrNull()
            } else {
                // The event update and the reminder swap are ONE batch (the provider runs a batch in a single
                // transaction), so a failure leaves the event exactly as it was. withExpectedCount(1) makes the batch
                // fail, and roll back, if the event is gone or not updatable, so reminders are never written for it.
                val ops = ArrayList<ContentProviderOperation>()
                ops += ContentProviderOperation.newUpdate(ContentUris.withAppendedId(Events.CONTENT_URI, existing))
                    .withValues(values)
                    .withExpectedCount(1)
                    .build()
                // Only the in-app notifications the editor showed are replaced; email reminders and the like stay.
                ops += ContentProviderOperation.newDelete(Reminders.CONTENT_URI)
                    .withSelection(
                        "${Reminders.EVENT_ID} = ? AND (${Reminders.METHOD} = ? OR ${Reminders.METHOD} = ?)",
                        arrayOf(existing.toString(), Reminders.METHOD_ALERT.toString(), Reminders.METHOD_DEFAULT.toString()),
                    )
                    .build()
                for (minutes in minutesList) {
                    ops += ContentProviderOperation.newInsert(Reminders.CONTENT_URI)
                        .withValue(Reminders.EVENT_ID, existing)
                        .withValue(Reminders.MINUTES, minutes)
                        .withValue(Reminders.METHOD, if (minutes == Reminders.MINUTES_DEFAULT) Reminders.METHOD_DEFAULT else Reminders.METHOD_ALERT)
                        .build()
                }
                resolver.applyBatch(CalendarContract.AUTHORITY, ops)
                existing
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Exception) {
            // Provider rejections (bad rule, read-only calendar, database error) shouldn't crash the app.
            Log.w(TAG, "saveEvent failed", t)
            Diag.record("saveEvent", t)
            null
        }
    }

    /**
     * Notifications that should fire in [fromMillis, toMillis), expanded from the event instances in that range.
     * Timed reminders are relative to the start. All-day reminders are relative to local midnight of the event's date, as Android and Google Calendar store them.
     */
    suspend fun upcomingAlerts(
        fromMillis: Long,
        toMillis: Long,
        prefs: CalendarPrefs = CalendarPrefs.NONE,
    ): List<UpcomingAlert> = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val remindersByEvent = HashMap<Long, MutableList<Int>>()
        try {
            resolver.query(
                Reminders.CONTENT_URI,
                arrayOf(Reminders.EVENT_ID, Reminders.MINUTES),
                "${Reminders.METHOD} = ? OR ${Reminders.METHOD} = ?",
                arrayOf(Reminders.METHOD_ALERT.toString(), Reminders.METHOD_DEFAULT.toString()),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    remindersByEvent.getOrPut(c.getLong(0)) { mutableListOf() }.add(resolveMinutes(c.getInt(1)))
                }
            }
        } catch (_: SecurityException) {
            return@withContext emptyList()
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Exception) {
            Log.w(TAG, "upcomingAlerts reminders failed", t)
            Diag.record("upcomingAlerts", t)
            return@withContext emptyList()
        }
        if (remindersByEvent.isEmpty()) return@withContext emptyList()

        // A reminder fires up to its lead time before its event, so events beyond the planning horizon can still have
        // a reminder inside it (a 3-week reminder for an event 20 days away). Fetch instances that far out.
        val longestLead = remindersByEvent.values.maxOf { list -> list.maxOrNull() ?: 0 }
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, fromMillis - 86_400_000L)
            ContentUris.appendId(it, alertQueryEnd(toMillis, longestLead))
        }.build()
        val out = mutableListOf<UpcomingAlert>()
        try {
            val base = arrayOf(
                Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.ALL_DAY, Instances.EVENT_LOCATION,
                Instances.EVENT_COLOR, Instances.CALENDAR_COLOR, Instances.CALENDAR_ID,
            )
            queryInstances(uri, base, arrayOf(Instances.SELF_ATTENDEE_STATUS, Instances.STATUS))?.use { c ->
                val hasExtras = c.columnCount > base.size
                while (c.moveToNext()) {
                    val eventId = c.getLong(0)
                    // Hidden calendars are hidden everywhere, so they don't ring either.
                    if (prefs.isHidden(c.getLong(7))) continue
                    // Meetings you declined or that were cancelled don't ring.
                    if (hasExtras && !shouldRing(c.getInt(8), c.getInt(9))) continue
                    val minutesList = remindersByEvent[eventId] ?: continue
                    val begin = c.getLong(2)
                    val allDay = c.getInt(3) == 1
                    val anchor = reminderAnchorMillis(begin, allDay, zone)
                    val title = c.getString(1).orEmpty()
                    val location = c.getString(4)?.takeIf { it.isNotBlank() }
                    val color = if (c.getInt(5) != 0) c.getInt(5) else prefs.colorFor(c.getLong(7), c.getInt(6))
                    for (minutes in minutesList.distinct()) {
                        val fireAt = anchor - minutes * 60_000L
                        if (fireAt >= fromMillis && fireAt < toMillis) {
                            out += UpcomingAlert(eventId, title, location, begin, allDay, minutes, fireAt, color)
                        }
                    }
                }
            }
        } catch (_: SecurityException) {
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Exception) {
            Log.w(TAG, "upcomingAlerts failed", t)
            Diag.record("upcomingAlerts", t)
        }
        out.sortedBy { it.fireAt }
    }

    /**
     * Loads an existing event into the editor's form. For a recurring event this describes the whole series.
     * Returns null if the event can't be read.
     */
    suspend fun draftFor(eventId: Long): NewEvent? = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        try {
            val draft = resolver.query(
                ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
                arrayOf(
                    Events.CALENDAR_ID,
                    Events.TITLE,
                    Events.DTSTART,
                    Events.DTEND,
                    Events.DURATION,
                    Events.RRULE,
                    Events.ALL_DAY,
                    Events.EVENT_LOCATION,
                    Events.DESCRIPTION,
                    Events.AVAILABILITY,
                    Events.ACCESS_LEVEL,
                    Events.EVENT_TIMEZONE,
                ),
                null,
                null,
                null,
            )?.use { c ->
                if (!c.moveToFirst()) return@withContext null
                val allDay = c.getInt(6) == 1
                val start = c.getLong(2)
                val end = if (c.isNull(3)) start + parseDurationMillis(c.getString(4)) else c.getLong(3)
                val rrule = c.getString(5)

                val startDay: LocalDate
                val endDay: LocalDate
                if (allDay) {
                    startDay = Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC).toLocalDate()
                    val endExclusive = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate()
                    endDay = maxOf(startDay, endExclusive.minusDays(1))
                } else {
                    startDay = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
                    endDay = maxOf(startDay, Instant.ofEpochMilli(maxOf(start, end - 1)).atZone(zone).toLocalDate())
                }

                NewEvent(
                    calendarId = c.getLong(0),
                    title = c.getString(1).orEmpty(),
                    allDay = allDay,
                    startMillis = start,
                    endMillis = end,
                    startDay = startDay,
                    endDay = endDay,
                    repeat = when {
                        rrule.isNullOrBlank() -> Repeat.NONE
                        else -> Repeat.entries.firstOrNull { r -> r.rule != null && rrule == r.rule } ?: Repeat.CUSTOM
                    },
                    customRule = rrule?.takeIf { it.isNotBlank() },
                    location = c.getString(7)?.takeIf { it.isNotBlank() },
                    description = c.getString(8)?.takeIf { it.isNotBlank() },
                    reminders = remindersFor(eventId),
                    busy = c.getInt(9) != Events.AVAILABILITY_FREE,
                    visibility = EventVisibility.entries.firstOrNull { it.value == c.getInt(10) }
                        ?: EventVisibility.DEFAULT,
                    id = eventId,
                    timeZoneId = if (allDay) null else c.getString(11)?.takeIf { it.isNotBlank() },
                    storedAccessLevel = c.getInt(10),
                    storedAvailability = c.getInt(9),
                )
            }
            draft
        } catch (_: SecurityException) {
            null
        }
    }

    private fun remindersFor(eventId: Long): List<Int> {
        val out = mutableListOf<Int>()
        resolver.query(
            Reminders.CONTENT_URI,
            arrayOf(Reminders.MINUTES),
            "${Reminders.EVENT_ID} = ? AND (${Reminders.METHOD} = ? OR ${Reminders.METHOD} = ?)",
            arrayOf(eventId.toString(), Reminders.METHOD_ALERT.toString(), Reminders.METHOD_DEFAULT.toString()),
            null,
        // Raw values: a calendar-default reminder (-1) stays -1 so an edit writes it back as the default, not as 10 min.
        )?.use { c -> while (c.moveToNext()) out += c.getInt(0) }
        return out.distinct().sorted()
    }

    /**
     * Reminders.MINUTES_DEFAULT (-1) means "use the calendar's default", which the provider doesn't expose.
     * Treat it as the common default of 10 minutes so such reminders still fire (and show sensibly in the editor).
     */
    private fun resolveMinutes(minutes: Int): Int =
        if (minutes == Reminders.MINUTES_DEFAULT) DEFAULT_REMINDER_MINUTES else minutes

    /** Notes for one event. Loaded on demand by the detail page, so list loads don't carry every description. */
    suspend fun description(eventId: Long): String? = withContext(Dispatchers.IO) {
        try {
            resolver.query(
                ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
                arrayOf(Events.DESCRIPTION),
                null, null, null,
            )?.use { c -> if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Exception) {
            Log.w(TAG, "description failed", t)
            Diag.record("description", t)
            null
        }
    }

    /** True if this event is a changed single occurrence of a series (it points at a parent event). */
    suspend fun isModifiedOccurrence(eventId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            resolver.query(
                ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
                arrayOf(Events.ORIGINAL_ID, Events.ORIGINAL_SYNC_ID),
                null, null, null,
            )?.use { c -> c.moveToFirst() && (!c.isNull(0) || !c.getString(1).isNullOrBlank()) } ?: false
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Exception) {
            Diag.record("isModifiedOccurrence", t)
            false
        }
    }

    /**
     * Deletes the event (for recurring events this removes the whole series). A changed single occurrence of a series
     * is cancelled instead: deleting its row would only undo the change and the original occurrence would come back.
     */
    suspend fun deleteEvent(eventId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
            if (isModifiedOccurrence(eventId)) {
                resolver.update(uri, ContentValues().apply { put(Events.STATUS, Events.STATUS_CANCELED) }, null, null) > 0
            } else {
                resolver.delete(uri, null, null) > 0
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Exception) {
            Log.w(TAG, "deleteEvent failed", t)
            Diag.record("deleteEvent", t)
            false
        }
    }

    /** Creates a calendar that exists only on this phone (never synced to any account). */
    suspend fun createLocalCalendar(name: String, color: Int): Long? = withContext(Dispatchers.IO) {
        val uri = Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT)
            .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, name)
            put(Calendars.CALENDAR_DISPLAY_NAME, name)
            put(Calendars.CALENDAR_COLOR, color)
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, LOCAL_ACCOUNT)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.VISIBLE, 1)
            put(Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
        }
        try {
            resolver.insert(uri, values)?.lastPathSegment?.toLongOrNull()
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Exception) {
            Log.w(TAG, "createLocalCalendar failed", t)
            Diag.record("createLocalCalendar", t)
            null
        }
    }

    private companion object {
        const val LOCAL_ACCOUNT = "Skydule"
        const val TAG = "CalendarRepository"
        const val DEFAULT_REMINDER_MINUTES = 10
    }
}
