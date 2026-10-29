package com.example.gradule.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.gradule.MainActivity
import com.example.gradule.R
import com.example.gradule.data.CalendarRepository
import com.example.gradule.data.SettingsStore
import com.example.gradule.data.UpcomingAlert
import com.example.gradule.data.reminderLabel
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.Instant
import java.time.ZoneId

const val EXTRA_EVENT_ID = "com.example.gradule.EVENT_ID"
private const val ACTION_FIRE = "com.example.gradule.action.FIRE_REMINDER"
private const val ACTION_SNOOZE = "com.example.gradule.action.SNOOZE_REMINDER"
private const val ACTION_DISMISS = "com.example.gradule.action.DISMISS_REMINDER"
private const val EXTRA_TITLE = "title"
private const val EXTRA_LOCATION = "location"
private const val EXTRA_BEGIN = "begin"
private const val EXTRA_ALL_DAY = "all_day"
private const val EXTRA_MINUTES = "minutes"
private const val EXTRA_COLOR = "color"
private const val CHANNEL_ID = "event_reminders"
private const val PREFS = "gradule_reminders"
private const val KEY_SCHEDULED = "scheduled"
private const val KEY_SNOOZES = "snoozes"
private const val KEY_LAST_RUN = "last_run"
/** After a reboot, only reminders from the last 6 hours are worth showing late. */
private const val CATCH_UP_MS = 6L * 3600_000
/** How far ahead alarms are planned. Each fired alarm (and every calendar change) plans the next stretch. */
private const val HORIZON_MS = 14L * 24 * 3600_000
private const val MAX_ALARMS = 200

/**
 * Plans one AlarmManager alarm per upcoming notification. Google Calendar's own app is not needed: the
 * alarms, the notifications and the rescheduling all live in this app, reading the same provider data.
 */
object ReminderScheduler {

    /**
     * @param catchUp after a reboot: also show reminders that came due while the phone was off (and whose event
     * hasn't started yet). Not done on every run, so normal alarms are never shown twice.
     */
    suspend fun reschedule(context: Context, catchUp: Boolean = false) {
        val app = context.applicationContext
        val alarms = app.getSystemService(AlarmManager::class.java)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val nowMs = System.currentTimeMillis()
        val lastRun = prefs.getLong(KEY_LAST_RUN, nowMs)
        prefs.edit().putLong(KEY_LAST_RUN, nowMs).apply()

        // Snoozes are plain alarms, which a reboot wipes; put the saved ones back.
        restoreSnoozes(app)

        // Drop whatever was planned last time, so edits and deletes take effect.
        // Snoozed reminders use their own keys and are left alone here.
        prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty().forEach { key ->
            pendingFor(app, key, PendingIntent.FLAG_NO_CREATE, null)?.let {
                alarms.cancel(it)
                it.cancel()
            }
        }
        prefs.edit().remove(KEY_SCHEDULED).apply()

        val canRead = ContextCompat.checkSelfPermission(app, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        if (!canRead) return

        val now = System.currentTimeMillis()
        val repo = CalendarRepository(app.contentResolver)
        if (catchUp) {
            val since = maxOf(lastRun, now - CATCH_UP_MS)
            for (a in repo.upcomingAlerts(since, now)) {
                // Skip anything whose event is already under way; a late "starts in 10 min" would just confuse.
                if (a.begin < now && !a.allDay) continue
                showReminder(app, alertIntent(app, a), SettingsStore(app).snoozeLength.first().minutes)
            }
        }
        val alerts = repo.upcomingAlerts(now, now + HORIZON_MS)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

        val keys = HashSet<String>()
        for (a in alerts.take(MAX_ALARMS)) {
            val key = "${a.eventId}:${a.begin}:${a.minutesBefore}"
            keys += key
            val pi = pendingFor(app, key, PendingIntent.FLAG_UPDATE_CURRENT, a) ?: continue
            if (exact) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, a.fireAt, pi)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, a.fireAt, pi)
            }
        }
        prefs.edit().putStringSet(KEY_SCHEDULED, keys).apply()
    }

    private fun pendingFor(context: Context, key: String, flags: Int, a: UpcomingAlert?): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_FIRE)
            .setData(Uri.parse("gradule://reminder/$key"))
        if (a != null) putAlertExtras(intent, a)
        return PendingIntent.getBroadcast(context, key.hashCode(), intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }
}

