package com.example.gradule.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.LaunchedEffect
import com.example.gradule.MainActivity
import androidx.core.content.ContextCompat
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.coroutineScope
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.unit.dp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gradule.MainViewModel
import com.example.gradule.data.CalendarEvent

enum class GraduleTab(val label: String) {
    Schedule("Schedule"),
    Week("Week"),
    Month("Month"),
}

@Composable
fun MainScreen(vm: MainViewModel) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = GraduleTab.entries[tabIndex]

    val date by vm.selectedDate.collectAsStateWithLifecycle()
    val byDay by vm.eventsByDay.collectAsStateWithLifecycle()
    val window by vm.window.collectAsStateWithLifecycle()
    val calendars by vm.calendars.collectAsStateWithLifecycle()
    val mode by vm.themeMode.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val todayTick by vm.todayTick.collectAsStateWithLifecycle()
    val temperatureUnit by vm.temperatureUnit.collectAsStateWithLifecycle()
    val weekStart by vm.weekStart.collectAsStateWithLifecycle()
    val defaultReminder by vm.defaultReminder.collectAsStateWithLifecycle()
    val snoozeLength by vm.snoozeLength.collectAsStateWithLifecycle()
    val showWeather by vm.showWeather.collectAsStateWithLifecycle()
    val firstDow by vm.firstDayOfWeek.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        vm.refreshWeather(ok, force = true)
    }
    val onWeather: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        when {
            !granted -> locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            weather is com.example.gradule.data.WeatherState.Unavailable -> vm.refreshWeather(true, force = true)
            // The link to another app exists only in the personal (debug) build.
            com.example.gradule.BuildConfig.GRADIENT_WEATHER_LINK -> context.openGradientWeather()
            else -> Unit
        }
    }

    var showSettings by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }
    val iconChoice by vm.iconChoice.collectAsStateWithLifecycle()
    val settingsProgress = remember { Animatable(0f) }

    fun openSettings() {
        scope.launch {
            settingsProgress.snapTo(0f)
            showSettings = true
            settingsProgress.animateTo(1f, tween(OPEN_MS, easing = OpenEasing))
        }
    }

    fun closeSettings() {
        scope.launch {
            settingsProgress.animateTo(0f, tween(CLOSE_MS, easing = CloseEasing))
            showSettings = false
        }
    }

    // New-event editor: grows out of the + button (or the tapped Week slot), same motion as the detail page.
    var editing by remember { mutableStateOf<EditorRequest?>(null) }
    val editorProgress = remember { Animatable(0f) }
    var fabBounds by remember { mutableStateOf<Rect?>(null) }

    fun openEditor(from: Rect, day: java.time.LocalDate, startMinutes: Int?) {
        if (editing != null) return
        editing = EditorRequest(from, day, startMinutes)
        scope.launch {
            editorProgress.snapTo(0f)
            editorProgress.animateTo(1f, tween(OPEN_MS, easing = OpenEasing))
        }
    }

    fun closeEditor() {
        scope.launch {
            editorProgress.animateTo(0f, tween(CLOSE_MS, easing = CloseEasing))
            editing = null
        }
    }
    // Detail page: grows out of the tapped card (progress 0 -> 1) and shrinks back into it on close.
    var opened by remember { mutableStateOf<OpenedEvent?>(null) }
    val detailProgress = remember { Animatable(0f) }

    fun openEvent(event: CalendarEvent, from: Rect) {
        if (opened != null) return
        opened = OpenedEvent(event, from)
        scope.launch {
            detailProgress.snapTo(0f)
            detailProgress.animateTo(1f, tween(OPEN_MS, easing = OpenEasing))
        }
    }

    fun closeEvent() {
        scope.launch {
            detailProgress.animateTo(0f, tween(CLOSE_MS, easing = CloseEasing))
            opened = null
        }
    }

    // Tapping a day in Week or Month jumps to that day in Schedule.
    val openDayInSchedule: (java.time.LocalDate) -> Unit = { d ->
        vm.selectDate(d)
        tabIndex = GraduleTab.Schedule.ordinal
    }

    var barPx by remember { mutableIntStateOf(0) }
    val barDp = with(density) { barPx.toDp() }
    CompositionLocalProvider(LocalBarInset provides barDp) {
        Box(Modifier.fillMaxSize()) {
            // Schedule scrolls underneath the floating bar (blurred there); Week and Month stay clear of it.
            BottomBlur(barPx = if (tab == GraduleTab.Schedule) barPx else 0, modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().statusBarsPadding()) {
                    when (tab) {
                        GraduleTab.Schedule -> ScheduleTab(
                            selected = date,
                            todayTick = todayTick,
                            byDay = byDay,
                            window = window,
                            weather = weather,
                            showWeather = showWeather,
                                onWeather = onWeather,
                            onEvent = { e, r -> openEvent(e, r) },
                            onToday = vm::goToToday,
                            onSettings = { openSettings() },
                            onNeedForward = vm::extendForward,
                            onNeedBackward = vm::extendBackward,
                        )
                        // Keyed so a change of first day of the week rebuilds the pager from the selected date.
                        GraduleTab.Week -> key(firstDow) { WeekTab(
                            selected = date,
                            firstDow = firstDow,
                            byDay = byDay,
                            onOpenDay = openDayInSchedule,
                            onEvent = { e, r -> openEvent(e, r) },
                            onCreateAt = { d, minutes, r -> openEditor(r, d, minutes) },
                            onSelectDate = vm::selectDate,
                            onEnsure = vm::ensureCovers,
                            onToday = vm::goToToday,
                            onSettings = { openSettings() },
                        ) }
                        GraduleTab.Month -> key(firstDow) { MonthTab(
                            selected = date,
                            firstDow = firstDow,
                            byDay = byDay,
                            onPickDay = openDayInSchedule,
                            onSelectDate = vm::selectDate,
                            onEnsure = vm::ensureCovers,
                            onToday = vm::goToToday,
                            onSettings = { openSettings() },
                        ) }
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomCenter).onSizeChanged { barPx = it.height }) {
                GlassTabBar(
                    selected = tab,
                    onSelect = { tabIndex = it.ordinal },
                )
            }
            // Same icon-only button on every tab.
            FloatingActionButton(
                onClick = {
                    val b = fabBounds
                    if (b != null) openEditor(b, if (tab == GraduleTab.Schedule) java.time.LocalDate.now() else date, null)
                },
                containerColor = White,
                contentColor = Ink,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = barDp + 16.dp)
                    .onGloballyPositioned { fabBounds = it.boundsInRoot() },
            ) { Icon(Icons.Default.Add, contentDescription = "New event") }
        }
    }

    editing?.let { request ->
        EventEditorOverlay(
            request = request,
            progress = editorProgress.asState(),
            calendars = calendars,
            onCreateLocal = vm::createLocalCalendar,
            defaultReminder = defaultReminder.minutes,
            onClose = { closeEditor() },
            onSave = { event ->
                vm.saveEvent(event)
                closeEditor()
            },
        )
    }
    BackHandler(enabled = editing != null && opened == null) { closeEditor() }

    opened?.let { current ->
        EventDetailOverlay(
            opened = current,
            loadNotes = vm::descriptionFor,
            progress = detailProgress.asState(),
            onClose = { closeEvent() },
            onDelete = {
                vm.deleteEvent(current.event.id)
                closeEvent()
            },
            onEdit = {
                // The detail page turns into the editor in place: same full-screen area, no card to return to.
                val event = current.event
                scope.launch {
                    val draft = vm.draftFor(event.id) ?: return@launch
                    val d = density.density
                    val full = Rect(0f, 0f, configuration.screenWidthDp * d, configuration.screenHeightDp * d)
                    opened = null
                    detailProgress.snapTo(0f)
                    editing = EditorRequest(full, draft.startDay, null, draft)
                    editorProgress.snapTo(1f)
                }
            },
        )
    }
    BackHandler(enabled = opened != null) { closeEvent() }

    // A tapped reminder notification opens its event. It may take a moment for the events to load.
    val requestedEvent by MainActivity.openEventRequests.collectAsStateWithLifecycle()
    LaunchedEffect(requestedEvent, byDay) {
        val id = requestedEvent ?: return@LaunchedEffect
        val event = byDay.values.asSequence().flatten().firstOrNull { it.id == id } ?: return@LaunchedEffect
        vm.selectDate(event.startDay)
        tabIndex = GraduleTab.Schedule.ordinal
        val d = density.density
        val cx = configuration.screenWidthDp * d / 2f
        val cy = configuration.screenHeightDp * d / 2f
        openEvent(event, Rect(Offset(cx, cy), 40f * d))
        MainActivity.openEventRequests.value = null
    }

    if (showSettings) {
        // Grows out of the gear and shrinks back into it, with the same motion as the event pages.
        TransformSheet(
            from = gearRect(),
            progress = settingsProgress.asState(),
            startColor = Glass,
            endColor = SettingsBackground,
            startCorner = 24.dp,
        ) { modifier -> SettingsScreen(
            modifier = modifier,
            mode = mode,
            onMode = vm::setThemeMode,
            weekStart = weekStart,
            onWeekStart = vm::setWeekStart,
            showWeather = showWeather,
            onShowWeather = vm::setShowWeather,
            defaultReminder = defaultReminder,
            onDefaultReminder = vm::setDefaultReminder,
            snoozeLength = snoozeLength,
            onSnoozeLength = vm::setSnoozeLength,
            temperature = temperatureUnit,
            onTemperature = vm::setTemperatureUnit,
            hasLocalCalendar = calendars.any { it.isLocal },
            onCreateLocal = vm::createLocalCalendar,
            iconChoice = iconChoice,
            onOpenIconPicker = { showIconPicker = true },
            onOpenPrivacy = { showPrivacy = true },
            onClose = { closeSettings() },
        ) }
    }
    BackHandler(enabled = showSettings) { closeSettings() }

    androidx.compose.animation.AnimatedVisibility(
        visible = showIconPicker,
        enter = androidx.compose.animation.slideInHorizontally { it },
        exit = androidx.compose.animation.slideOutHorizontally { it },
    ) { IconPickerScreen(choice = iconChoice, onChoice = vm::setIconChoice, onClose = { showIconPicker = false }) }
    BackHandler(enabled = showIconPicker) { showIconPicker = false }

    // The policy sits above settings. Declared after settings' back handler so Back closes it first.
    androidx.compose.animation.AnimatedVisibility(
        visible = showPrivacy,
        enter = androidx.compose.animation.slideInHorizontally { it },
        exit = androidx.compose.animation.slideOutHorizontally { it },
    ) { PrivacyScreen(onClose = { showPrivacy = false }) }
    BackHandler(enabled = showPrivacy) { showPrivacy = false }
}

/** Approximate on-screen spot of the gear button in the top-right of every tab's header. */
@Composable
private fun gearRect(): Rect {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val top = WindowInsets.statusBars.getTop(density).toFloat()
    val screenW = config.screenWidthDp * density.density
    val size = 48f * density.density
    val right = screenW - 12f * density.density
    return Rect(right - size, top, right, top + size)
}
