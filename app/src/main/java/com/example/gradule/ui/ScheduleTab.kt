package com.example.gradule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gradule.data.CalendarEvent
import com.example.gradule.data.DateWindow
import com.example.gradule.data.WeatherState
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.time.format.TextStyle as JTextStyle

private sealed interface SchedRow {
    data class Month(val ym: YearMonth) : SchedRow
    data class Day(val date: LocalDate) : SchedRow
}

/** Days that have events, plus today and the selected day, with a header wherever the month changes. */
private fun buildRows(dates: Set<LocalDate>): List<SchedRow> {
    val out = ArrayList<SchedRow>()
    var last: YearMonth? = null
    for (d in dates.sorted()) {
        val ym = YearMonth.from(d)
        if (ym != last) {
            out += SchedRow.Month(ym)
            last = ym
        }
        out += SchedRow.Day(d)
    }
    return out
}

/**
 * Google-Calendar-style schedule: a hero that is always about today (date, weather, how busy it is), then one
 * continuous list of the days that have something on. Empty days are skipped, except today and the selected day.
 */
@Composable
fun ScheduleTab(
    selected: LocalDate,
    todayTick: Int,
    byDay: Map<LocalDate, List<CalendarEvent>>,
    window: DateWindow,
    weather: WeatherState,
    showWeather: Boolean,
    onWeather: () -> Unit,
    onEvent: EventOpener,
    onToday: () -> Unit,
    onSettings: () -> Unit,
    onNeedForward: () -> Unit,
    onNeedBackward: () -> Unit,
) {
    val today = LocalDate.now()
    val rows = remember(byDay, selected) { buildRows(byDay.keys + today + selected) }
    val listState = rememberLazyListState()

    var scrolled by remember { mutableStateOf(false) }
    var lastTarget by remember { mutableStateOf<LocalDate?>(null) }

    // "Today" pressed: animate to today's row, even if today is already the selected day.
    // Declared before the selection effect so it can claim the scroll first.
    var handledTick by remember { mutableIntStateOf(todayTick) }
    LaunchedEffect(todayTick) {
        if (todayTick != handledTick) {
            handledTick = todayTick
            val idx = rows.indexOfFirst { it is SchedRow.Day && it.date == today }
            if (idx >= 0) {
                lastTarget = today
                scrolled = true
                listState.animateScrollToItem(idx + 1) // +1 for the leading sentinel item
            }
        }
    }

    // Once the user drags the list themselves we stop re-pinning it to the selected day.
    var userMoved by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect {
            if (it is androidx.compose.foundation.interaction.DragInteraction.Start) userMoved = true
        }
    }
    LaunchedEffect(selected) { userMoved = false }

    // Show the selected day (today on launch) at the top. Events load after first composition and add rows above
    // it, so keep re-pinning on every rows change until the user takes over; this is what keeps launch on today.
    LaunchedEffect(selected, rows) {
        if (lastTarget != selected || !userMoved) {
            val idx = rows.indexOfFirst { it is SchedRow.Day && it.date == selected }
            if (idx >= 0) {
                // Land on the month header when it directly precedes the day, so the list reads naturally.
                listState.scrollToItem(idx + 1)
                lastTarget = selected
                scrolled = true
            }
        }
    }

    // Sentinel items at both ends tell us when to load more days.
    val atEnd by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.key == "end" } }
    }
    val atStart by remember {
        derivedStateOf { scrolled && listState.layoutInfo.visibleItemsInfo.any { it.key == "start" } }
    }
    LaunchedEffect(atEnd, window) {
        if (atEnd) {
            delay(200)
            onNeedForward()
        }
    }
    LaunchedEffect(atStart, window) {
        if (atStart) {
            delay(200)
            onNeedBackward()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Hero(
            today = today,
            count = byDay[today]?.size ?: 0,
            weather = weather,
            showWeather = showWeather,
            onWeather = onWeather,
            onToday = onToday,
            onSettings = onSettings,
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                // Cards fade out softly under the header instead of being cut off.
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to Color.Transparent, 1f to Color.Black, endY = 22.dp.toPx(),
                        ),
                        size = androidx.compose.ui.geometry.Size(size.width, 22.dp.toPx()),
                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                    )
                },
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = LocalBarInset.current + 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "start") { Spacer(Modifier.height(1.dp)) }
            items(
                rows,
                key = {
                    when (it) {
                        is SchedRow.Month -> "m${it.ym}"
                        is SchedRow.Day -> it.date.toEpochDay()
                    }
                },
            ) { row ->
                when (row) {
                    is SchedRow.Month -> MonthLabel(row.ym)
                    is SchedRow.Day -> DayRow(
                        date = row.date,
                        today = today,
                        selected = selected,
                        events = byDay[row.date].orEmpty(),
                        onEvent = onEvent,
                    )
                }
            }
            item(key = "end") {
                // byDay starts empty while the first load runs, so only call it empty once it has had a moment.
                var settled by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { delay(800); settled = true }
                val nothingAtAll = settled && byDay.isEmpty()
                if (nothingAtAll) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Tap + to add an event", color = White.copy(alpha = 0.8f), fontSize = 22.sp, fontWeight = FontWeight.Light)
                    }
                } else {
                    Text(
                        "That's everything for now",
                        color = White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    )
                }
            }
        }
    }
}

