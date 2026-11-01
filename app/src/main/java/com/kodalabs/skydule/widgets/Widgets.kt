package com.kodalabs.skydule.widgets

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.kodalabs.skydule.MainActivity
import com.kodalabs.skydule.R
import com.kodalabs.skydule.data.CalendarEvent
import com.kodalabs.skydule.data.CalendarRepository
import com.kodalabs.skydule.data.DateWindow
import com.kodalabs.skydule.data.SettingsStore
import com.kodalabs.skydule.data.WeekStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** Intent extra: open Skydule's Schedule on this day (a [LocalDate] epoch day). Set by taps on the widgets. */
const val EXTRA_OPEN_DAY = "com.kodalabs.skydule.OPEN_DAY"

/** Next up (2x2). */
class NextUpWidget : SkyWidget()

/** Today's agenda (4x2, can be stretched taller). */
class AgendaWidget : SkyWidget()

/** Week strip (4x2). */
class WeekWidget : SkyWidget()

/**
 * Shared plumbing for the three home-screen widgets. They have one fixed look (the dusk sky), no settings, and a tap
 * only ever opens Skydule on a day. All drawing is in [Widgets].
 */
abstract class SkyWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Widgets.refresh(context, goAsync())
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        Widgets.refresh(context, goAsync())
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Widgets.ACTION_REFRESH ->
                Widgets.refresh(context, goAsync())
        }
    }
}

