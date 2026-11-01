package com.kodalabs.skydule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kodalabs.skydule.data.CalendarEvent
import java.time.format.DateTimeFormatter

/** The event being shown and where its card sat on screen, so the page can grow out of (and back into) it. */
data class OpenedEvent(val event: CalendarEvent, val from: Rect)

private val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMM")
private val shortDayFormat = DateTimeFormatter.ofPattern("d MMM")

private fun whenLine(e: CalendarEvent): String {
    val day = if (e.startDay == e.endDay) {
        e.startDay.format(dayFormat)
    } else {
        "${e.startDay.format(shortDayFormat)} – ${e.endDay.format(shortDayFormat)}"
    }
    return "$day • ${e.timeLabel()}"
}

/** Event details as a full-screen page that grows out of the tapped card (see [TransformSheet]). */
@Composable
fun EventDetailOverlay(
    opened: OpenedEvent,
    progress: State<Float>,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    loadNotes: suspend (Long) -> String?,
    /** True for a single changed occurrence of a series: deleting it must not claim the whole series goes. */
    isModifiedOccurrence: suspend (Long) -> Boolean = { false },
    /** False for events in read-only calendars (subscriptions, holidays, shared view-only): no Edit, no Delete. */
    canModify: Boolean = true,
) {
    val event = opened.event
    // Notes aren't part of the list data; fetch just this event's when its page opens.
    val notes by produceState<String?>(initialValue = null, event.id) { value = loadNotes(event.id) }
    val modifiedOccurrence by produceState(initialValue = false, event.id) { value = isModifiedOccurrence(event.id) }
    var confirmDelete by remember { mutableStateOf(false) }
    // The page takes its tint from the event's calendar colour.
    val page = remember(event.color) { lerp(eventColor(event.color), Color(0xFF0B1026), 0.74f) }

    val paneMode = LocalPaneMode.current
    val inPane = paneMode != PaneMode.NONE
    TransformSheet(
        from = opened.from,
        progress = progress,
        startColor = Glass,
        // In the side panel the card's own frosted glass shows through, the same as when nothing is selected.
        endColor = if (inPane) Color.Transparent else page,
        startCorner = 28.dp,
        scrim = if (inPane) 0f else 0.32f,
        endCorner = if (inPane) 28.dp else 0.dp,
        startFull = paneMode == PaneMode.SLIDE,
    ) { modifier ->
        DetailPage(event, notes, canModify, modifier, onClose, onEdit = onEdit, onDelete = { confirmDelete = true })
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete event?") },
            text = {
                Text(
                    "“${event.title.ifBlank { "(No title)" }}” will be removed from your calendar. " +
                        if (modifiedOccurrence) "Only this changed occurrence is removed; the rest of the series stays."
                        else "If it repeats, the whole series goes.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DetailPage(
    event: CalendarEvent,
    notes: String?,
    canModify: Boolean,
    modifier: Modifier,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = eventColor(event.color)
    Column(modifier.pageInsets()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close", tint = White) }
            Spacer(Modifier.weight(1f))
            if (canModify) {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit", tint = White) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = White) }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 10.dp).size(24.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(accent))
                }
                Spacer(Modifier.width(32.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        event.title.ifBlank { "(No title)" },
                        fontSize = 28.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Normal,
                        color = White,
                    )
                    Text(
                        whenLine(event),
                        fontSize = 15.sp,
                        color = White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            event.location?.let { place ->
                val context = androidx.compose.ui.platform.LocalContext.current
                Box(Modifier.clickable { context.openInMaps(place) }) {
                    DetailRow(Icons.Default.LocationOn) {
                        Column {
                            Text(place, fontSize = 16.sp, color = White)
                            Text("Open in Maps", fontSize = 13.sp, color = White.copy(alpha = 0.6f))
                        }
                    }
                }
            }
            if (event.calendarName.isNotBlank()) {
                DetailRow(Icons.Default.DateRange) {
                    Column {
                        Text(event.calendarName, fontSize = 16.sp, color = White)
                        if (!canModify) {
                            Text("Read-only calendar", fontSize = 13.sp, color = White.copy(alpha = 0.6f))
                        }
                    }
                }
            }
            notes?.let { text ->
                DetailRow(Icons.Default.Menu) { Text(text, fontSize = 16.sp, lineHeight = 22.sp, color = White) }
            }
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = White.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(32.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}