/** Always today: labelled as such, with the weather right beside the date. */
@Composable
private fun Hero(
    today: LocalDate,
    count: Int,
    weather: WeatherState,
    showWeather: Boolean,
    onWeather: () -> Unit,
    onToday: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "TODAY",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                color = White.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f),
            )
            TodayPill(onToday, label = "Go to today")
            Spacer(Modifier.width(4.dp))
            SettingsButton(onSettings)
        }
        // Explicit line height so descenders (the "y" in Thursday) aren't clipped, plus room before the pill.
        Text(
            today.dayOfWeek.getDisplayName(JTextStyle.FULL, Locale.getDefault()),
            style = MaterialTheme.typography.displayLarge.copy(lineHeight = 72.sp),
            color = White,
            maxLines = 1,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        if (showWeather) {
            WeatherPill(
                weather,
                onWeather,
                date = today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
            )
        } else {
            Text(
                today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                fontSize = 18.sp,
                color = White.copy(alpha = 0.85f),
                maxLines = 1,
            )
        }
        Text(
            when (count) {
                0 -> "Nothing planned today"
                1 -> "1 event today"
                else -> "$count events today"
            },
            fontSize = 14.sp,
            color = White.copy(alpha = 0.65f),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun MonthLabel(ym: YearMonth) {
    Text(
        "${ym.month.getDisplayName(JTextStyle.FULL, Locale.getDefault())} ${ym.year}",
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = White.copy(alpha = 0.8f),
        modifier = Modifier.padding(start = 4.dp, top = 12.dp),
    )
}

@Composable
private fun DayRow(
    date: LocalDate,
    today: LocalDate,
    selected: LocalDate,
    events: List<CalendarEvent>,
    onEvent: EventOpener,
) {
    val isToday = date == today
    val isSelected = date == selected
    Column(Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.width(52.dp).padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfWeek.getDisplayName(JTextStyle.SHORT, Locale.getDefault()),
                fontSize = 12.sp,
                color = White.copy(alpha = 0.75f),
            )
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isToday) White else Color.Transparent)
                    .then(if (isSelected && !isToday) Modifier.border(1.5.dp, White, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    date.dayOfMonth.toString(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isToday) Ink else White,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Today's empty state is already said by the header ("Nothing planned today").
            if (events.isEmpty() && !isToday) {
                Text(
                    "Nothing planned",
                    fontSize = 15.sp,
                    color = White.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 18.dp),
                )
            } else {
                events.forEach { e -> EventCard(e, onClick = { bounds -> onEvent(e, bounds) }) }
            }
        }
    }
    // Marks where this day ends, so one day's events don't run into the next.
        DayDivider(Modifier.padding(top = 12.dp))
    }
}

/** A thin rounded line between days. */
@Composable
private fun DayDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.24f)),
    )
}
