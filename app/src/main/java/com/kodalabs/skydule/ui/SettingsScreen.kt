package com.kodalabs.skydule.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
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
import com.kodalabs.skydule.data.DefaultReminder
import com.kodalabs.skydule.data.SnoozeLength
import com.kodalabs.skydule.data.TemperatureUnit
import com.kodalabs.skydule.data.ThemeMode
import com.kodalabs.skydule.data.WeekStart

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
    showSideOption: Boolean,
    detailSide: com.kodalabs.skydule.data.DetailSide,
    onDetailSide: (com.kodalabs.skydule.data.DetailSide) -> Unit,
    iconChoice: com.kodalabs.skydule.icons.IconChoice,
    onOpenIconPicker: () -> Unit,
    onOpenCalendarSettings: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onClose: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }

    // On wide screens the page stays a readable column in the middle instead of stretching edge to edge.
    Box(modifier.fillMaxSize().background(SettingsBackground), contentAlignment = Alignment.TopCenter) {
    Column(
        Modifier
            .widthIn(max = 640.dp)
            .fillMaxSize()
            .statusBarsPadding(),
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
            Section("Calendars") {
                SettingRow(
                    title = "Calendar settings",
                    onClick = onOpenCalendarSettings,
                    trailingArrow = true,
                )
            }
            Section("Appearance") {
                PickRow("Theme", mode, ThemeMode.entries, { it.label }, onMode)
                PickRow("Week starts on", weekStart, WeekStart.entries, { it.label }, onWeekStart)
                SettingRow(
                    title = "App icon",
                    value = iconChoice.label,
                    onClick = onOpenIconPicker,
                    trailingArrow = true,
                )
                // Only on foldables and tablets: the devices that can show the event panel beside the list.
                if (showSideOption) {
                    PickRow(
                        "Event details",
                        detailSide,
                        com.kodalabs.skydule.data.DetailSide.entries,
                        { it.label },
                        onDetailSide,
                        subtitle = "Which side they open on when the screen is wide",
                    )
                }
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
                var crashReports by remember { mutableStateOf(com.kodalabs.skydule.data.Diag.enabled(context)) }
                ToggleRow(
                    title = "Send crash reports",
                    subtitle = "Anonymous, to fix bugs. Never your calendar",
                    checked = crashReports,
                    onChange = {
                        crashReports = it
                        com.kodalabs.skydule.data.Diag.setEnabled(context, it)
                    },
                )
                SettingRow(
                    title = "Send feedback",
                    subtitle = "Email us, with your app and phone details",
                    onClick = { context.sendFeedback() },
                    trailingArrow = true,
                )
                SettingRow(title = "Data sources", subtitle = "Open-Meteo weather · Google and OpenStreetMap places")
                SettingRow(
                    title = "Privacy policy",
                    subtitle = "What stays on your phone",
                    onClick = onOpenPrivacy,
                    trailingArrow = true,
                )
            }
            Spacer(Modifier.size(24.dp))
            NavBarSpacer()
        }
    }
    BottomEdgeFade(Modifier.align(Alignment.BottomCenter))
    }
}

/** Space the height of the navigation bar, so the last row can scroll clear of it while the page runs edge to edge. */
@Composable
internal fun NavBarSpacer() {
    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
}

/** Lets a scrolling page run under the gesture handle: content fades into the page colour instead of ending in a flat band. */
@Composable
internal fun BottomEdgeFade(modifier: Modifier = Modifier) {
    val navHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(navHeight + 20.dp)
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color.Transparent, SettingsBackground.copy(alpha = 0.92f)),
                ),
            ),
    )
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

/** Opens the user's email app with a draft to the support address, pre-filled with app and phone details to help us reproduce problems. */
private fun android.content.Context.sendFeedback() {
    val info = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
    val build = info?.let { androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(it) }
    val body = "\n\n\n--- Please write above this line ---\n" +
        "Skydule ${info?.versionName.orEmpty()} (build ${build ?: "?"})\n" +
        "Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})\n" +
        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n"
    val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
        data = android.net.Uri.parse("mailto:")
        putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf("getskydule@gmail.com"))
        putExtra(android.content.Intent.EXTRA_SUBJECT, "Skydule feedback")
        putExtra(android.content.Intent.EXTRA_TEXT, body)
    }
    try {
        startActivity(intent)
    } catch (_: android.content.ActivityNotFoundException) {
        android.widget.Toast.makeText(this, "No email app found. Write to getskydule@gmail.com", android.widget.Toast.LENGTH_LONG).show()
    }
}
