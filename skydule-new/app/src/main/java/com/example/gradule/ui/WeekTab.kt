package com.example.gradule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gradule.data.CalendarEvent
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import java.time.format.TextStyle as JTextStyle

private const val PAGES = 2000
private const val CENTER = 1000
private val HOUR = 60.dp
private val GUTTER = 40.dp

/** A timed event placed in a day column; [lane]/[lanes] handle side-by-side overlaps. */
private class Placed(val e: CalendarEvent, val start: Float, val end: Float) {
    var lane = 0
    var lanes = 1
}

/** Packs a day's timed events into lanes so overlapping events sit side by side. */
private fun layoutDay(events: List<CalendarEvent>, date: LocalDate): List<Placed> {
    val dayStart = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val items = events.filter { !it.allDay }.map {
        val s = ((it.begin - dayStart) / 60_000f).coerceIn(0f, 1440f)
        val en = ((it.end - dayStart) / 60_000f).coerceIn(0f, 1440f)
        Placed(it, s, maxOf(en, minOf(s + 20f, 1440f)))
    }.sortedWith(compareBy<Placed> { it.start }.thenByDescending { it.end })

    var clusterEnd = -1f
    val laneEnds = mutableListOf<Float>()
    val cluster = mutableListOf<Placed>()
    for (p in items) {
        if (cluster.isNotEmpty() && p.start >= clusterEnd) {
            val n = laneEnds.size
            cluster.forEach { it.lanes = n }
            cluster.clear()
            laneEnds.clear()
            clusterEnd = -1f
        }
        var lane = laneEnds.indexOfFirst { it <= p.start }
        if (lane == -1) {
            laneEnds.add(p.end)
            lane = laneEnds.size - 1
        } else {
            laneEnds[lane] = p.end
        }
        p.lane = lane
        cluster.add(p)
        clusterEnd = maxOf(clusterEnd, p.end)
    }
    val n = maxOf(laneEnds.size, 1)
    cluster.forEach { it.lanes = n }
    return items
}

/**
 * A swipeable week with an hour grid. Tapping a day header opens that day's events in a panel under the grid, like
 * the Month tab; tapping it again closes it.
 */
