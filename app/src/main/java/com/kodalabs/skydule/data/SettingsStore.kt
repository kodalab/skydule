package com.kodalabs.skydule.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

enum class ThemeMode(val label: String) {
    SKY("Sky (follows the time of day)"),
    DYNAMIC("Material You (Android 12+)"),
    AMOLED("AMOLED black"),
}

enum class TemperatureUnit(val label: String) {
    AUTO("Automatic (by region)"),
    CELSIUS("Celsius (°C)"),
    FAHRENHEIT("Fahrenheit (°F)"),
}

enum class WeekStart(val label: String) {
    SYSTEM("Automatic (by region)"),
    MONDAY("Monday"),
    SUNDAY("Sunday"),
}

/** Notification a new event gets automatically. [minutes] is null for none. */
enum class DefaultReminder(val label: String, val minutes: Int?) {
    NONE("No alert", null),
    AT_TIME("At time of event", 0),
    TEN("10 minutes before", 10),
    THIRTY("30 minutes before", 30),
    HOUR("1 hour before", 60),
    DAY("1 day before", 1440),
}

/** How long a reminder is put off when you tap Snooze on its notification. */
/** Which side the event panel sits on when the screen is wide enough for two panes. */
enum class DetailSide(val label: String) {
    RIGHT("On the right"),
    LEFT("On the left"),
}

enum class SnoozeLength(val label: String, val minutes: Int) {
    FIVE("5 minutes", 5),
    TEN("10 minutes", 10),
    FIFTEEN("15 minutes", 15),
    THIRTY("30 minutes", 30),
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val tempKey = stringPreferencesKey("temperature_unit")
    private val weekStartKey = stringPreferencesKey("week_start")
    private val reminderKey = stringPreferencesKey("default_reminder")
    private val showWeatherKey = booleanPreferencesKey("show_weather")
    private val detailSideKey = stringPreferencesKey("detail_side")
    private val snoozeKey = stringPreferencesKey("snooze_length")
    private val hiddenCalendarsKey = stringSetPreferencesKey("hidden_calendars")
    private val calendarColorsKey = stringPreferencesKey("calendar_colors")
    private val hiddenKeysKey = stringSetPreferencesKey("hidden_calendar_keys")
    private val colorKeysKey = stringPreferencesKey("calendar_color_keys")
    private val recentColorsKey = stringPreferencesKey("recent_colors")

    private inline fun <reified T : Enum<T>> Flow<androidx.datastore.preferences.core.Preferences>.enumFlow(
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        default: T,
    ): Flow<T> = map { prefs -> prefs[key]?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.enumFlow(themeKey, ThemeMode.SKY)
    val temperatureUnit: Flow<TemperatureUnit> = context.dataStore.data.enumFlow(tempKey, TemperatureUnit.AUTO)
    val weekStart: Flow<WeekStart> = context.dataStore.data.enumFlow(weekStartKey, WeekStart.SYSTEM)
    val defaultReminder: Flow<DefaultReminder> = context.dataStore.data.enumFlow(reminderKey, DefaultReminder.NONE)
    val showWeather: Flow<Boolean> = context.dataStore.data.map { it[showWeatherKey] ?: true }
    /** 0 = plain sliding pill, 1 = full stretch. */
    val detailSide: Flow<DetailSide> = context.dataStore.data.enumFlow(detailSideKey, DetailSide.RIGHT)
    /** Debug builds only: lets a phone in landscape show the two-pane layout for testing. */
    val snoozeLength: Flow<SnoozeLength> = context.dataStore.data.enumFlow(snoozeKey, SnoozeLength.TEN)

    /**
     * Calendars hidden in Skydule and the colours chosen for them. They are stored by a stable calendar key (account +
     * the calendar's sync id or name, see [calendarKey]), not by the phone's calendar id, so a backup restored onto
     * another phone lands on the right calendars. The keys are matched to this phone's ids here, and again whenever the
     * calendars change.
     */
    val calendarPrefs: Flow<CalendarPrefs> =
        combine(context.dataStore.data, calendarKeysFlow()) { prefs, idToKey ->
            resolveCalendarPrefs(
                prefs[hiddenKeysKey].orEmpty(),
                parseKeyedColors(prefs[colorKeysKey]),
                idToKey,
            )
        }.distinctUntilChanged().onStart { migrateLegacyCalendarPrefs() }

    private fun calendarKeysFlow(): Flow<Map<Long, String>> =
        kotlinx.coroutines.flow.flow {
            // No calendar access (first launch): nothing to match, and Android refuses to watch the calendar anyway.
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.READ_CALENDAR,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) emit(emptyMap()) else emitAll(
                CalendarRepository(context.contentResolver).changes()
                    .debounce(250)
                    .map { queryCalendarKeys(context.contentResolver) },
            )
        }.distinctUntilChanged()

