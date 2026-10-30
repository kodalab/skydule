package com.example.gradule

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gradule.data.CalendarEvent
import com.example.gradule.data.CalendarRepository
import com.example.gradule.data.DateWindow
import com.example.gradule.data.DefaultReminder
import com.example.gradule.data.DeviceCalendar
import com.example.gradule.data.LocationProvider
import com.example.gradule.data.NewEvent
import com.example.gradule.data.SettingsStore
import com.example.gradule.data.TemperatureUnit
import com.example.gradule.data.SnoozeLength
import com.example.gradule.data.ThemeMode
import com.example.gradule.data.WeekStart
import com.example.gradule.data.WeatherRepository
import com.example.gradule.data.WeatherState
import com.example.gradule.data.groupByDay
import com.example.gradule.reminders.ReminderScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = CalendarRepository(app.contentResolver)
    private val settings = SettingsStore(app)
    private val weatherRepo = WeatherRepository()
    private val location = LocationProvider(app)

    // Known from the first instant (not after the first screen composes), so loading can start during the splash.
    private val _hasPermission = MutableStateFlow(
        app.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED &&
            app.checkSelfPermission(android.Manifest.permission.WRITE_CALENDAR) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED,
    )
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    /** The day all three tabs agree on. Month/Week navigation and Schedule scrolling target this. */
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    /** Bumped every time "Today" is pressed, so Schedule scrolls even when today is already selected. */
    private val _todayTick = MutableStateFlow(0)
    val todayTick: StateFlow<Int> = _todayTick.asStateFlow()

    /** Dates whose events are loaded. Grows as the user scrolls or pages further out. */
    private val _window = MutableStateFlow(
        LocalDate.now().let { DateWindow(it.minusDays(60), it.plusDays(270)) },
    )
    val window: StateFlow<DateWindow> = _window.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SKY)

    val temperatureUnit: StateFlow<TemperatureUnit> = settings.temperatureUnit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TemperatureUnit.AUTO)
    val weekStart: StateFlow<WeekStart> = settings.weekStart
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekStart.SYSTEM)
    val defaultReminder: StateFlow<DefaultReminder> = settings.defaultReminder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DefaultReminder.NONE)
    val showWeather: StateFlow<Boolean> = settings.showWeather
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val snoozeLength: StateFlow<SnoozeLength> = settings.snoozeLength
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SnoozeLength.TEN)

    private val _iconChoice = MutableStateFlow(com.example.gradule.icons.DayIcon.choice(app))
    val iconChoice: StateFlow<com.example.gradule.icons.IconChoice> = _iconChoice.asStateFlow()

    fun setIconChoice(choice: com.example.gradule.icons.IconChoice) {
        _iconChoice.value = choice
        com.example.gradule.icons.DayIcon.setChoice(getApplication(), choice)
    }

    fun setSnoozeLength(length: SnoozeLength) {
        viewModelScope.launch { settings.setSnoozeLength(length) }
    }


    /** The first day of the week the Week and Month tabs use, resolved from the setting or the region. */
    val firstDayOfWeek: StateFlow<DayOfWeek> = weekStart.map { choice ->
        when (choice) {
            WeekStart.SYSTEM -> WeekFields.of(Locale.getDefault()).firstDayOfWeek
            WeekStart.MONDAY -> DayOfWeek.MONDAY
            WeekStart.SUNDAY -> DayOfWeek.SUNDAY
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekFields.of(Locale.getDefault()).firstDayOfWeek)

    /** Every event in the window, bucketed by each day it touches. Refreshes when the calendar store changes. */
    val eventsByDay: StateFlow<Map<LocalDate, List<CalendarEvent>>> =
        combine(_hasPermission, _window) { ok, w -> ok to w }
            .flatMapLatest { (ok, w) ->
                if (!ok) {
                    _eventsLoaded.value = true // nothing to wait for
                    flowOf(emptyMap<LocalDate, List<CalendarEvent>>())
                } else {
                    // The first load goes straight away. A later sync can fire dozens of change notifications in a
                    // burst: wait for those to settle, and drop any load a newer change has made stale.
                    merge(flowOf(Unit), repo.changes().drop(1).debounce(250))
                        .mapLatest { repo.events(w).groupByDay().also { _eventsLoaded.value = true } }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _eventsLoaded = MutableStateFlow(false)

    /** True once the first events have been read (or there is no access to read), so the splash can end on content. */
    val eventsLoaded: StateFlow<Boolean> = _eventsLoaded.asStateFlow()

    val calendars: StateFlow<List<DeviceCalendar>> =
        _hasPermission
            .flatMapLatest { ok ->
                if (!ok) flowOf(emptyList<DeviceCalendar>())
                else merge(flowOf(Unit), repo.changes().drop(1).debounce(250)).mapLatest { repo.calendars() }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Keep reminder alarms in step with the calendar: re-plan whenever events change.
        _hasPermission
            .flatMapLatest { ok -> if (ok) repo.changes() else emptyFlow() }
            .debounce(750)
            .onEach { ReminderScheduler.reschedule(app) }
            .launchIn(viewModelScope)
    }

    // ---- Weather -------------------------------------------------------------------------------

    private val _weather = MutableStateFlow<WeatherState>(WeatherState.NeedsPermission)
    val weather: StateFlow<WeatherState> = _weather.asStateFlow()
    private var weatherFetchedAt = 0L

    /**
     * Loads current conditions for the phone's approximate location. Cheap to call often: fresh data
     * (under 20 minutes old) is reused unless [force] is set.
     */
    fun refreshWeather(hasLocationPermission: Boolean, force: Boolean = false) {
        if (!hasLocationPermission) {
            _weather.value = WeatherState.NeedsPermission
            return
        }
        val now = SystemClock.elapsedRealtime()
        val current = _weather.value
        if (!force && current is WeatherState.Ready && now - weatherFetchedAt < 20 * 60_000L) return
        if (current !is WeatherState.Ready) _weather.value = WeatherState.Loading

        viewModelScope.launch {
            val where = location.current()
            val fahrenheit = when (settings.temperatureUnit.first()) {
                TemperatureUnit.AUTO -> Locale.getDefault().country == "US"
                TemperatureUnit.CELSIUS -> false
                TemperatureUnit.FAHRENHEIT -> true
            }
            val result = where?.let { weatherRepo.current(it.latitude, it.longitude, fahrenheit) }
            if (result != null) {
                _weather.value = WeatherState.Ready(result)
                weatherFetchedAt = SystemClock.elapsedRealtime()
            } else if (_weather.value !is WeatherState.Ready) {
                _weather.value = WeatherState.Unavailable
            }
        }
    }

    // ---- Calendar ------------------------------------------------------------------------------

    fun setPermission(granted: Boolean) {
        _hasPermission.value = granted
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun goToToday() {
        selectDate(LocalDate.now())
        _todayTick.value += 1
    }

    /** Make sure [from]..[to] (inclusive) is loaded, with some padding so paging stays smooth. */
    fun ensureCovers(from: LocalDate, to: LocalDate) {
        val w = _window.value
        val start = if (from.isBefore(w.start)) from.minusDays(60) else w.start
        val end = if (!to.isBefore(w.endExclusive)) to.plusDays(61) else w.endExclusive
        if (start != w.start || end != w.endExclusive) _window.value = DateWindow(start, end)
    }

    /** Schedule hit the bottom of the list: load another chunk (capped at roughly 3 years out). */
    fun extendForward() {
        val w = _window.value
        if (w.endExclusive.isBefore(LocalDate.now().plusDays(1100))) {
            _window.value = w.copy(endExclusive = w.endExclusive.plusDays(120))
        }
    }

    /** Schedule hit the top of the list: load further back (capped at roughly 2 years). */
    fun extendBackward() {
        val w = _window.value
        if (w.start.isAfter(LocalDate.now().minusDays(730))) {
            _window.value = w.copy(start = w.start.minusDays(120))
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        viewModelScope.launch {
            settings.setTemperatureUnit(unit)
            // Re-fetch in the new unit if weather is already showing.
            if (_weather.value !is WeatherState.NeedsPermission) refreshWeather(true, force = true)
        }
    }

    fun setWeekStart(start: WeekStart) {
        viewModelScope.launch { settings.setWeekStart(start) }
    }

    fun setDefaultReminder(r: DefaultReminder) {
        viewModelScope.launch { settings.setDefaultReminder(r) }
    }

    fun setShowWeather(show: Boolean) {
        viewModelScope.launch { settings.setShowWeather(show) }
    }

    /** Creates the event, or updates it in place when [NewEvent.id] is set. */
    fun saveEvent(event: NewEvent) {
        viewModelScope.launch { repo.saveEvent(event) }
    }

    /** Current values of an event, for the editor. */
    suspend fun draftFor(eventId: Long): NewEvent? = repo.draftFor(eventId)

    suspend fun descriptionFor(eventId: Long): String? = repo.description(eventId)

    fun deleteEvent(eventId: Long) {
        viewModelScope.launch { repo.deleteEvent(eventId) }
    }

    fun createLocalCalendar() {
        viewModelScope.launch { repo.createLocalCalendar("On this phone", 0xFFE8913A.toInt()) }
    }
}