private fun putAlertExtras(intent: Intent, a: UpcomingAlert): Intent =
    intent.putExtra(EXTRA_EVENT_ID, a.eventId)
        .putExtra(EXTRA_TITLE, a.title)
        .putExtra(EXTRA_LOCATION, a.location)
        .putExtra(EXTRA_BEGIN, a.begin)
        .putExtra(EXTRA_ALL_DAY, a.allDay)
        .putExtra(EXTRA_MINUTES, a.minutesBefore)
        .putExtra(EXTRA_COLOR, a.color)

private fun alertIntent(context: Context, a: UpcomingAlert): Intent =
    putAlertExtras(Intent(context, ReminderReceiver::class.java), a)

// --- Saved snoozes -------------------------------------------------------------------------------------------

private fun snoozePrefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

private fun readSnoozes(context: Context): JSONObject =
    runCatching { JSONObject(snoozePrefs(context).getString(KEY_SNOOZES, null) ?: "{}") }.getOrDefault(JSONObject())

private fun writeSnoozes(context: Context, all: JSONObject) {
    snoozePrefs(context).edit().putString(KEY_SNOOZES, all.toString()).apply()
}

private fun saveSnooze(context: Context, key: String, at: Long, source: Intent) {
    val all = readSnoozes(context)
    all.put(
        key,
        JSONObject()
            .put("at", at)
            .put("eventId", source.getLongExtra(EXTRA_EVENT_ID, -1L))
            .put("title", source.getStringExtra(EXTRA_TITLE).orEmpty())
            .put("location", source.getStringExtra(EXTRA_LOCATION))
            .put("begin", source.getLongExtra(EXTRA_BEGIN, 0L))
            .put("allDay", source.getBooleanExtra(EXTRA_ALL_DAY, false))
            .put("minutes", source.getIntExtra(EXTRA_MINUTES, 0))
            .put("color", source.getIntExtra(EXTRA_COLOR, 0)),
    )
    writeSnoozes(context, all)
}

private fun forgetSnooze(context: Context, key: String) {
    val all = readSnoozes(context)
    if (all.has(key)) {
        all.remove(key)
        writeSnoozes(context, all)
    }
}

/** Re-arms every saved snooze. Safe to repeat: the same key maps to the same alarm. Expired ones fire right away. */
private fun restoreSnoozes(context: Context) {
    val all = readSnoozes(context)
    val now = System.currentTimeMillis()
    val keys = all.keys().asSequence().toList()
    for (key in keys) {
        val o = all.optJSONObject(key) ?: continue
        // A snooze for an event that has long since started has nothing left to say.
        val begin = o.optLong("begin")
        if (!o.optBoolean("allDay") && begin < now - 60 * 60_000L) {
            forgetSnooze(context, key)
            continue
        }
        val source = Intent().apply {
            putExtra(EXTRA_EVENT_ID, o.optLong("eventId", -1L))
            putExtra(EXTRA_TITLE, o.optString("title"))
            putExtra(EXTRA_LOCATION, o.optString("location").takeIf { it.isNotBlank() && it != "null" })
            putExtra(EXTRA_BEGIN, begin)
            putExtra(EXTRA_ALL_DAY, o.optBoolean("allDay"))
            putExtra(EXTRA_MINUTES, o.optInt("minutes"))
            putExtra(EXTRA_COLOR, o.optInt("color"))
        }
        snoozePending(context, key, PendingIntent.FLAG_UPDATE_CURRENT, source)
            ?.let { setAlarm(context, maxOf(o.optLong("at"), now + 1_000L), it) }
    }
}

/** Identifies one notification: the same event occurrence and lead time always maps to the same key. */
private fun notificationKey(intent: Intent): String =
    "${intent.getLongExtra(EXTRA_EVENT_ID, -1L)}:${intent.getLongExtra(EXTRA_BEGIN, 0L)}:${intent.getIntExtra(EXTRA_MINUTES, 0)}"

/** The alarm that re-shows a snoozed notification. Cancelling it uses the same key, with no extras needed. */
private fun snoozePending(context: Context, key: String, flags: Int, source: Intent?): PendingIntent? {
    val intent = Intent(context, ReminderReceiver::class.java)
        .setAction(ACTION_FIRE)
        .setData(Uri.parse("gradule://snooze/$key"))
    source?.let { intent.putExtras(it) }
    return PendingIntent.getBroadcast(context, "snooze:$key".hashCode(), intent, flags or PendingIntent.FLAG_IMMUTABLE)
}