    /** Moves hidden/colour choices saved by calendar id (builds before 47) over to stable keys. Safe to repeat. */
    private suspend fun migrateLegacyCalendarPrefs() {
        val snapshot = context.dataStore.data.first()
        if (snapshot[hiddenCalendarsKey] == null && snapshot[calendarColorsKey] == null) return
        // Ids saved by an older build only mean something on the phone that saved them. A fresh install (first install
        // time = last update time) that already has such values got them from a backup made on another phone, where
        // the ids point at different calendars: drop them instead of applying them to the wrong ones.
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        if (info != null && !legacyIdsAreTrusted(info.firstInstallTime, info.lastUpdateTime)) {
            context.dataStore.edit { it.remove(hiddenCalendarsKey); it.remove(calendarColorsKey) }
            return
        }
        val idToKey = queryCalendarKeys(context.contentResolver)
        if (idToKey.isEmpty()) return // no access (yet): keep the old values for next time
        context.dataStore.edit { prefs ->
            val hidden = prefs[hiddenKeysKey].orEmpty().toMutableSet()
            prefs[hiddenCalendarsKey].orEmpty().mapNotNull { it.toLongOrNull() }.forEach { id -> idToKey[id]?.let { hidden += it } }
            val colors = parseKeyedColors(prefs[colorKeysKey]).toMutableMap()
            parseCalendarColors(prefs[calendarColorsKey]).forEach { (id, c) -> idToKey[id]?.let { colors[it] = c } }
            prefs[hiddenKeysKey] = hidden
            prefs[colorKeysKey] = formatKeyedColors(colors)
            prefs.remove(hiddenCalendarsKey)
            prefs.remove(calendarColorsKey)
        }
    }

    /** Colours the user picked with the custom picker, newest first. */
    val recentColors: Flow<List<Int>> = context.dataStore.data.map { prefs ->
        prefs[recentColorsKey].orEmpty().split(',').mapNotNull { it.toIntOrNull() }
    }

    suspend fun setCalendarHidden(id: Long, hidden: Boolean) {
        val key = queryCalendarKeys(context.contentResolver)[id] ?: return
        context.dataStore.edit { prefs ->
            val set = prefs[hiddenKeysKey].orEmpty().toMutableSet()
            if (hidden) set += key else set -= key
            prefs[hiddenKeysKey] = set
        }
    }

    /** [color] null puts the calendar back to its own colour. */
    suspend fun setCalendarColor(id: Long, color: Int?) {
        val key = queryCalendarKeys(context.contentResolver)[id] ?: return
        context.dataStore.edit { prefs ->
            val map = parseKeyedColors(prefs[colorKeysKey]).toMutableMap()
            if (color == null) map.remove(key) else map[key] = color
            prefs[colorKeysKey] = formatKeyedColors(map)
        }
    }

