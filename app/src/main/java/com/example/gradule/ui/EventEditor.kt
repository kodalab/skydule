package com.example.gradule.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.text.input.KeyboardType
import com.example.gradule.data.DeviceCalendar
import com.example.gradule.data.EventVisibility
import com.example.gradule.data.NewEvent
import com.example.gradule.data.Repeat
import com.example.gradule.data.reminderLabel
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Where the editor should grow from, and what to prefill. [startMinutes] is minutes after midnight, if a slot was tapped. */
data class EditorRequest(val from: Rect, val date: LocalDate, val startMinutes: Int?, val existing: NewEvent? = null)

private val EditorBackground = Color(0xFF111631)
private val dateFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** New-event editor as a full-screen page that grows out of the + button (or a tapped Week slot). */
@Composable
fun EventEditorOverlay(
    request: EditorRequest,
    progress: State<Float>,
    calendars: List<DeviceCalendar>,
    onCreateLocal: () -> Unit,
    defaultReminder: Int?,
    onClose: () -> Unit,
    onSave: (NewEvent) -> Unit,
) {
    val paneMode = LocalPaneMode.current
    val inPane = paneMode != PaneMode.NONE
    TransformSheet(
        from = request.from,
        progress = progress,
        startColor = White,
        endColor = if (inPane) Color.Transparent else EditorBackground,
        startCorner = 16.dp,
        scrim = if (inPane) 0f else 0.32f,
        endCorner = if (inPane) 28.dp else 0.dp,
        startFull = paneMode == PaneMode.SLIDE,
    ) { modifier ->
        EditorPage(modifier, request, calendars, onCreateLocal, defaultReminder, onClose, onSave)
    }
}

