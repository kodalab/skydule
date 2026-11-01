package com.kodalabs.skydule.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.Rect
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kodalabs.skydule.data.CalendarEvent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import java.time.format.TextStyle as JTextStyle

private const val PAGES = 2000
private const val CENTER = 1000

/**
 * A swipeable month grid with a panel underneath. Tapping a day selects it and lists its events in the panel; tapping
 * an event opens it.
 */
@Composable
fun MonthTab(
    selected: LocalDate,
    firstDow: DayOfWeek,
    byDay: Map<LocalDate, List<CalendarEvent>>,
    onEvent: (CalendarEvent, Rect) -> Unit,
    panelOpen: Boolean,
    onPanelOpen: (Boolean) -> Unit,
    onAdd: (LocalDate, Rect) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onEnsure: (LocalDate, LocalDate) -> Unit,
    onToday: () -> Unit,
    onSettings: () -> Unit,
) {
    val today = LocalDate.now()
    val anchor = remember { YearMonth.now() }
    fun pageFor(d: LocalDate): Int = CENTER + ChronoUnit.MONTHS.between(anchor, YearMonth.from(d)).toInt()
    fun monthOf(page: Int): YearMonth = anchor.plusMonths((page - CENTER).toLong())

    val pager = rememberPagerState(initialPage = pageFor(selected), pageCount = { PAGES })
    val currentSelected by rememberUpdatedState(selected)

    // Selected date changed elsewhere (Today button, another tab): follow it.
    LaunchedEffect(selected) {
        val target = pageFor(selected)
        if (target != pager.currentPage) pager.scrollToPage(target)
    }
    // User swiped to another month: carry the selected date along (same day of month, clamped).
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            val ym = monthOf(page)
            val sel = currentSelected
            if (YearMonth.from(sel) != ym) {
                onSelectDate(ym.atDay(minOf(sel.dayOfMonth, ym.lengthOfMonth())))
            }
        }
    }
    LaunchedEffect(pager.currentPage) {
        val ym = monthOf(pager.currentPage)
        onEnsure(ym.atDay(1).minusDays(7), ym.atEndOfMonth().plusDays(7))
    }

    val shown = monthOf(pager.currentPage)
    // The month is a plain full-height calendar. Tapping a day slides its panel down from under the grid; tapping the
    // same day again slides it away; tapping another day swaps what the open panel shows.
    val reveal by animateFloatAsState(
        targetValue = if (panelOpen) 1f else 0f,
        animationSpec = Motion.panel(),
        label = "dayPanel",
    )
    Column(Modifier.fillMaxSize()) {
        CompactHeader(
            title = shown.month.getDisplayName(JTextStyle.FULL, Locale.getDefault()),
            subtitle = "${shown.year}",
            showToday = true,
            onToday = onToday,
            onSettings = onSettings,
        )
        WeekdayLabels(firstDow)
        HorizontalPager(state = pager, modifier = Modifier.weight(1.2f).fillMaxWidth()) { page ->
            MonthGrid(
                ym = monthOf(page),
                firstDow = firstDow,
                today = today,
                selected = if (panelOpen) selected else null,
                reveal = reveal,
                byDay = byDay,
                onPick = { d ->
                    if (panelOpen && d == currentSelected) {
                        onPanelOpen(false)
                    } else {
                        onSelectDate(d)
                        onPanelOpen(true)
                    }
                },
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
        // Always reserved, so the grid's bottom edge never jumps as the panel comes and goes.
        Spacer(Modifier.height(LocalBarInset.current + 8.dp))
    }
}

/** The day panel under the Month grid and the Week grid: the day's events as cards, with a + to add one. */
@Composable
internal fun DayPanel(
    date: LocalDate,
    events: List<CalendarEvent>,
    onEvent: (CalendarEvent, Rect) -> Unit,
    onAdd: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Glass)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                date.format(DateTimeFormatter.ofPattern("EEEE d MMMM")),
                modifier = Modifier.weight(1f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = White,
            )
            // Adds an event on this day without leaving the panel. It stands in for the floating button while the panel is open.
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(White)
                    .clickableWithBounds(onAdd),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Add, contentDescription = "Add event", tint = Ink) }
        }
        if (events.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Nothing planned", fontSize = 20.sp, fontWeight = FontWeight.Light, color = White.copy(alpha = 0.85f))
                Text("Tap + to add an event", fontSize = 14.sp, color = White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 4.dp))
            }
        } else {
            val listState = rememberLazyListState()
            // Another day starts at the top of its list.
            LaunchedEffect(date) { listState.scrollToItem(0) }
            // A gentle fade at the bottom edge says there is more to scroll. Only for 3+ events (it looks odd on two), and it goes away at the end of the list.
            val moreBelow by animateFloatAsState(if (events.size >= 3 && listState.canScrollForward) 1f else 0f, Motion.fastEffects(), label = "moreBelow")
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        if (moreBelow > 0f) {
                            val fade = 40.dp.toPx().coerceAtMost(size.height)
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    1f to Color.Black,
                                    startY = size.height - fade,
                                    endY = size.height,
                                ),
                                topLeft = Offset(0f, size.height - fade),
                                size = Size(size.width, fade),
                                alpha = moreBelow,
                                blendMode = BlendMode.DstOut,
                            )
                        }
                    },
                // Room at the end so the last card can scroll clear of the + button.
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 6.dp, bottom = 72.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(events, key = { "${it.id}-${it.begin}" }) { e -> EventCard(e, onClick = { r -> onEvent(e, r) }) }
            }
        }
    }
}

