package com.example.gradule.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import kotlin.math.roundToInt
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gradule.data.DefaultReminder
import com.example.gradule.data.SnoozeLength
import com.example.gradule.data.TemperatureUnit
import com.example.gradule.data.ThemeMode
import com.example.gradule.data.WeekStart

internal val SettingsBackground = Color(0xFF111631)

/** Full-page settings, in the spirit of Gradient Weather: a back arrow, then grouped rounded cards. */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    mode: ThemeMode,
    onMode: (ThemeMode) -> Unit,
    weekStart: WeekStart,
    onWeekStart: (WeekStart) -> Unit,
    showWeather: Boolean,
    onShowWeather: (Boolean) -> Unit,
    defaultReminder: DefaultReminder,
    onDefaultReminder: (DefaultReminder) -> Unit,
    snoozeLength: SnoozeLength,
    onSnoozeLength: (SnoozeLength) -> Unit,
    temperature: TemperatureUnit,
    onTemperature: (TemperatureUnit) -> Unit,
    hasLocalCalendar: Boolean,
    onCreateLocal: () -> Unit,
    iconChoice: com.example.gradule.icons.IconChoice,
    onOpenIconPicker: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onClose: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }

    Column(
        modifier
            .fillMaxSize()
            .background(SettingsBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = White) }
            Text(
                "Settings",
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-0.5).sp,
                color = White,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(24.dp),
        ) {
            Section("Appearance") {
                PickRow("Theme", mode, ThemeMode.entries, { it.label }, onMode)
                PickRow("Week starts on", weekStart, WeekStart.entries, { it.label }, onWeekStart)
                SettingRow(
                    title = "App icon",
                    value = iconChoice.label,
                    onClick = onOpenIconPicker,
                    trailingArrow = true,
                )
            }
            Section("Schedule") {
                ToggleRow(
                    title = "Show weather",
                    subtitle = "Beside today's date",
                    checked = showWeather,
                    onChange = onShowWeather,
                )
                PickRow(
                    "Default alert",
                    defaultReminder,
                    DefaultReminder.entries,
                    { it.label },
                    onDefaultReminder,
                    subtitle = "Added to every new event",
                )
            }
            Section("Weather") {
                PickRow("Temperature", temperature, TemperatureUnit.entries, { it.label }, onTemperature)
            }
            Section("Reminders") {
                PickRow("Snooze for", snoozeLength, SnoozeLength.entries, { it.label }, onSnoozeLength)
                SettingRow(
                    title = "Notification style",
                    subtitle = "Sound, banners and lock screen",
                    onClick = { context.openSystemSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS) },
                    trailingArrow = true,
                )
                SettingRow(
                    title = "Keep reminders on time",
                    subtitle = "Battery use for Skydule",
                    onClick = { context.openSystemSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS) },
                    trailingArrow = true,
                )
            }
            if (!hasLocalCalendar) {
                Section("Calendars") {
                    SettingRow(
                        title = "Create a calendar on this phone",
                        subtitle = "Events stay on this phone and never sync",
                        onClick = onCreateLocal,
                        trailingArrow = true,
                    )
                }
            }
            Section("About") {
                SettingRow(title = "Version", value = version)
                SettingRow(title = "Data sources", subtitle = "Open-Meteo weather · OpenStreetMap places")
                SettingRow(
                    title = "Privacy policy",
                    subtitle = "What stays on your phone",
                    onClick = onOpenPrivacy,
                    trailingArrow = true,
                )
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            color = White.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Glass),
            content = content,
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailingArrow: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = White)
            subtitle?.let { Text(it, fontSize = 13.sp, color = White.copy(alpha = 0.6f)) }
        }
        value?.let { Text(it, fontSize = 14.sp, color = White.copy(alpha = 0.75f), modifier = Modifier.padding(start = 12.dp)) }
        if (trailingArrow) {
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = White.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        trailing?.invoke()
    }
}

/** A row that shows its current choice and opens a list of options. */
@Composable
private fun <T> PickRow(
    title: String,
    selected: T,
    options: List<T>,
    label: (T) -> String,
    onPick: (T) -> Unit,
    subtitle: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    SettingRow(title = title, subtitle = subtitle, value = label(selected), onClick = { open = true }, trailingArrow = true)
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onPick(option)
                                    open = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == selected, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(label(option))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(
        title = title,
        subtitle = subtitle,
        onClick = { onChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

private fun Context.openSystemSettings(action: String) {
    val intent = if (action == Settings.ACTION_APP_NOTIFICATION_SETTINGS) {
        Intent(action).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    } else {
        Intent(action, Uri.fromParts("package", packageName, null))
    }
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}