@Composable
fun WeekTab(
    selected: LocalDate,
    firstDow: DayOfWeek,
    byDay: Map<LocalDate, List<CalendarEvent>>,
    onEvent: EventOpener,
    onCreateAt: (LocalDate, Int, Rect) -> Unit,
    panelOpen: Boolean,
    onPanelOpen: (Boolean) -> Unit,
    onAdd: (LocalDate, Rect) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onEnsure: (LocalDate, LocalDate) -> Unit,
    onToday: () -> Unit,
    onSettings: () -> Unit,
) {
    val today = LocalDate.now()
    val anchor = remember(firstDow) { LocalDate.now().with(TemporalAdjusters.previousOrSame(firstDow)) }
    fun weekStartOf(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(firstDow))
    fun pageFor(d: LocalDate): Int = CENTER + ChronoUnit.WEEKS.between(anchor, weekStartOf(d)).toInt()
    fun startOf(page: Int): LocalDate = anchor.plusWeeks((page - CENTER).toLong())

    val pager = rememberPagerState(initialPage = pageFor(selected), pageCount = { PAGES })
    val currentSelected by rememberUpdatedState(selected)

    // Selected date changed elsewhere (Today button, another tab): follow it.
    LaunchedEffect(selected) {
        val target = pageFor(selected)
        if (target != pager.currentPage) pager.scrollToPage(target)
    }
    // User swiped to another week: keep the same weekday selected.
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            val ws = startOf(page)
            val sel = currentSelected
            if (weekStartOf(sel) != ws) {
                val dayOffset = ChronoUnit.DAYS.between(weekStartOf(sel), sel)
                onSelectDate(ws.plusDays(dayOffset))
            }
        }
    }
    LaunchedEffect(pager.currentPage) {
        val ws = startOf(pager.currentPage)
        onEnsure(ws.minusDays(14), ws.plusDays(21))
    }

    // One vertical scroll position shared by every page, so swiping weeks keeps the same time of day in view.
    val density = LocalDensity.current
    val initialScroll = remember {
        val h = (LocalTime.now().hour - 1).coerceIn(0, 16)
        with(density) { (HOUR * h).roundToPx() }
    }
    val scroll = rememberScrollState(initial = initialScroll)

    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = LocalTime.now()
        }
    }

    val ws = startOf(pager.currentPage)
    val we = ws.plusDays(6)
    val fmt = remember { DateTimeFormatter.ofPattern("d MMM") }

    val currentSelectedDay by rememberUpdatedState(selected)
    // Same slide as the Month panel: the grid gives up room as the panel grows.
    val reveal by animateFloatAsState(
        targetValue = if (panelOpen) 1f else 0f,
        animationSpec = Motion.panel(),
        label = "weekDayPanel",
    )

    Column(Modifier.fillMaxSize()) {
        CompactHeader(
            // Same month: "25 – 31 Oct" (short enough for a foldable's cover screen); across months: "28 Oct – 3 Nov".
            title = if (ws.month == we.month) "${ws.dayOfMonth} – ${we.format(fmt)}" else "${ws.format(fmt)} – ${we.format(fmt)}",
            subtitle = "${we.year}",
            showToday = true,
            onToday = onToday,
            onSettings = onSettings,
        )
        HorizontalPager(state = pager, modifier = Modifier.weight(1.2f).fillMaxWidth()) { page ->
            WeekPage(
                weekStart = startOf(page),
                today = today,
                selected = if (panelOpen) selected else null,
                byDay = byDay,
                scroll = scroll,
                now = now,
                onPickDay = { d ->
                    if (panelOpen && d == currentSelectedDay) {
                        onPanelOpen(false)
                    } else {
                        onSelectDate(d)
                        onPanelOpen(true)
                    }
                },
                onEvent = onEvent,
                onCreateAt = onCreateAt,
            )
        }
        if (reveal > 0.001f) {
            Box(Modifier.weight(reveal).fillMaxWidth().clipToBounds().alpha(reveal)) {
                DayPanel(
                    date = selected,
                    events = byDay[selected].orEmpty(),
                    onEvent = onEvent,
                    onAdd = { r -> onAdd(selected, r) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // The tab bar's room, plus Month's extra gap as the panel opens, so both tabs line up.
        Spacer(Modifier.height(LocalBarInset.current + 8.dp * reveal))
    }
}

@Composable
private fun WeekPage(
    weekStart: LocalDate,
    today: LocalDate,
    selected: LocalDate?,
    byDay: Map<LocalDate, List<CalendarEvent>>,
    scroll: ScrollState,
    now: LocalTime,
    onPickDay: (LocalDate) -> Unit,
    onEvent: EventOpener,
    onCreateAt: (LocalDate, Int, Rect) -> Unit,
) {
    val days = remember(weekStart) { (0..6).map { weekStart.plusDays(it.toLong()) } }

    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(GridGlass),
    ) {
        // Day headers
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp)) {
            Spacer(Modifier.width(GUTTER))
            days.forEach { d ->
                val isToday = d == today
                val isSelected = d == selected
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) White.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onPickDay(d) }
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        d.dayOfWeek.getDisplayName(JTextStyle.SHORT, Locale.getDefault()),
                        fontSize = 11.sp,
                        color = White.copy(alpha = 0.7f),
                    )
                    Box(
                        Modifier
                            .padding(top = 2.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (isToday) White else Color.Transparent)
                            .then(if (isSelected && !isToday) Modifier.border(1.5.dp, White, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            d.dayOfMonth.toString(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isToday) Ink else White,
                        )
                    }
                }
            }
        }

        // All-day strip, only when the week has any all-day events
        val allDay = days.map { d -> byDay[d].orEmpty().filter { it.allDay } }
        if (allDay.any { it.isNotEmpty() }) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Text(
                    "all day",
                    modifier = Modifier.width(GUTTER).padding(end = 6.dp, top = 2.dp),
                    textAlign = TextAlign.End,
                    fontSize = 9.sp,
                    color = White.copy(alpha = 0.55f),
                )
                allDay.forEach { list ->
                    Column(Modifier.weight(1f).padding(horizontal = 1.dp)) {
                        list.take(2).forEach { e ->
                            EventChip(e, modifier = Modifier.padding(bottom = 1.dp), onClick = { bounds -> onEvent(e, bounds) })
                        }
                        if (list.size > 2) {
                            Text("+${list.size - 2}", fontSize = 9.sp, color = White.copy(alpha = 0.75f))
                        }
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            TimeGrid(days, today, byDay, scroll, now, onEvent, onCreateAt)
        }
    }
}

