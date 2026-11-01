package com.kodalabs.skydule.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kodalabs.skydule.data.CalendarPrefs
import com.kodalabs.skydule.data.DeviceCalendar

/** 24 swatches, 6 across: warm to cool, then neutrals. */
internal val CalendarPalette = listOf(
    0xE91E63, 0xEC407A, 0xF44336, 0xEF6C57, 0xE53935, 0xFB8C00,
    0xFF9800, 0xFFB300, 0xFDD835, 0xC0CA33, 0x7CB342, 0x26A65B,
    0x00A98F, 0x2E9E6B, 0x26C6DA, 0x1E88E5, 0x3F7BE0, 0x5C6BC0,
    0x9575CD, 0xAB47BC, 0x8E24AA, 0x8D6E63, 0x9E9E9E, 0xC2A27C,
).map { it or (0xFF shl 24) }

private val DialogSurface = Color(0xFF2A2F4A)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
/** "Calendar settings": every calendar on the phone, grouped by account, with a show/hide switch and a colour. */
@Composable
fun CalendarSettingsScreen(
    calendars: List<DeviceCalendar>,
    prefs: CalendarPrefs,
    recentColors: List<Int>,
    onHidden: (Long, Boolean) -> Unit,
    onColor: (Long, Int?) -> Unit,
    onRecentColor: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf<DeviceCalendar?>(null) }
    var custom by remember { mutableStateOf<DeviceCalendar?>(null) }
    val groups = remember(calendars) {
        calendars.groupBy { it.account to it.accountType }.entries.sortedWith(
            compareBy({ it.value.first().isLocal }, { it.key.first.lowercase() }),
        )
    }

    Box(modifier.fillMaxSize().background(SettingsBackground), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = White) }
                Text("Calendar settings", fontSize = 28.sp, fontWeight = FontWeight.Light, color = White, modifier = Modifier.padding(start = 4.dp))
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    "Show, hide and recolour your calendars. This only changes Skydule, not your other apps, and hidden calendars stop their reminders here.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = White.copy(alpha = 0.65f),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                if (calendars.isEmpty()) {
                    Text("No calendars found on this phone yet.", color = White.copy(alpha = 0.7f), fontSize = 15.sp, modifier = Modifier.padding(8.dp))
                }
                for ((key, list) in groups) {
                    Column {
                        Text(
                            if (list.first().isLocal) "On this phone" else key.first,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = White.copy(alpha = 0.75f),
                            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                        )
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Glass)) {
                            for (cal in list) {
                                val hidden = prefs.isHidden(cal.id)
                                val shown = prefs.colorFor(cal.id, cal.color)
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .clickable { picking = cal },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Box(Modifier.size(26.dp).clip(CircleShape).background(eventColor(shown).copy(alpha = if (hidden) 0.45f else 1f)))
                                    }
                                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                        Text(
                                            cal.name,
                                            fontSize = 16.sp,
                                            color = White.copy(alpha = if (hidden) 0.55f else 1f),
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        )
                                        if (!cal.isLocal && cal.account.isNotBlank() && cal.account != cal.name) {
                                            Text(cal.account, fontSize = 12.sp, color = White.copy(alpha = 0.55f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        }
                                    }
                                    Switch(
                                        checked = !hidden,
                                        onCheckedChange = { onHidden(cal.id, !it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = White,
                                            checkedTrackColor = Color(0xFF7FB6FF),
                                            uncheckedThumbColor = White.copy(alpha = 0.8f),
                                            uncheckedTrackColor = White.copy(alpha = 0.22f),
                                            uncheckedBorderColor = Color.Transparent,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                NavBarSpacer()
            }
        }
        BottomEdgeFade(Modifier.align(Alignment.BottomCenter))
    }

    picking?.let { cal ->
        val current = prefs.colorFor(cal.id, cal.color) or (0xFF shl 24)
        Dialog(onDismissRequest = { picking = null }) {
            Column(Modifier.clip(RoundedCornerShape(28.dp)).background(DialogSurface).padding(20.dp)) {
                Text("Colour in Skydule", fontSize = 18.sp, color = White)
                Text(cal.name, fontSize = 13.sp, color = White.copy(alpha = 0.6f), maxLines = 1)
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    maxItemsInEachRow = 6,
                ) {
                    for (c in CalendarPalette) {
                        val selected = c == current
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable { onColor(cal.id, c); picking = null },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .then(if (selected) Modifier.border(2.dp, White, CircleShape) else Modifier),
                            )
                        }
                    }
                    // Colour wheel: opens the custom picker.
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).clickable { custom = cal; picking = null },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.sweepGradient(
                                        listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
                                    ),
                                ),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { onColor(cal.id, null); picking = null }, enabled = prefs.colors.containsKey(cal.id)) {
                        Text("Use calendar colour", color = if (prefs.colors.containsKey(cal.id)) Color(0xFF7FB6FF) else White.copy(alpha = 0.3f))
                    }
                    TextButton(onClick = { picking = null }) { Text("Cancel", color = White.copy(alpha = 0.8f)) }
                }
            }
        }
    }

    custom?.let { cal ->
        CustomColorDialog(
            start = prefs.colorFor(cal.id, cal.color) or (0xFF shl 24),
            title = cal.name,
            recent = recentColors,
            onDone = { c -> onColor(cal.id, c); onRecentColor(c); custom = null },
            onCancel = { custom = null },
        )
    }
}

