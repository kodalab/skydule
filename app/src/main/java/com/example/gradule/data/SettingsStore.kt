package com.example.gradule.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[themeKey] = mode.name }
    suspend fun setTemperatureUnit(unit: TemperatureUnit) = context.dataStore.edit { it[tempKey] = unit.name }
    suspend fun setWeekStart(start: WeekStart) = context.dataStore.edit { it[weekStartKey] = start.name }
    suspend fun setDefaultReminder(r: DefaultReminder) = context.dataStore.edit { it[reminderKey] = r.name }
    suspend fun setShowWeather(show: Boolean) = context.dataStore.edit { it[showWeatherKey] = show }
    suspend fun setDetailSide(side: DetailSide) = context.dataStore.edit { it[detailSideKey] = side.name }
    suspend fun setSnoozeLength(length: SnoozeLength) = context.dataStore.edit { it[snoozeKey] = length.name }
}