@Composable
private fun WeekdayLabels(firstDow: DayOfWeek) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)) {
        for (i in 0..6) {
            Text(
                firstDow.plus(i.toLong()).getDisplayName(JTextStyle.SHORT, Locale.getDefault()),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                color = White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    ym: YearMonth,
    firstDow: DayOfWeek,
    today: LocalDate,
    selected: LocalDate?,
    reveal: Float,
    byDay: Map<LocalDate, List<CalendarEvent>>,
    onPick: (LocalDate) -> Unit,
) {
    val first = ym.atDay(1)
    val offset = (first.dayOfWeek.value - firstDow.value + 7) % 7
    val weeks = (offset + ym.lengthOfMonth() + 6) / 7
    val gridStart = first.minusDays(offset.toLong())

    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(GridGlass)
            .padding(6.dp),
    ) {
        for (w in 0 until weeks) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (i in 0..6) {
                    val d = gridStart.plusDays((w * 7 + i).toLong())
                    DayCell(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        date = d,
                        inMonth = d.month == ym.month,
                        isToday = d == today,
                        isSelected = d == selected,
                        events = byDay[d].orEmpty(),
                        reveal = reveal,
                        onClick = { onPick(d) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    modifier: Modifier,
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    events: List<CalendarEvent>,
    reveal: Float,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) White.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 3.dp)
            .alpha(if (inMonth) 1f else 0.4f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (isToday) White else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isToday) Ink else White,
            )
        }
        Spacer(Modifier.height(3.dp))
        // Roomy (panel away): up to two titled chips, then "+N". Panel open: the cells are short, so the chips give way
        // to one dot per event (up to three). One value, `reveal`, drives both the cell height and this swap.
        val chipsAlpha = (1f - reveal * 2f).coerceIn(0f, 1f)
        val dotsAlpha = (reveal * 2f - 1f).coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().clipToBounds()) {
            if (chipsAlpha > 0f) {
                Column(
                    Modifier.fillMaxWidth().graphicsLayer { alpha = chipsAlpha },
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    events.take(2).forEach { e ->
                        val bg = eventColor(e.color)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(chipHeight(9.5.sp, 16.dp))
                                .clip(RoundedCornerShape(5.dp))
                                .background(bg)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                e.title.ifBlank { "(No title)" },
                                style = chipTextStyle(9.5.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = bg.onColor(),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (events.size > 2) {
                        Text("+${events.size - 2}", fontSize = 10.sp, color = White.copy(alpha = 0.85f), modifier = Modifier.padding(start = 3.dp))
                    }
                }
            }
            if (dotsAlpha > 0f) {
                Row(
                    Modifier.fillMaxWidth().graphicsLayer { alpha = dotsAlpha },
                    horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
                ) {
                    events.take(3).forEach { e ->
                        Box(Modifier.size(5.dp).clip(CircleShape).background(eventColor(e.color)))
                    }
                }
            }
        }
    }
}