    suspend fun addRecentColor(color: Int) = context.dataStore.edit { prefs ->
        val list = (listOf(color) + prefs[recentColorsKey].orEmpty().split(',').mapNotNull { it.toIntOrNull() }).distinct().take(5)
        prefs[recentColorsKey] = list.joinToString(",")
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[themeKey] = mode.name }
    suspend fun setTemperatureUnit(unit: TemperatureUnit) = context.dataStore.edit { it[tempKey] = unit.name }
    suspend fun setWeekStart(start: WeekStart) = context.dataStore.edit { it[weekStartKey] = start.name }
    suspend fun setDefaultReminder(r: DefaultReminder) = context.dataStore.edit { it[reminderKey] = r.name }
    suspend fun setShowWeather(show: Boolean) = context.dataStore.edit { it[showWeatherKey] = show }
    suspend fun setDetailSide(side: DetailSide) = context.dataStore.edit { it[detailSideKey] = side.name }
    suspend fun setSnoozeLength(length: SnoozeLength) = context.dataStore.edit { it[snoozeKey] = length.name }
}

/** "id:argb,id:argb" as stored in settings. Bad pieces are skipped. */
internal fun parseCalendarColors(raw: String?): Map<Long, Int> =
    raw.orEmpty().split(',').mapNotNull { part ->
        val bits = part.split(':')
        val id = bits.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        val color = bits.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
        id to color
    }.toMap()

internal fun formatCalendarColors(map: Map<Long, Int>): String = map.entries.joinToString(",") { "${it.key}:${it.value}" }

// --- Calendar keys: choices that survive a restore onto another phone ---------------------------------------------

/**
 * A calendar's identity that does not change between phones: account type and name, plus the calendar's sync id
 * (Google calendars have one) or, for calendars without one (on-device calendars), its name.
 */
internal fun calendarKey(accountType: String?, accountName: String?, syncId: String?, displayName: String?): String =
    listOf(
        accountType.orEmpty(),
        accountName.orEmpty(),
        syncId?.takeIf { it.isNotBlank() } ?: "name:" + displayName.orEmpty(),
    ).joinToString("|")

/** This phone's calendar ids with their stable keys. Empty without calendar access. */
internal suspend fun queryCalendarKeys(resolver: android.content.ContentResolver): Map<Long, String> =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val out = HashMap<Long, String>()
        try {
            resolver.query(
                android.provider.CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    android.provider.CalendarContract.Calendars._ID,
                    android.provider.CalendarContract.Calendars.ACCOUNT_TYPE,
                    android.provider.CalendarContract.Calendars.ACCOUNT_NAME,
                    android.provider.CalendarContract.Calendars._SYNC_ID,
                    android.provider.CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) out[c.getLong(0)] = calendarKey(c.getString(1), c.getString(2), c.getString(3), c.getString(4))
            }
        } catch (_: SecurityException) {
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Diag.record("queryCalendarKeys", e)
        }
        out
    }

/** Turns stored keys into this phone's calendar ids. Keys with no matching calendar here are simply not applied. */
internal fun resolveCalendarPrefs(hiddenKeys: Set<String>, colorKeys: Map<String, Int>, idToKey: Map<Long, String>): CalendarPrefs =
    CalendarPrefs(
        hidden = idToKey.filterValues { it in hiddenKeys }.keys,
        colors = idToKey.mapNotNull { (id, key) -> colorKeys[key]?.let { id to it } }.toMap(),
    )

/** Colours by calendar key, stored as "key=argb&key=argb" with the keys URL-encoded. Bad pieces are skipped. */
internal fun parseKeyedColors(raw: String?): Map<String, Int> =
    raw.orEmpty().split('&').mapNotNull { part ->
        val i = part.lastIndexOf('=')
        if (i <= 0) return@mapNotNull null
        val key = runCatching { java.net.URLDecoder.decode(part.substring(0, i), "UTF-8") }.getOrNull() ?: return@mapNotNull null
        val color = part.substring(i + 1).toIntOrNull() ?: return@mapNotNull null
        key to color
    }.toMap()

internal fun formatKeyedColors(map: Map<String, Int>): String =
    map.entries.joinToString("&") { java.net.URLEncoder.encode(it.key, "UTF-8") + "=" + it.value }

/** Saved by an updated install (trust the ids), or restored into a fresh one (do not). */
internal fun legacyIdsAreTrusted(firstInstallTime: Long, lastUpdateTime: Long): Boolean =
    lastUpdateTime - firstInstallTime > 5_000L
