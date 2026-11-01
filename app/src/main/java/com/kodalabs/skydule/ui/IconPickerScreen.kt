package com.kodalabs.skydule.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kodalabs.skydule.R
import com.kodalabs.skydule.icons.IconChoice
import com.kodalabs.skydule.icons.IconPeriod
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

private val PlexSerif = FontFamily(Font(R.font.ibmplexserif_bold, FontWeight.Bold))

/** What the launcher icon will look like: a squircle of [size] with the dynamic or the default design. */
@Composable
private fun IconPreview(dynamic: Boolean, period: IconPeriod, size: Dp, date: LocalDate = LocalDate.now()) {
    val density = LocalDensity.current
    fun sp(fraction: Float) = with(density) { (size * fraction).toSp() }
    val shape = RoundedCornerShape(size * 0.24f)
    if (dynamic) {
        val (top, bottom) = remember(period) { skyColors(period.hour) }
        // Month: the sky's lighter end, lifted 80% toward white (matches month_color in tools/generate_day_icons.py).
        val monthColor = remember(top, bottom) {
            val light = if (top.luminance() > bottom.luminance()) top else bottom
            androidx.compose.ui.graphics.lerp(light, Color.White, 0.8f)
        }
        val shadow = Shadow(Color(0x46000000), Offset(0f, with(density) { (size * 0.02f).toPx() }), 0f)
        Box(Modifier.size(size).clip(shape).background(Brush.verticalGradient(listOf(top, bottom))), Alignment.Center) {
            Text(
                date.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH).uppercase(),
                Modifier.align(Alignment.TopCenter).padding(top = size * 0.15f),
                color = monthColor, fontFamily = PlexSerif, fontWeight = FontWeight.Bold,
                fontSize = sp(0.17f), style = TextStyle(shadow = shadow),
            )
            Text(
                date.dayOfMonth.toString(),
                Modifier.align(Alignment.BottomCenter).padding(bottom = size * 0.07f),
                color = Color.White, fontFamily = PlexSerif, fontWeight = FontWeight.Bold,
                fontSize = sp(0.50f), style = TextStyle(shadow = shadow),
            )
        }
    } else {
        Box(
            Modifier.size(size).clip(shape).background(
                Brush.verticalGradient(listOf(Color(0xFF1B2A6B), Color(0xFF6A3F9C), Color(0xFFF29445))),
            ),
            Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(size * 0.10f).clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(Color(0xFFFFECAA), Color(0xFFFFA55A)))),
                )
                Spacer(Modifier.height(size * 0.04f))
                Text(
                    "Skydule", color = Color.White, fontFamily = PlexSerif, fontWeight = FontWeight.Bold,
                    fontSize = sp(0.16f),
                )
            }
        }
    }
}

@Composable
private fun ChoiceCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val border = if (selected) BorderStroke(2.dp, White) else BorderStroke(1.dp, White.copy(alpha = 0.15f))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Glass)
            .border(border, RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = White)
                Text(subtitle, fontSize = 14.sp, color = White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 2.dp))
            }
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = White, unselectedColor = White.copy(alpha = 0.6f)),
            )
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}

/** Settings page for choosing between the default icon and the dynamic date icon, with previews. */
@Composable
fun IconPickerScreen(
    choice: IconChoice,
    onChoice: (IconChoice) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = remember { IconPeriod.at(LocalTime.now().hour) }
    Column(
        modifier.fillMaxSize().background(SettingsBackground).statusBarsPadding().navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = White)
            }
            Text("App icon", fontSize = 28.sp, fontWeight = FontWeight.Light, color = White, modifier = Modifier.padding(start = 4.dp))
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ChoiceCard(
                title = "Default",
                subtitle = "The Skydule icon",
                selected = choice == IconChoice.DEFAULT,
                onClick = { onChoice(IconChoice.DEFAULT) },
            ) {
                Box(Modifier.fillMaxWidth(), Alignment.Center) { IconPreview(false, current, 96.dp) }
            }
            ChoiceCard(
                title = "Dynamic",
                subtitle = "Shows today's date, and the sky changes through the day",
                selected = choice == IconChoice.DYNAMIC,
                onClick = { onChoice(IconChoice.DYNAMIC) },
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    IconPreview(true, current, 96.dp)
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (p in IconPeriod.entries) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconPreview(true, p, 56.dp)
                                Text(
                                    p.label,
                                    fontSize = 12.sp,
                                    color = White.copy(alpha = if (p == current) 1f else 0.6f),
                                    fontWeight = if (p == current) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
            Text(
                "The icon on your home screen changes when you leave Skydule, so the app stays open while you choose. " +
                    "Your launcher may take a moment to show it. Switch back to Default any time.",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = White.copy(alpha = 0.55f),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}
