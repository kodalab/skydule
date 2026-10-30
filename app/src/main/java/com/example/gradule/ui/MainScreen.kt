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
import kotlinx.coroutines.delay
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
import com.example.gradule.data.DetailSide
import androidx.compose.runtime.movableContentOf
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
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
    val detailSide by vm.detailSide.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Two panes (list + event panel) when there is room: 600dp+ wide and 480dp+ tall.  Folding or unfolding changes this live (the activity isn't recreated).
    val wide = configuration.screenWidthDp >= 600 &&
        configuration.screenHeightDp >= 480
    val paneWidth = (configuration.screenWidthDp * 0.45f).coerceIn(300f, 520f).dp
    // Every tab stays full width until an event opens; then the card slides in and the content narrows to its edge.
    val paneMode = when {
        !wide -> PaneMode.NONE
        else -> PaneMode.SLIDE
    }
    var paneBounds by remember { mutableStateOf(Rect.Zero) }

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
            if (paneMode == PaneMode.SLIDE) editorProgress.snapTo(1f) else editorProgress.animateTo(1f, tween(OPEN_MS, easing = OpenEasing))
        }
    }

    // True while the side card slides away, so it keeps showing its page until it has left.
    var paneClosing by remember { mutableStateOf(false) }

    fun closeEditor() {
        scope.launch {
            if (paneMode == PaneMode.SLIDE) {
                paneClosing = true
            } else {
                editorProgress.animateTo(0f, tween(CLOSE_MS, easing = CloseEasing))
                editing = null
            }
        }
    }
    // Detail page: grows out of the tapped card (progress 0 -> 1) and shrinks back into it on close.
    var opened by remember { mutableStateOf<OpenedEvent?>(null) }
    val detailProgress = remember { Animatable(0f) }

    fun openEvent(event: CalendarEvent, from: Rect) {
        if (editing != null) return
        if (opened != null) {
            // Wide screens: tapping another event swaps the panel's content in place.
            if (wide) opened = OpenedEvent(event, from)
            return
        }
        opened = OpenedEvent(event, from)
        scope.launch {
            detailProgress.snapTo(0f)
            if (paneMode == PaneMode.SLIDE) detailProgress.snapTo(1f) else detailProgress.animateTo(1f, tween(OPEN_MS, easing = OpenEasing))
        }
    }

    fun closeEvent() {
        scope.launch {
            if (paneMode == PaneMode.SLIDE) {
                paneClosing = true
            } else {
                detailProgress.animateTo(0f, tween(CLOSE_MS, easing = CloseEasing))
                opened = null
            }
        }
    }

    // Tapping a day in Week or Month jumps to that day in Schedule.
    val openDayInSchedule: (java.time.LocalDate) -> Unit = { d ->
        vm.selectDate(d)
        tabIndex = GraduleTab.Schedule.ordinal
    }

    // The editor and the detail page live in one place on a phone (full screen) and another on a wide screen
    // (inside the side panel). movableContentOf keeps their state (a half-written event) when the screen folds
    // or unfolds and they move between the two.
    val overlays = remember {
        movableContentOf { origin: Offset, inPane: Boolean ->
            val cfg = LocalConfiguration.current
            val dens = LocalDensity.current
            editing?.let { request ->
                EventEditorOverlay(
                    request = request.copy(from = request.from.translate(-origin)),
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
            opened?.let { current ->
                EventDetailOverlay(
                    opened = current.copy(from = current.from.translate(-origin)),
                    loadNotes = vm::descriptionFor,
                    progress = detailProgress.asState(),
                    onClose = { closeEvent() },
                    onDelete = {
                        vm.deleteEvent(current.event.id)
                        closeEvent()
                    },
                    onEdit = {
                        // The detail page turns into the editor in place: same area, no card to return to.
                        val event = current.event
                        scope.launch {
                            val draft = vm.draftFor(event.id) ?: return@launch
                            val d = dens.density
                            val full = if (inPane) paneBounds else Rect(0f, 0f, cfg.screenWidthDp * d, cfg.screenHeightDp * d)
                            if (inPane) {
                                editing = EditorRequest(full, draft.startDay, null, draft)
                                editorProgress.snapTo(1f)
                                opened = null
                                detailProgress.snapTo(0f)
                            } else {
                                opened = null
                                detailProgress.snapTo(0f)
                                editing = EditorRequest(full, draft.startDay, null, draft)
                                editorProgress.snapTo(1f)
                            }
                        }
                    },
                )
            }
        }
    }

    var barPx by remember { mutableIntStateOf(0) }
    // Month's day panel has its own add button, so the floating one steps aside while the panel is open.
    var monthPanelOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val barDp = with(density) { barPx.toDp() }
    val paneOpen = (opened != null || editing != null) && !paneClosing
    val fromRight = detailSide == DetailSide.RIGHT
    // Schedule: the card is always there. Week and Month: it slides in from the chosen side and the content
    // makes room for it, then slides out again when closed.
    val paneSlot: @Composable () -> Unit = {
        when (paneMode) {
            PaneMode.SLIDE -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = if (fromRight) Alignment.CenterEnd else Alignment.CenterStart,
            ) { androidx.compose.animation.AnimatedVisibility(
                visible = paneOpen,
                enter = androidx.compose.animation.slideInHorizontally(tween(OPEN_MS, easing = OpenEasing)) { if (fromRight) it else -it },
                exit = androidx.compose.animation.slideOutHorizontally(tween(CLOSE_MS, easing = CloseEasing)) { if (fromRight) it else -it },
            ) {
                // The card keeps its page until the slide-out has finished, then the page is let go.
                androidx.compose.runtime.DisposableEffect(Unit) {
                    onDispose {
                        if (paneClosing) {
                            editing = null
                            opened = null
                            paneClosing = false
                            scope.launch { editorProgress.snapTo(0f); detailProgress.snapTo(0f) }
                        }
                    }
                }
                DetailPane(paneWidth, true, paneMode, { paneBounds = it }) {
                    overlays(paneBounds.topLeft, true)
                }
            } }
            PaneMode.NONE -> Unit
        }
    }
    // The content makes room for the card by padding, so the sky, the blur and the tab bar's fade
    // stay one continuous layer right across the screen, behind the card as well.
    val roomSpec = tween<androidx.compose.ui.unit.Dp>(if (paneOpen) OPEN_MS else CLOSE_MS, easing = if (paneOpen) OpenEasing else CloseEasing)
    val startPad by androidx.compose.animation.core.animateDpAsState(if (paneMode == PaneMode.SLIDE && paneOpen && !fromRight) paneWidth + 24.dp else 0.dp, roomSpec, label = "startPad")
    val endPad by androidx.compose.animation.core.animateDpAsState(if (paneMode == PaneMode.SLIDE && paneOpen && fromRight) paneWidth + 24.dp else 0.dp, roomSpec, label = "endPad")
    CompositionLocalProvider(LocalBarInset provides barDp) {
        Box(Modifier.fillMaxSize()) {
            // Schedule scrolls underneath the floating bar (blurred there); Week and Month stay clear of it.
            BottomBlur(barPx = barPx, meltContent = tab == GraduleTab.Schedule, modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().statusBarsPadding().padding(start = startPad, end = endPad)) {
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
                            onAdd = { d, r -> openEditor(r, d, null) },
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
                            onEvent = { e, r -> openEvent(e, r) },
                            panelOpen = monthPanelOpen,
                            onPanelOpen = { monthPanelOpen = it },
                            onAdd = { d, r -> openEditor(r, d, null) },
                            onSelectDate = vm::selectDate,
                            onEnsure = vm::ensureCovers,
                            onToday = vm::goToToday,
                            onSettings = { openSettings() },
                        ) }
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(start = startPad, end = endPad).onSizeChanged { barPx = it.height }) {
                GlassTabBar(
                    selected = tab,
                    onSelect = { tabIndex = it.ordinal },
                )
            }
            // The same icon-only button on every tab, except Month with its day panel open (the panel has its own).
            androidx.compose.animation.AnimatedVisibility(
                visible = !(tab == GraduleTab.Month && monthPanelOpen),
                enter = androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp + endPad, bottom = barDp + 16.dp),
            ) {
                FloatingActionButton(
                    onClick = {
                        val b = fabBounds
                        if (b != null) openEditor(b, if (tab == GraduleTab.Schedule) java.time.LocalDate.now() else date, null)
                    },
                    containerColor = White,
                    contentColor = Ink,
                    modifier = Modifier.onGloballyPositioned { fabBounds = it.boundsInRoot() },
                ) { Icon(Icons.Default.Add, contentDescription = "New event") }
            }
            paneSlot()
        }
    }
    if (paneMode == PaneMode.NONE) overlays(Offset.Zero, false)
    BackHandler(enabled = editing != null && opened == null) { closeEditor() }
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
            from = gearRect(if (paneMode == PaneMode.SLIDE && paneOpen && fromRight) paneWidth + 24.dp else 0.dp),
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
            showSideOption = vm.supportsTwoPanes,
            detailSide = detailSide,
            onDetailSide = vm::setDetailSide,
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
private fun gearRect(rightInset: androidx.compose.ui.unit.Dp): Rect {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val top = WindowInsets.statusBars.getTop(density).toFloat()
    val screenW = config.screenWidthDp * density.density
    val size = 48f * density.density
    val right = screenW - rightInset.value * density.density - 12f * density.density
    return Rect(right - size, top, right, top + size)
}


/**
 * The side panel on wide screens: a floating frosted card (the same glass as the list cards) that holds the event
 * page or editor, or a hint when nothing is open. Its bounds are reported so pages can grow out of the tapped card.
 */
@Composable
private fun DetailPane(
    width: androidx.compose.ui.unit.Dp,
    hasContent: Boolean,
    mode: PaneMode,
    onBounds: (Rect) -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
    // Margin on every side; the outer side keeps clear of the screen edge and the inner side from the list.
    Box(
        Modifier
            .fillMaxHeight()
            .width(width + 24.dp)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(12.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { onBounds(it.boundsInRoot()) }
                .background(Glass, shape)
                .border(1.dp, White.copy(alpha = 0.14f), shape),
        ) {
            if (!hasContent) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Select an event", fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Light, color = White.copy(alpha = 0.8f))
                    Text(
                        "Its details open here, beside your list.",
                        fontSize = 14.sp,
                        color = White.copy(alpha = 0.55f),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            CompositionLocalProvider(LocalPaneMode provides mode) { content() }
        }
    }
}
