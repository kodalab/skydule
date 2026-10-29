package com.example.gradule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.example.gradule.data.CalendarEvent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import java.time.format.TextStyle as JTextStyle

private const val PAGES = 2000
private const val CENTER = 1000

/** A swipeable month grid. Tapping a day jumps to it in Schedule. */
@Composable
fun MonthTab(
    selected: LocalDate,
    firstDow: DayOfWeek,
    byDay: Map<LocalDate, List<CalendarEvent>>,
    onPickDay: (LocalDate) -> Unit,
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
    Column(Modifier.fillMaxSize()) {
        CompactHeader(
            title = shown.month.getDisplayName(JTextStyle.FULL, Locale.getDefault()),
            subtitle = "${shown.year}",
            showToday = true,
            onToday = onToday,
            onSettings = onSettings,
        )
        WeekdayLabels(firstDow)
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            MonthGrid(
                ym = monthOf(page),
                firstDow = firstDow,
                today = today,
                selected = selected,
                byDay = byDay,
                onPick = onPickDay,
            )
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
    selected: LocalDate,
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
            .padding(start = 12.dp, end = 12.dp, bottom = LocalBarInset.current + 8.dp)
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
    onClick: () -> Unit,
) {
    BoxWithConstraints(
        modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) White.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 3.dp),
    ) {
        // How many event chips fit under the day number in this cell.
        val maxChips = ((maxHeight - 32.dp) / 16.dp).toInt().coerceIn(0, 4)
        Column(
            Modifier.fillMaxWidth().alpha(if (inMonth) 1f else 0.4f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isToday) White else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    date.dayOfMonth.toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isToday) Ink else White,
                )
            }
            Spacer(Modifier.height(2.dp))
            val shownCount = (if (events.size > maxChips) maxChips - 1 else events.size).coerceAtLeast(0)
            events.take(shownCount).forEach { e ->
                EventChip(e, modifier = Modifier.padding(vertical = 0.5.dp))
            }
            if (events.size > shownCount && maxChips > 0) {
                Text("+${events.size - shownCount}", fontSize = 10.sp, color = White.copy(alpha = 0.8f))
            }
        }
    }
}