@Composable
private fun TimeGrid(
    days: List<LocalDate>,
    today: LocalDate,
    byDay: Map<LocalDate, List<CalendarEvent>>,
    scroll: ScrollState,
    now: LocalTime,
    onEvent: EventOpener,
    onCreateAt: (LocalDate, Int, Rect) -> Unit,
) {
    val lineColor = White.copy(alpha = 0.10f)
    Row(Modifier.fillMaxWidth().verticalScroll(scroll).height(HOUR * 24)) {
        // Hour labels
        Box(Modifier.width(GUTTER).fillMaxHeight()) {
            for (h in 1..23) {
                Text(
                    "%02d".format(h),
                    fontSize = 10.sp,
                    color = White.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = HOUR * h - 7.dp)
                        .padding(end = 6.dp),
                )
            }
        }
        // Grid lines + seven day columns
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .drawBehind {
                    val hourPx = HOUR.toPx()
                    for (h in 0..24) {
                        drawLine(lineColor, Offset(0f, h * hourPx), Offset(size.width, h * hourPx), strokeWidth = 1f)
                    }
                    val colW = size.width / 7f
                    for (i in 1..6) {
                        drawLine(lineColor, Offset(i * colW, 0f), Offset(i * colW, size.height), strokeWidth = 1f)
                    }
                },
        ) {
            days.forEach { d ->
                DayColumn(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    date = d,
                    isToday = d == today,
                    events = byDay[d].orEmpty(),
                    now = now,
                    onEvent = onEvent,
                    onCreateAt = onCreateAt,
                )
            }
        }
    }
}

@Composable
private fun DayColumn(
    modifier: Modifier,
    date: LocalDate,
    isToday: Boolean,
    events: List<CalendarEvent>,
    now: LocalTime,
    onEvent: EventOpener,
    onCreateAt: (LocalDate, Int, Rect) -> Unit,
) {
    val holder = remember { CoordsHolder() }
    val hourPx = with(LocalDensity.current) { HOUR.toPx() }
    BoxWithConstraints(
        modifier
            .then(if (isToday) Modifier.background(White.copy(alpha = 0.06f)) else Modifier)
            .onGloballyPositioned { holder.coords = it }
            // Tap an empty slot: new event at that hour, growing out of the tapped spot.
            .pointerInput(date) {
                detectTapGestures { pos ->
                    val c = holder.coords
                    if (c != null && c.isAttached) {
                        val minutes = ((pos.y / hourPx) * 60f).toInt().coerceIn(0, 23 * 60) / 30 * 30
                        val origin = c.positionInRoot()
                        val top = origin.y + (minutes / 60f) * hourPx
                        onCreateAt(
                            date,
                            minutes,
                            Rect(origin.x, top, origin.x + c.size.width, top + hourPx),
                        )
                    }
                }
            },
    ) {
        val colW = maxWidth
        val placed = remember(events, date) { layoutDay(events, date) }
        placed.forEach { p ->
            val top = HOUR * (p.start / 60f)
            val height = maxOf(HOUR * ((p.end - p.start) / 60f), 18.dp)
            val bg = eventColor(p.e.color)
            val onBg = bg.onColor()
            val tall = height > 44.dp
            Box(
                Modifier
                    .offset(x = colW * p.lane / p.lanes, y = top)
                    .width(colW / p.lanes - 2.dp)
                    .height(height - 1.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg.copy(alpha = 0.92f))
                    .clickableWithBounds { bounds -> onEvent(p.e, bounds) }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Column {
                    Text(
                        p.e.title.ifBlank { "(No title)" },
                        fontSize = 10.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = onBg,
                        maxLines = if (tall) 3 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (tall) {
                        Text(formatTime(p.e.begin), fontSize = 9.sp, color = onBg.copy(alpha = 0.8f))
                    }
                }
            }
        }
        if (isToday) {
            val y = HOUR * ((now.hour * 60 + now.minute) / 60f)
            val red = Color(0xFFFF6B6B)
            Box(Modifier.offset(y = y).fillMaxWidth().height(2.dp).background(red))
            Box(Modifier.offset(x = (-3).dp, y = y - 3.dp).size(8.dp).clip(CircleShape).background(red))
        }
    }
}