object Widgets {
    const val ACTION_REFRESH = "com.kodalabs.skydule.action.WIDGET_REFRESH"

    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    /** Redraws every placed widget. Cheap when none are placed. Safe to call from anywhere. */
    fun refresh(context: Context, pending: BroadcastReceiver.PendingResult? = null) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                render(app)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("Widgets", "Widget refresh failed", e)
                com.kodalabs.skydule.data.Diag.record("Widgets", e)
            } finally {
                pending?.finish()
            }
        }
    }

    private suspend fun render(app: Context) {
        val mgr = AppWidgetManager.getInstance(app)
        fun ids(cls: Class<*>) = mgr.getAppWidgetIds(ComponentName(app, cls))
        val nextIds = ids(NextUpWidget::class.java)
        val agendaIds = ids(AgendaWidget::class.java)
        val weekIds = ids(WeekWidget::class.java)
        if (nextIds.isEmpty() && agendaIds.isEmpty() && weekIds.isEmpty()) return

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val now = System.currentTimeMillis()
        val settings = SettingsStore(app)
        val firstDow = when (settings.weekStart.first()) {
            WeekStart.SYSTEM -> WeekFields.of(Locale.getDefault()).firstDayOfWeek
            WeekStart.MONDAY -> DayOfWeek.MONDAY
            WeekStart.SUNDAY -> DayOfWeek.SUNDAY
        }
        val weekStart = today.with(TemporalAdjusters.previousOrSame(firstDow))

        val hasAccess = ContextCompat.checkSelfPermission(app, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val events: List<CalendarEvent> = if (!hasAccess) emptyList() else runCatching {
            CalendarRepository(app.contentResolver).events(
                DateWindow(minOf(today, weekStart), maxOf(weekStart.plusDays(7), today.plusDays(15))),
                settings.calendarPrefs.first(),
            )
        }.getOrDefault(emptyList())
        val data = Data(app, today, now, weekStart, events, hasAccess)

        // Phones differ a lot (on a tall home-screen grid a 2x2 is a narrow tall tile), so each widget lays itself out
        // for the height it is actually given.
        fun heightOf(id: Int) = mgr.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110)
        nextIds.forEach { mgr.updateAppWidget(it, nextUp(data, heightOf(it))) }
        agendaIds.forEach { mgr.updateAppWidget(it, agenda(data, slots = ((heightOf(it) - 52) / 36).coerceIn(1, 6))) }
        weekIds.forEach { mgr.updateAppWidget(it, week(data, heightOf(it))) }
        scheduleNext(app, data)
    }

    private class Data(
        val app: Context,
        val today: LocalDate,
        val now: Long,
        val weekStart: LocalDate,
        val events: List<CalendarEvent>,
        val hasAccess: Boolean,
    ) {
        fun on(day: LocalDate): List<CalendarEvent> =
            events.filter { it.startDay <= day && day <= it.endDay }
                .sortedWith(compareByDescending<CalendarEvent> { it.allDay }.thenBy { it.begin })
    }

    // ---- Next up -------------------------------------------------------------------------------

    private fun nextUp(d: Data, heightDp: Int): RemoteViews {
        val v = RemoteViews(d.app.packageName, R.layout.widget_next)
        v.setOnClickPendingIntent(R.id.root, openDay(d.app, d.today))
        v.setViewVisibility(R.id.sub, View.GONE)
        v.setViewVisibility(R.id.then, View.GONE)
        // A tall tile gets bigger type, room for a long title, and a "then" line for what follows.
        val tall = heightDp >= 190
        // A short tile (a 2x1 on a phone with short grid cells) drops the label and keeps one line of title.
        val short = heightDp < 100
        v.setViewVisibility(R.id.label, if (short) View.GONE else View.VISIBLE)
        v.setTextViewTextSize(R.id.title, TypedValue.COMPLEX_UNIT_SP, if (tall) 26f else if (short) 18f else 21f)
        v.setInt(R.id.title, "setMaxLines", if (tall) 4 else if (short) 1 else 2)
        if (!d.hasAccess) {
            v.setTextViewText(R.id.label, "SKYDULE")
            v.setTextViewText(R.id.title, "Open Skydule")
            v.setTextViewText(R.id.time, "to get started")
            return v
        }
        val todays = d.on(d.today)
        val current = todays.firstOrNull { !it.allDay && it.begin <= d.now && d.now < it.end }
        val upcoming = todays.firstOrNull { !it.allDay && it.begin > d.now }
        val allDay = todays.firstOrNull { it.allDay }
        val afterThis = todays.filter { !it.allDay && it.begin > d.now && it !== (current ?: upcoming) }.firstOrNull()
        if (tall && afterThis != null && (current != null || upcoming != null)) {
            v.setTextViewText(R.id.then, "Then ${clock(afterThis.begin)}  ${afterThis.title.ifBlank { "(No title)" }}")
            v.setViewVisibility(R.id.then, View.VISIBLE)
        }
        when {
            current != null -> {
                v.setTextViewText(R.id.label, "NOW")
                v.setTextViewText(R.id.title, current.title.ifBlank { "(No title)" })
                v.setTextViewText(R.id.time, "Until ${clock(current.end)}")
                current.location?.takeIf { it.isNotBlank() && !short }?.let {
                    v.setTextViewText(R.id.sub, it)
                    v.setViewVisibility(R.id.sub, View.VISIBLE)
                }
            }
            upcoming != null -> {
                v.setTextViewText(R.id.label, "NEXT UP")
                v.setTextViewText(R.id.title, upcoming.title.ifBlank { "(No title)" })
                v.setTextViewText(R.id.time, "${clock(upcoming.begin)} – ${clock(upcoming.end)}")
                upcoming.location?.takeIf { it.isNotBlank() && !short }?.let {
                    v.setTextViewText(R.id.sub, it)
                    v.setViewVisibility(R.id.sub, View.VISIBLE)
                }
            }
            allDay != null -> {
                v.setTextViewText(R.id.label, "TODAY")
                v.setTextViewText(R.id.title, allDay.title.ifBlank { "(No title)" })
                v.setTextViewText(R.id.time, "All day")
            }
            else -> {
                v.setTextViewText(R.id.label, "TODAY")
                v.setTextViewText(R.id.title, "Clear day")
                v.setTextViewText(R.id.time, if (todays.isEmpty()) "Nothing planned" else "Nothing else today")
            }
        }
        return v
    }

    // ---- Today's agenda ------------------------------------------------------------------------

    private fun agenda(d: Data, slots: Int): RemoteViews {
        val v = RemoteViews(d.app.packageName, R.layout.widget_agenda)
        v.setOnClickPendingIntent(R.id.root, openDay(d.app, d.today))
        v.setTextViewText(R.id.day, d.today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()))
        v.setTextViewText(R.id.date, d.today.format(DateTimeFormatter.ofPattern("d MMM")))
        val rows = intArrayOf(R.id.row1, R.id.row2, R.id.row3, R.id.row4, R.id.row5, R.id.row6)
        val bars = intArrayOf(R.id.bar1, R.id.bar2, R.id.bar3, R.id.bar4, R.id.bar5, R.id.bar6)
        val times = intArrayOf(R.id.time1, R.id.time2, R.id.time3, R.id.time4, R.id.time5, R.id.time6)
        val titles = intArrayOf(R.id.title1, R.id.title2, R.id.title3, R.id.title4, R.id.title5, R.id.title6)
        rows.forEach { v.setViewVisibility(it, View.GONE) }
        v.setViewVisibility(R.id.empty, View.GONE)
        v.setViewVisibility(R.id.more, View.GONE)

        if (!d.hasAccess) {
            v.setViewVisibility(R.id.empty, View.VISIBLE)
            v.setTextViewText(R.id.emptyTitle, "Open Skydule")
            v.setTextViewText(R.id.emptySub, "to get started")
            return v
        }
        // Events already over drop off the list as the day goes on.
        val todays = d.on(d.today).filter { it.allDay || it.end > d.now }
        if (todays.isEmpty()) {
            v.setViewVisibility(R.id.empty, View.VISIBLE)
            v.setTextViewText(R.id.emptyTitle, "Clear day")
            v.setTextViewText(R.id.emptySub, "Nothing planned")
            return v
        }
        todays.take(slots).forEachIndexed { i, e ->
            v.setViewVisibility(rows[i], View.VISIBLE)
            v.setInt(bars[i], "setColorFilter", e.color or (0xFF shl 24))
            v.setTextViewText(times[i], if (e.allDay) "All day" else clock(e.begin))
            v.setTextViewText(titles[i], e.title.ifBlank { "(No title)" })
        }
        if (todays.size > slots) {
            v.setViewVisibility(R.id.more, View.VISIBLE)
            v.setTextViewText(R.id.more, "+${todays.size - slots} more")
        }
        return v
    }

    // ---- Week strip ----------------------------------------------------------------------------

    private fun week(d: Data, heightDp: Int): RemoteViews {
        val v = RemoteViews(d.app.packageName, R.layout.widget_week)
        val end = d.weekStart.plusDays(6)
        val fmt = DateTimeFormatter.ofPattern("d MMM")
        v.setTextViewText(
            R.id.range,
            (if (d.weekStart.month == end.month) "${d.weekStart.dayOfMonth} – ${end.format(fmt)}" else "${d.weekStart.format(fmt)} – ${end.format(fmt)}")
                .uppercase(Locale.getDefault()),
        )
        val cells = intArrayOf(R.id.cell0, R.id.cell1, R.id.cell2, R.id.cell3, R.id.cell4, R.id.cell5, R.id.cell6)
        val dows = intArrayOf(R.id.dow0, R.id.dow1, R.id.dow2, R.id.dow3, R.id.dow4, R.id.dow5, R.id.dow6)
        val nums = intArrayOf(R.id.num0, R.id.num1, R.id.num2, R.id.num3, R.id.num4, R.id.num5, R.id.num6)
        val dots = intArrayOf(R.id.dot0, R.id.dot1, R.id.dot2, R.id.dot3, R.id.dot4, R.id.dot5, R.id.dot6)
        val ink = 0xFF3B3A6E.toInt()
        val tall = heightDp >= 190
        for (i in 0..6) {
            v.setTextViewTextSize(dows[i], TypedValue.COMPLEX_UNIT_SP, if (tall) 11f else 10f)
            v.setTextViewTextSize(nums[i], TypedValue.COMPLEX_UNIT_SP, if (tall) 19f else 16f)
            val day = d.weekStart.plusDays(i.toLong())
            val isToday = day == d.today
            v.setTextViewText(dows[i], day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()))
            v.setTextViewText(nums[i], day.dayOfMonth.toString())
            v.setInt(cells[i], "setBackgroundResource", if (isToday) R.drawable.widget_today else 0)
            v.setTextColor(dows[i], if (isToday) ink else 0xFFFFFFFF.toInt())
            v.setTextColor(nums[i], if (isToday) ink else 0xFFFFFFFF.toInt())
            v.setInt(dots[i], "setColorFilter", if (isToday) ink else 0xFFFFFFFF.toInt())
            v.setViewVisibility(dots[i], if (d.hasAccess && d.on(day).isNotEmpty()) View.VISIBLE else View.INVISIBLE)
            v.setOnClickPendingIntent(cells[i], openDay(d.app, day))
        }
        v.setOnClickPendingIntent(R.id.range, openDay(d.app, d.today))
        v.setOnClickPendingIntent(R.id.next, openDay(d.app, d.today))
        v.setOnClickPendingIntent(R.id.root, openDay(d.app, d.today))

        val lines = intArrayOf(R.id.next, R.id.next2, R.id.next3, R.id.next4, R.id.next5)
        lines.forEach { v.setViewVisibility(it, View.GONE) }
        v.setViewVisibility(R.id.next, View.VISIBLE)
        val count = ((heightDp - 100) / 30).coerceIn(1, 5)
        val list = if (!d.hasAccess) emptyList() else d.events
            .filter { if (it.allDay) it.endDay >= d.today else it.end > d.now }
            .sortedWith(compareBy({ maxOf(it.startDay, d.today) }, { !it.allDay }, { it.begin }))
            .take(count)
        if (!d.hasAccess) {
            v.setTextViewText(R.id.next, "Open Skydule to get started")
        } else if (list.isEmpty()) {
            v.setTextViewText(R.id.next, "Clear week · nothing planned")
        } else {
            list.forEachIndexed { i, e ->
                v.setViewVisibility(lines[i], View.VISIBLE)
                v.setTextViewText(lines[i], (if (i == 0) "Next · " else "") + whenLabel(d, e) + " " + e.title.ifBlank { "(No title)" })
                v.setOnClickPendingIntent(lines[i], openDay(d.app, maxOf(e.startDay, d.today)))
            }
        }
        return v
    }

    // ---- Helpers -------------------------------------------------------------------------------

    /** "Now", "13:30", "Tomorrow 09:00", "Tue 3 Nov" ... for an upcoming event. */
    private fun whenLabel(d: Data, e: CalendarEvent): String {
        val day = maxOf(e.startDay, d.today)
        val time = if (e.allDay) "" else " " + clock(e.begin)
        return when {
            day == d.today && e.allDay -> "Today"
            day == d.today && e.begin <= d.now -> "Now"
            day == d.today -> clock(e.begin)
            day == d.today.plusDays(1) -> "Tomorrow$time"
            else -> day.format(DateTimeFormatter.ofPattern("EEE d MMM")) + time
        }
    }

    private fun clock(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(timeFormat)

    /** Opens Skydule's Schedule on [day]. Each day gets its own PendingIntent so they don't overwrite each other. */
    private fun openDay(app: Context, day: LocalDate): PendingIntent {
        // The launcher alias (not MainActivity directly) so Android reuses the instance that is already running
        // instead of stacking a second one on top of it.
        val intent = (app.packageManager.getLaunchIntentForPackage(app.packageName) ?: Intent(app, MainActivity::class.java))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .setData(Uri.parse("skydule://widget/day/${day.toEpochDay()}"))
            .putExtra(EXTRA_OPEN_DAY, day.toEpochDay())
        return PendingIntent.getActivity(app, day.toEpochDay().toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Wakes the widgets when what they show next changes: an event starting or ending, or midnight. Inexact on purpose. */
    private fun scheduleNext(app: Context, d: Data) {
        val zone = ZoneId.systemDefault()
        val midnight = d.today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val soonest = d.events.asSequence()
            .filter { !it.allDay }
            .flatMap { sequenceOf(it.begin, it.end) }
            .filter { it > d.now }
            .minOrNull()
        val at = minOf(midnight, soonest ?: Long.MAX_VALUE, d.now + 6 * 60 * 60 * 1000L)
        val intent = Intent(app, NextUpWidget::class.java).setAction(ACTION_REFRESH)
        val pi = PendingIntent.getBroadcast(app, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        app.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC, at + 1_000L, pi)
    }
}