@Composable
private fun EditorPage(
    modifier: Modifier,
    request: EditorRequest,
    calendars: List<DeviceCalendar>,
    onCreateLocal: () -> Unit,
    defaultReminder: Int?,
    onClose: () -> Unit,
    onSave: (NewEvent) -> Unit,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val existing = request.existing
    val zone = remember { ZoneId.systemDefault() }

    val writable = remember(calendars) { calendars.filter { it.writable } }
    var calendarId by remember { mutableStateOf(existing?.calendarId) }
    val calendar = writable.firstOrNull { it.id == calendarId }
        ?: writable.firstOrNull { !it.isLocal }
        ?: writable.firstOrNull()

    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var allDay by remember { mutableStateOf(existing?.allDay ?: false) }
    val initialStart = remember {
        if (existing != null) {
            if (existing.allDay) existing.startDay.atTime(9, 0)
            else java.time.Instant.ofEpochMilli(existing.startMillis).atZone(zone).toLocalDateTime()
        } else {
            val minutes = request.startMinutes
                ?: if (request.date == LocalDate.now()) (LocalTime.now().hour + 1).coerceAtMost(23) * 60 else 9 * 60
            request.date.atTime(minutes / 60, minutes % 60)
        }
    }
    val initialEnd = remember {
        if (existing != null) {
            if (existing.allDay) existing.endDay.atTime(9, 0)
            else java.time.Instant.ofEpochMilli(existing.endMillis).atZone(zone).toLocalDateTime()
        } else {
            initialStart.plusHours(1)
        }
    }
    var start by remember { mutableStateOf(initialStart) }
    var end by remember { mutableStateOf(initialEnd) }
    var repeat by remember { mutableStateOf(existing?.repeat ?: Repeat.NONE) }
    var location by remember { mutableStateOf(existing?.location.orEmpty()) }
    var suggestions by remember { mutableStateOf<List<com.example.gradule.data.PlaceSuggestion>>(emptyList()) }
    var pickedLocation by remember { mutableStateOf("") }
    // Debounced look-up as the user types.
    LaunchedEffect(location) {
        val q = location.trim()
        if (q.length < 3 || q == pickedLocation) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(350)
        suggestions = com.example.gradule.data.PlaceSearch.search(q)
    }
    val reminders = remember {
        mutableStateListOf<Int>().apply {
            if (existing != null) addAll(existing.reminders) else defaultReminder?.let { add(it) }
        }
    }
    var customOpen by remember { mutableStateOf(false) }
    val addReminder: (Int) -> Unit = { m -> if (m !in reminders) reminders.add(m) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var visibility by remember { mutableStateOf(existing?.visibility ?: EventVisibility.DEFAULT) }
    var busy by remember { mutableStateOf(existing?.busy ?: true) }

    // Like Google: focus the title once the page has finished opening.
    LaunchedEffect(Unit) {
        delay(OPEN_MS.toLong())
        runCatching { focus.requestFocus() }
    }

    /** Moving the start keeps the event's length. */
    fun moveStart(new: LocalDateTime) {
        val length = Duration.between(start, end)
        start = new
        end = new.plus(length)
    }

    /** An end before the start snaps to half an hour after it. */
    fun moveEnd(new: LocalDateTime) {
        end = if (new.isBefore(start)) start.plusMinutes(30) else new
    }

    fun pickDate(current: LocalDate, onPicked: (LocalDate) -> Unit) {
        DatePickerDialog(
            context,
            { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d)) },
            current.year, current.monthValue - 1, current.dayOfMonth,
        ).show()
    }

    fun pickTime(current: LocalTime, onPicked: (LocalTime) -> Unit) {
        TimePickerDialog(
            context,
            { _, h, m -> onPicked(LocalTime.of(h, m)) },
            current.hour, current.minute, DateFormat.is24HourFormat(context),
        ).show()
    }

    Column(modifier.pageInsets().imePadding()) {
        // Top bar: close + Save
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                keyboard?.hide()
                onClose()
            }) { Icon(Icons.Default.Close, contentDescription = "Close", tint = White) }
            Spacer(Modifier.weight(1f))
            Button(
                enabled = title.isNotBlank() && calendar != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = White,
                    contentColor = Ink,
                    disabledContainerColor = White.copy(alpha = 0.2f),
                    disabledContentColor = White.copy(alpha = 0.5f),
                ),
                onClick = {
                    val cal = calendar ?: return@Button
                    val zone = ZoneId.systemDefault()
                    val endDay = if (end.toLocalDate().isBefore(start.toLocalDate())) start.toLocalDate() else end.toLocalDate()
                    keyboard?.hide()
                    onSave(
                        NewEvent(
                            id = existing?.id,
                            calendarId = cal.id,
                            title = title.trim(),
                            allDay = allDay,
                            startMillis = start.atZone(zone).toInstant().toEpochMilli(),
                            endMillis = end.atZone(zone).toInstant().toEpochMilli(),
                            startDay = start.toLocalDate(),
                            endDay = endDay,
                            repeat = repeat,
                            customRule = if (repeat == Repeat.CUSTOM) existing?.customRule else null,
                            location = location.trim().ifBlank { null },
                            description = description.trim().ifBlank { null },
                            reminders = reminders.sorted(),
                            busy = busy,
                            visibility = visibility,
                        ),
                    )
                },
            ) { Text("Save") }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            PlainField(
                value = title,
                onChange = { title = it },
                hint = "Add title",
                fontSize = 28.sp,
                singleLine = false,
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp)
                    .focusRequester(focus),
            )

            // Calendar / account
            if (calendar == null) {
                FormRow(icon = { Dot(Color.Gray) }) {
                    Column(Modifier.weight(1f)) {
                        Text("No writable calendar found", fontSize = 16.sp, color = White)
                        Text(
                            "Add a Google account in system settings, or keep events on this phone only.",
                            fontSize = 12.sp,
                            color = White.copy(alpha = 0.65f),
                        )
                        TextButton(onClick = onCreateLocal) { Text("Create a calendar on this phone") }
                    }
                }
            } else {
                var open by remember { mutableStateOf(false) }
                Box {
                    FormRow(icon = { Dot(eventColor(calendar.color)) }, modifier = Modifier.clickable { open = true }) {
                        Column(Modifier.weight(1f)) {
                            Text(calendar.name, fontSize = 16.sp, color = White)
                            Text(
                                if (calendar.isLocal) "On this phone" else calendar.account,
                                fontSize = 12.sp,
                                color = White.copy(alpha = 0.65f),
                            )
                        }
                        Text("▾", color = White.copy(alpha = 0.7f))
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        writable.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(if (c.isLocal) "${c.name} · On this phone" else "${c.name} · ${c.account}") },
                                leadingIcon = { Dot(eventColor(c.color)) },
                                onClick = {
                                    calendarId = c.id
                                    open = false
                                },
                            )
                        }
                    }
                }
            }

            // All-day
            FormRow(icon = { ClockIcon(White.copy(alpha = 0.7f)) }) {
                Text("All-day", fontSize = 16.sp, color = White, modifier = Modifier.weight(1f))
                Switch(checked = allDay, onCheckedChange = { allDay = it })
            }

            // Start / end
            FormRow(icon = null) {
                Text(
                    start.format(dateFormat),
                    fontSize = 16.sp,
                    color = White,
                    modifier = Modifier.weight(1f).clickable {
                        pickDate(start.toLocalDate()) { d -> moveStart(d.atTime(start.toLocalTime())) }
                    }.padding(vertical = 6.dp),
                )
                if (!allDay) {
                    Text(
                        start.format(timeFormat),
                        fontSize = 16.sp,
                        color = White,
                        modifier = Modifier.clickable {
                            pickTime(start.toLocalTime()) { t -> moveStart(start.toLocalDate().atTime(t)) }
                        }.padding(vertical = 6.dp, horizontal = 4.dp),
                    )
                }
            }
            FormRow(icon = null) {
                Text(
                    end.format(dateFormat),
                    fontSize = 16.sp,
                    color = White,
                    modifier = Modifier.weight(1f).clickable {
                        pickDate(end.toLocalDate()) { d -> moveEnd(d.atTime(end.toLocalTime())) }
                    }.padding(vertical = 6.dp),
                )
                if (!allDay) {
                    Text(
                        end.format(timeFormat),
                        fontSize = 16.sp,
                        color = White,
                        modifier = Modifier.clickable {
                            pickTime(end.toLocalTime()) { t -> moveEnd(end.toLocalDate().atTime(t)) }
                        }.padding(vertical = 6.dp, horizontal = 4.dp),
                    )
                }
            }

            // Repeat
            PickerRow(
                icon = { Icon(Icons.Default.Refresh, null, tint = White.copy(alpha = 0.7f)) },
                text = repeat.label,
                active = repeat != Repeat.NONE,
                options = Repeat.entries
                    .filter { it != Repeat.CUSTOM || existing?.repeat == Repeat.CUSTOM }
                    .map { it.label to it },
                onPick = { repeat = it },
            )

            // Location
            FormRow(icon = { Icon(Icons.Default.LocationOn, null, tint = White.copy(alpha = 0.7f)) }) {
                PlainField(
                    value = location,
                    onChange = { location = it },
                    hint = "Add location",
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                )
                if (location.isNotBlank()) {
                    Text(
                        "Maps",
                        color = White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Glass)
                            .clickable { context.openInMaps(location.trim()) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            // Live suggestions while typing; picking one fills the field.
            suggestions.forEach { s ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            pickedLocation = s.text
                            location = s.text
                            suggestions = emptyList()
                        }
                        .padding(start = 80.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    Text(s.title, color = White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (s.subtitle.isNotBlank()) {
                        Text(s.subtitle, color = White.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Notifications: one line per reminder (removable), then an add row, as in Google Calendar.
            reminders.sorted().forEachIndexed { i, m ->
                FormRow(icon = { if (i == 0) Icon(Icons.Default.Notifications, null, tint = White.copy(alpha = 0.7f)) }) {
                    Text(
                        reminderLabel(m),
                        color = White,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove notification",
                        tint = White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp).clickable { reminders.remove(m) },
                    )
                }
            }
            PickerRow(
                icon = {
                    Icon(
                        if (reminders.isEmpty()) Icons.Default.Notifications else Icons.Default.Add,
                        null,
                        tint = White.copy(alpha = 0.7f),
                    )
                },
                text = if (reminders.isEmpty()) "Add notification" else "Add another notification",
                active = reminders.isNotEmpty(),
                options = reminderOptions,
                onPick = { m -> if (m == CUSTOM_REMINDER) customOpen = true else addReminder(m) },
            )
            if (customOpen) {
                CustomReminderDialog(
                    onAdd = { m ->
                        addReminder(m)
                        customOpen = false
                    },
                    onDismiss = { customOpen = false },
                )
            }

            // Description
            FormRow(icon = { Icon(Icons.Default.Menu, null, tint = White.copy(alpha = 0.7f)) }) {
                PlainField(
                    value = description,
                    onChange = { description = it },
                    hint = "Add description",
                    singleLine = false,
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                )
            }

            // Visibility
            PickerRow(
                icon = { Icon(Icons.Default.Lock, null, tint = White.copy(alpha = 0.7f)) },
                text = visibility.label,
                active = visibility != EventVisibility.DEFAULT,
                options = EventVisibility.entries.map { it.label to it },
                onPick = { visibility = it },
            )

            // Busy / free
            PickerRow(
                icon = { BriefcaseIcon(White.copy(alpha = 0.7f)) },
                text = if (busy) "Busy" else "Free",
                active = true,
                options = listOf("Busy" to true, "Free" to false),
                onPick = { busy = it },
            )
        }
    }
}