/** Button on the notification itself. Carries the reminder's extras so the receiver can act on it. */
private fun actionPending(context: Context, action: String, key: String, source: Intent): PendingIntent {
    val intent = Intent(context, ReminderReceiver::class.java)
        .setAction(action)
        .setData(Uri.parse("gradule://action/$key/$action"))
        .putExtras(source)
    return PendingIntent.getBroadcast(
        context,
        "$action:$key".hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private fun setAlarm(context: Context, at: Long, pi: PendingIntent) {
    val alarms = context.getSystemService(AlarmManager::class.java)
    val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
    if (exact) {
        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    } else {
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }
}

/**
 * Receives fired alarms, shows the notification, and handles its Snooze and Dismiss buttons. After each
 * action it plans the next stretch, and it also runs after boot and time changes.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_FIRE -> {
                        // A snooze that has fired is spent; forget it so a reboot doesn't bring it back.
                        if (intent.data?.host == "snooze") forgetSnooze(context.applicationContext, notificationKey(intent))
                        val snoozeMinutes = SettingsStore(context.applicationContext).snoozeLength.first().minutes
                        showReminder(context, intent, snoozeMinutes)
                    }
                    ACTION_SNOOZE -> {
                        val snoozeMinutes = SettingsStore(context.applicationContext).snoozeLength.first().minutes
                        snooze(context, intent, snoozeMinutes)
                    }
                    ACTION_DISMISS -> dismiss(context, intent)
                }
                ReminderScheduler.reschedule(context, catchUp = intent.action == Intent.ACTION_BOOT_COMPLETED)
            } finally {
                pending.finish()
            }
        }
    }

    private fun snooze(context: Context, intent: Intent, minutes: Int) {
        val key = notificationKey(intent)
        NotificationManagerCompat.from(context).cancel(key.hashCode())
        val at = System.currentTimeMillis() + minutes * 60_000L
        saveSnooze(context.applicationContext, key, at, intent)
        snoozePending(context, key, PendingIntent.FLAG_UPDATE_CURRENT, intent)?.let { setAlarm(context, at, it) }
    }

    private fun dismiss(context: Context, intent: Intent) {
        val key = notificationKey(intent)
        NotificationManagerCompat.from(context).cancel(key.hashCode())
        forgetSnooze(context.applicationContext, key)
        // Cancel any snoozed re-alert for this reminder, so a dismissed reminder never comes back.
        snoozePending(context, key, PendingIntent.FLAG_NO_CREATE, null)?.let {
            context.getSystemService(AlarmManager::class.java).cancel(it)
            it.cancel()
        }
    }
}

private fun showReminder(context: Context, intent: Intent, snoozeMinutes: Int) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
    val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "(No title)" }
    val location = intent.getStringExtra(EXTRA_LOCATION)
    val begin = intent.getLongExtra(EXTRA_BEGIN, 0L)
    val allDay = intent.getBooleanExtra(EXTRA_ALL_DAY, false)
    val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
    val color = intent.getIntExtra(EXTRA_COLOR, 0)

    val nm = context.getSystemService(NotificationManager::class.java)
    if (nm.getNotificationChannel(CHANNEL_ID) == null) {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Event reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shown before an event starts"
            },
        )
    }

    val time = if (allDay) "All day" else Instant.ofEpochMilli(begin).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    val headline = if (minutes == 0) "Starting now" else reminderLabel(minutes).removeSuffix(" before").let { "In $it" }
    val text = listOfNotNull(headline, time, location).joinToString(" · ")

    val open = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(EXTRA_EVENT_ID, eventId)
    val contentIntent = PendingIntent.getActivity(
        context,
        "$eventId:$begin".hashCode(),
        open,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val key = notificationKey(intent)
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_event)
        // Tint the small icon and accent with the calendar's colour (system default if unknown).
        .apply { if (color != 0) setColor(color or 0xFF000000.toInt()) }
        .setContentTitle(title)
        .setContentText(text)
        .setContentIntent(contentIntent)
        .setAutoCancel(true)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .addAction(0, "Snooze $snoozeMinutes min", actionPending(context, ACTION_SNOOZE, key, intent))
        .addAction(0, "Dismiss", actionPending(context, ACTION_DISMISS, key, intent))
        .build()
    try {
        NotificationManagerCompat.from(context).notify(key.hashCode(), notification)
    } catch (_: SecurityException) {
    }
}
