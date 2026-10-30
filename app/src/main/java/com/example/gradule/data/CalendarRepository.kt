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
    suspend fun events(window: DateWindow): List<CalendarEvent> = withContext(Dispatchers.IO) {
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
        )
        val out = mutableListOf<CalendarEvent>()
        try {
            resolver.query(uri, projection, null, null, "${Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
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
                        color = if (eventColor != 0) eventColor else c.getInt(7),
                        calendarName = c.getString(8).orEmpty(),
                        startDay = startDay,
                        endDay = endDay,
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
        val rule = if (e.repeat == Repeat.CUSTOM) e.customRule else e.repeat.rule
        val values = ContentValues().apply {
            put(Events.CALENDAR_ID, e.calendarId)
            put(Events.TITLE, e.title)
            e.location?.let { put(Events.EVENT_LOCATION, it) }
            e.description?.let { put(Events.DESCRIPTION, it) }
            put(Events.AVAILABILITY, if (e.busy) Events.AVAILABILITY_BUSY else Events.AVAILABILITY_FREE)
            put(Events.ACCESS_LEVEL, e.visibility.value)
            put(Events.HAS_ALARM, if (e.reminders.isNotEmpty()) 1 else 0)

            if (e.allDay) {
                val start = e.startDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                put(Events.ALL_DAY, 1)
                put(Events.EVENT_TIMEZONE, "UTC")
                put(Events.DTSTART, start)
                if (rule != null) {
                    val days = ChronoUnit.DAYS.between(e.startDay, e.endDay) + 1
                    put(Events.RRULE, rule)
                    put(Events.DURATION, "P${days}D")
                } else {
                    put(Events.DTEND, e.endDay.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                }
            } else {
                put(Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(Events.DTSTART, e.startMillis)
                if (rule != null) {
                    val seconds = maxOf(60L, (e.endMillis - e.startMillis) / 1000)
                    put(Events.RRULE, rule)
                    put(Events.DURATION, "P${seconds}S")
                } else {
                    put(Events.DTEND, e.endMillis)
                }
            }
            // Editing may switch between repeating and one-off, so clear whichever form is no longer used.
            if (rule == null) {
                putNull(Events.RRULE)
                putNull(Events.DURATION)
            } else {
                putNull(Events.DTEND)
            }
        }
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
                        .withValue(Reminders.METHOD, Reminders.METHOD_ALERT)
                        .build()
                }
                resolver.applyBatch(CalendarContract.AUTHORITY, ops)[0].uri?.lastPathSegment?.toLongOrNull()
            } else {
                val updated = resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, existing), values, null, null)
                // Nothing changed (read-only calendar, event gone): leave the existing reminders alone.
                if (updated <= 0) return@withContext null
                // Reminders are replaced wholesale, in one batch: simpler than diffing, and the provider allows it.
                val ops = ArrayList<ContentProviderOperation>()
                ops += ContentProviderOperation.newDelete(Reminders.CONTENT_URI)
                    .withSelection("${Reminders.EVENT_ID} = ?", arrayOf(existing.toString()))
                    .build()
                for (minutes in minutesList) {
                    ops += ContentProviderOperation.newInsert(Reminders.CONTENT_URI)
                        .withValue(Reminders.EVENT_ID, existing)
                        .withValue(Reminders.MINUTES, minutes)
                        .withValue(Reminders.METHOD, Reminders.METHOD_ALERT)
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
            null
        }
    }

    /**
     * Notifications that should fire in [fromMillis, toMillis), expanded from the event instances in that range.
     * Timed reminders are relative to the start. All-day reminders are relative to 9:00 on the day, like Google's default.
     */
    suspend fun upcomingAlerts(fromMillis: Long, toMillis: Long): List<UpcomingAlert> = withContext(Dispatchers.IO) {
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
        }
        if (remindersByEvent.isEmpty()) return@withContext emptyList()

        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, fromMillis)
            ContentUris.appendId(it, toMillis)
        }.build()
        val out = mutableListOf<UpcomingAlert>()
        try {
            resolver.query(
                uri,
                arrayOf(
                    Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.ALL_DAY, Instances.EVENT_LOCATION,
                    Instances.EVENT_COLOR, Instances.CALENDAR_COLOR,
                ),
                null,
                null,
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val eventId = c.getLong(0)
                    val minutesList = remindersByEvent[eventId] ?: continue
                    val begin = c.getLong(2)
                    val allDay = c.getInt(3) == 1
                    val anchor = if (allDay) {
                        Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
                            .atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
                    } else {
                        begin
                    }
                    val title = c.getString(1).orEmpty()
                    val location = c.getString(4)?.takeIf { it.isNotBlank() }
                    val color = if (c.getInt(5) != 0) c.getInt(5) else c.getInt(6)
                    for (minutes in minutesList.distinct()) {
                        val fireAt = anchor - minutes * 60_000L
                        if (fireAt >= fromMillis && fireAt < toMillis) {
                            out += UpcomingAlert(eventId, title, location, begin, allDay, minutes, fireAt, color)
                        }
                    }
                }
            }
        } catch (_: SecurityException) {
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
        )?.use { c -> while (c.moveToNext()) out += resolveMinutes(c.getInt(0)) }
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
            null
        }
    }

    /** Deletes the event (for recurring events this removes the whole series). */
    suspend fun deleteEvent(eventId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), null, null) > 0
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Exception) {
            Log.w(TAG, "deleteEvent failed", t)
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
            null
        }
    }

    private companion object {
        const val LOCAL_ACCOUNT = "Skydule"
        const val TAG = "CalendarRepository"
        const val DEFAULT_REMINDER_MINUTES = 10
    }
}
