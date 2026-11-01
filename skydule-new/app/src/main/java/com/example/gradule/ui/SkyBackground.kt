package com.example.gradule.ui

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.example.gradule.data.ThemeMode
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.sin

private class SkyStop(val hour: Float, val top: Color, val bottom: Color)

// Palette keyframes through the day. Kept mid-to-dark so white text always reads well.
private val skyStops = listOf(
    SkyStop(0f, Color(0xFF0B1026), Color(0xFF1B2250)),    // night
    SkyStop(5f, Color(0xFF1B2250), Color(0xFF5B4B8A)),    // pre-dawn
    SkyStop(7f, Color(0xFF4A6FA5), Color(0xFFD9805F)),    // sunrise
    SkyStop(11f, Color(0xFF2E78C7), Color(0xFF5FA3DE)),   // morning blue
    SkyStop(15f, Color(0xFFD98A2F), Color(0xFFA84B28)),   // amber afternoon
    SkyStop(18.5f, Color(0xFFB8426A), Color(0xFF3C2A6E)), // dusk
    SkyStop(21f, Color(0xFF141A3D), Color(0xFF2B2358)),   // evening
    SkyStop(24f, Color(0xFF0B1026), Color(0xFF1B2250)),   // night again
)

internal fun skyColors(hour: Float): Pair<Color, Color> {
    val i = skyStops.indexOfLast { it.hour <= hour }.coerceIn(0, skyStops.size - 2)
    val a = skyStops[i]
    val b = skyStops[i + 1]
    val t = ((hour - a.hour) / (b.hour - a.hour)).coerceIn(0f, 1f)
    return lerp(a.top, b.top, t) to lerp(a.bottom, b.bottom, t)
}

internal fun currentHour(): Float = LocalTime.now().let { it.hour + it.minute / 60f }

@Composable
private fun rememberHour(): State<Float> {
    val hour = remember { mutableFloatStateOf(currentHour()) }
    LaunchedEffect(Unit) {
        while (true) {
            hour.floatValue = currentHour()
            delay(30_000)
        }
    }
    return hour
}

/**
 * Full-screen gradient that drifts with the time of day, with a sun/moon glow on an arc.
 */
@Composable
fun SkyBackground(
    mode: ThemeMode,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    if (mode == ThemeMode.AMOLED) {
        Box(modifier.fillMaxSize().background(Color.Black), content = content)
        return
    }

    val hour by rememberHour()
    val scheme = MaterialTheme.colorScheme
    val useDynamic = mode == ThemeMode.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val (timeTop, timeBottom) = skyColors(hour)
    val targetTop = if (useDynamic) scheme.primaryContainer else timeTop
    val targetBottom = if (useDynamic) scheme.tertiaryContainer else timeBottom

    val top by animateColorAsState(targetTop, tween(2_000), label = "skyTop")
    val bottom by animateColorAsState(targetBottom, tween(2_000), label = "skyBottom")

    // The glow used to "breathe" with an infinite animation, which redrew the whole background every frame forever
    // (battery, and Compose-heavy screens underneath). It is a steady glow now.
    val breathe = 0.9f

    val isDay = hour in 6f..19.5f
    val progress = if (isDay) (hour - 6f) / 13.5f else ((hour - 19.5f + 24f) % 24f) / 10.5f
    val glowColor = if (isDay) Color(0xFFFFF1CC) else Color(0xFFBFD0FF)

    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .drawBehind {
                val x = size.width * (0.15f + 0.7f * progress)
                val y = size.height * (0.26f - 0.12f * sin(PI.toFloat() * progress))
                val radius = size.width * 0.75f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = (if (isDay) 0.38f else 0.22f) * breathe),
                            Color.Transparent,
                        ),
                        center = Offset(x, y),
                        radius = radius,
                    ),
                    radius = radius,
                    center = Offset(x, y),
                )
            },
        content = content,
    )
}