/** A small colour picker: saturation/brightness square, hue slider, hex field and recently used colours. */
@Composable
private fun CustomColorDialog(
    start: Int,
    title: String,
    recent: List<Int>,
    onDone: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    val hsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(start, it) }
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    var hexText by remember { mutableStateOf(hexOf(start)) }
    var hexError by remember { mutableStateOf(false) }

    fun current(): Int = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)) or (0xFF shl 24)
    fun sync() { hexText = hexOf(current()); hexError = false }

    Dialog(onDismissRequest = onCancel) {
        Column(Modifier.clip(RoundedCornerShape(28.dp)).background(DialogSurface).padding(20.dp)) {
            Text("Custom colour", fontSize = 18.sp, color = White)
            Text(title, fontSize = 13.sp, color = White.copy(alpha = 0.6f), maxLines = 1)
            Spacer(Modifier.height(14.dp))

            // Saturation (left to right) and brightness (bottom to top) for the chosen hue.
            var box by remember { mutableStateOf(IntSize.Zero) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .onSizeChanged { box = it }
                    .pointerInput(Unit) {
                        fun set(p: Offset) {
                            if (box.width == 0 || box.height == 0) return
                            sat = (p.x / box.width).coerceIn(0f, 1f)
                            value = 1f - (p.y / box.height).coerceIn(0f, 1f)
                            sync()
                        }
                        detectTapGestures { set(it) }
                    }
                    .pointerInput(Unit) {
                        fun set(p: Offset) {
                            if (box.width == 0 || box.height == 0) return
                            sat = (p.x / box.width).coerceIn(0f, 1f)
                            value = 1f - (p.y / box.height).coerceIn(0f, 1f)
                            sync()
                        }
                        detectDragGestures(onDragStart = { set(it) }) { change, _ -> set(change.position) }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val pure = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                    drawRect(Brush.horizontalGradient(listOf(Color.White, pure)))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    val c = Offset(sat * size.width, (1f - value) * size.height)
                    drawCircle(Color.Black.copy(alpha = 0.5f), radius = 11.dp.toPx(), center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx()))
                    drawCircle(Color.White, radius = 10.dp.toPx(), center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                }
            }

            // Hue slider.
            Spacer(Modifier.height(14.dp))
            var bar by remember { mutableStateOf(IntSize.Zero) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .onSizeChanged { bar = it }
                    .pointerInput(Unit) {
                        detectTapGestures {
                            if (bar.width > 0) { hue = (it.x / bar.width).coerceIn(0f, 1f) * 360f; sync() }
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { if (bar.width > 0) { hue = (it.x / bar.width).coerceIn(0f, 1f) * 360f; sync() } },
                        ) { change, _ -> if (bar.width > 0) { hue = (change.position.x / bar.width).coerceIn(0f, 1f) * 360f; sync() } }
                    },
            ) {
                Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                    drawRoundRect(
                        Brush.horizontalGradient((0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) }),
                        topLeft = Offset(0f, 4.dp.toPx()),
                        size = Size(size.width, 16.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                    )
                    val x = hue / 360f * size.width
                    drawCircle(Color.White, radius = 12.dp.toPx(), center = Offset(x, size.height / 2))
                    drawCircle(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))), radius = 8.dp.toPx(), center = Offset(x, size.height / 2))
                }
            }

            // Preview and hex field.
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(current())))
                Spacer(Modifier.size(12.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(White.copy(alpha = 0.1f))
                        .border(1.dp, if (hexError) Color(0xFFFF8A80) else Color.Transparent, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    BasicTextField(
                        value = hexText,
                        onValueChange = { raw ->
                            val clean = raw.uppercase().filter { it == '#' || it in '0'..'9' || it in 'A'..'F' }.take(7)
                            hexText = clean
                            val parsed = parseHex(clean)
                            if (parsed != null) {
                                val out = FloatArray(3).also { android.graphics.Color.colorToHSV(parsed, it) }
                                hue = out[0]; sat = out[1]; value = out[2]; hexError = false
                            } else {
                                hexError = clean.length >= 7
                            }
                        },
                        singleLine = true,
                        textStyle = TextStyle(color = White, fontSize = 16.sp),
                        cursorBrush = SolidColor(White),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    )
                }
            }

            if (recent.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Recently used", fontSize = 12.sp, color = White.copy(alpha = 0.6f))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (c in recent) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(eventColor(c))
                                .clickable {
                                    val out = FloatArray(3).also { android.graphics.Color.colorToHSV(c or (0xFF shl 24), it) }
                                    hue = out[0]; sat = out[1]; value = out[2]; sync()
                                },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text("Cancel", color = White.copy(alpha = 0.8f)) }
                TextButton(onClick = { if (!hexError) onDone(current()) }) { Text("Done", color = Color(0xFF7FB6FF), fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

internal fun hexOf(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

/** "#RRGGBB" (or without the #) to an opaque ARGB int; null if it isn't six hex digits. */
internal fun parseHex(text: String): Int? {
    val body = text.removePrefix("#")
    if (body.length != 6) return null
    return body.toIntOrNull(16)?.let { it or (0xFF shl 24) }
}
