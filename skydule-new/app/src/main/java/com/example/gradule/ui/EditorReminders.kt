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
import com.example.gradule.data.ALL_DAY_DEFAULT_REMINDER
import com.example.gradule.data.allDayReminderLabel
import com.example.gradule.data.allDayReminderMinutes
import com.example.gradule.data.reminderLabel
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// A menu sentinel only (never stored). -1 is the provider's "calendar default", which is a real reminder value.
internal const val CUSTOM_REMINDER = Int.MIN_VALUE
internal const val MAX_REMINDER_MINUTES = 40320 // four weeks, Google's ceiling for custom notifications

internal val reminderOptions: List<Pair<String, Int>> = listOf(
    "At time of event" to 0,
    "5 minutes before" to 5,
    "10 minutes before" to 10,
    "15 minutes before" to 15,
    "30 minutes before" to 30,
    "1 hour before" to 60,
    "1 day before" to 1440,
    "Custom…" to CUSTOM_REMINDER,
)

/** Presets for an all-day event: the evening before, and mornings 1 day, 2 days and a week before. Offsets count back from local midnight. */
internal fun allDayReminderOptions(): List<Pair<String, Int>> =
    listOf(180, ALL_DAY_DEFAULT_REMINDER, 2340, 9540).map { allDayReminderLabel(it) to it } +
        ("Custom…" to CUSTOM_REMINDER)

/** Days before plus a time of day, for an all-day event ("2 days before at 6:00 PM"). */
@Composable
internal fun CustomAllDayReminderDialog(onAdd: (Int) -> Unit, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var amount by remember { mutableStateOf("1") }
    var time by remember { mutableStateOf(LocalTime.of(9, 0)) }
    val days = amount.toIntOrNull()
    val minutes = days?.let { allDayReminderMinutes(it, time.hour * 60 + time.minute) }
    val valid = minutes != null && minutes in 0..MAX_REMINDER_MINUTES

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom notification") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter(Char::isDigit).take(2) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(90.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("days before, at", style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, h, m -> time = LocalTime.of(h, m) },
                            time.hour,
                            time.minute,
                            android.text.format.DateFormat.is24HourFormat(context),
                        ).show()
                    },
                ) { Text(time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))) }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (valid) allDayReminderLabel(minutes!!) else "Choose 1 to 28 days before (or 0 days at 12:00 AM)",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(minutes!!) }, enabled = valid) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Number plus unit, like Google's "Custom…" notification dialog. */
@Composable
internal fun CustomReminderDialog(onAdd: (Int) -> Unit, onDismiss: () -> Unit) {
    val units = listOf("minutes" to 1, "hours" to 60, "days" to 1440, "weeks" to 10080)
    var amount by remember { mutableStateOf("10") }
    var unit by remember { mutableIntStateOf(0) }
    val minutes = amount.toIntOrNull()?.times(units[unit].second)
    val valid = minutes != null && minutes in 0..MAX_REMINDER_MINUTES

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom notification") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter(Char::isDigit).take(5) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(110.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("before", style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(8.dp))
                units.forEachIndexed { i, (label, _) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { unit = i }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = unit == i, onClick = { unit = i })
                        Text(label)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (valid) reminderLabel(minutes!!) else "Choose up to 4 weeks",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(minutes!!) }, enabled = valid) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
