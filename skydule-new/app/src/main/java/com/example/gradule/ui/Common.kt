package com.example.gradule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gradule.data.CalendarEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

val White = Color.White
val Ink = Color(0xFF1B1B1F)

/** Frosted-glass fill for cards over the sky gradient. */
val Glass = Color.White.copy(alpha = 0.13f)

/** Slightly darker glass behind the dense grids (week and month) so text stays legible on bright skies. */
val GridGlass = Color.Black.copy(alpha = 0.20f)

/** Calendar colours from the provider are usually opaque ARGB, but force alpha to be safe. */
fun eventColor(argb: Int): Color = Color(argb or (0xFF shl 24))

/** Black or white, whichever reads better on this colour. */
fun Color.onColor(): Color = if (luminance() > 0.55f) Ink else White

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

fun formatTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(timeFormat)

fun CalendarEvent.timeLabel(): String =
    if (allDay) "All day" else "${formatTime(begin)} – ${formatTime(end)}"

/** Called when an event is tapped, with the tapped element's on-screen bounds so the detail page can grow out of it. */
typealias EventOpener = (CalendarEvent, Rect) -> Unit

class CoordsHolder {
    var coords: LayoutCoordinates? = null
}

/** Like `clickable`, but reports the element's bounds in the root, for the card-to-page transition. */
fun Modifier.clickableWithBounds(onClick: (Rect) -> Unit): Modifier = composed {
    val holder = remember { CoordsHolder() }
    Modifier
        .onGloballyPositioned { holder.coords = it }
        .clickable(role = Role.Button) {
            val c = holder.coords
            if (c != null && c.isAttached) onClick(c.boundsInRoot())
        }
}

@Composable
fun TodayPill(onClick: () -> Unit, modifier: Modifier = Modifier, label: String = "Today") {
    Text(
        label,
        color = White,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Glass)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
fun SettingsButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = White)
    }
}

/** Title row used by the Week and Month tabs. Settings live here too, so they're reachable from every tab. */
@Composable
fun CompactHeader(
    title: String,
    subtitle: String,
    showToday: Boolean,
    onToday: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            // One line always: on a narrow screen (a foldable's cover display) the title shrinks to fit
            // instead of wrapping and being cut off.
            var titleSize by remember(title) { mutableStateOf(32f) }
            Text(
                title,
                fontSize = titleSize.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-0.5).sp,
                color = White,
                maxLines = 1,
                softWrap = false,
                onTextLayout = { if (it.hasVisualOverflow && titleSize > 18f) titleSize -= 1f },
            )
            Text(subtitle, fontSize = 14.sp, color = White.copy(alpha = 0.65f))
        }
        if (showToday) {
            // "Go to today" is long for a narrow screen, so it becomes "Today" there.
            val narrow = LocalConfiguration.current.screenWidthDp < 400
            TodayPill(onToday, label = if (narrow) "Today" else "Go to today")
            Spacer(Modifier.width(4.dp))
        }
        SettingsButton(onSettings)
    }
}

@Composable
fun EventCard(event: CalendarEvent, onClick: (Rect) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Glass)
            .clickableWithBounds(onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(6.dp).height(46.dp).clip(CircleShape).background(eventColor(event.color)))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                event.title.ifBlank { "(No title)" },
                style = MaterialTheme.typography.titleMedium,
                color = White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(event.timeLabel(), fontSize = 14.sp, color = White.copy(alpha = 0.75f))
            event.location?.let {
                Text(it, fontSize = 13.sp, color = White.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Compact coloured label for an event, used in the month grid and the week all-day strip. */
@Composable
fun EventChip(
    event: CalendarEvent,
    modifier: Modifier = Modifier,
    height: Dp = 15.dp,
    fontSize: TextUnit = 9.sp,
    onClick: ((Rect) -> Unit)? = null,
) {
    val bg = eventColor(event.color)
    Box(
        modifier
            .fillMaxWidth()
            .height(chipHeight(fontSize, height))
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .then(if (onClick != null) Modifier.clickableWithBounds(onClick) else Modifier)
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            event.title.ifBlank { "(No title)" },
            style = chipTextStyle(fontSize),
            color = bg.onColor(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Text style for the tiny coloured event labels. The line is exactly the font size tall with the glyphs centred in it
 * and no extra font padding, so the text sits in the middle of the chip instead of low and clipped (Samsung's default
 * font padding and a larger system font size both pushed it down).
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
fun chipTextStyle(fontSize: TextUnit) = androidx.compose.ui.text.TextStyle(
    fontSize = fontSize,
    lineHeight = fontSize,
    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
        alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
        trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
    ),
    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
)

/** Chip height that grows with the system font size, so a larger font never gets cut off. */
@Composable
fun chipHeight(fontSize: TextUnit, min: Dp): Dp {
    val d = androidx.compose.ui.platform.LocalDensity.current
    return with(d) { maxOf(min, (fontSize * 1.65f).toDp()) }
}
