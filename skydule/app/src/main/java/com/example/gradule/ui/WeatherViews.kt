package com.example.gradule.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gradule.data.WeatherKind
import com.example.gradule.data.WeatherState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val SunColor = Color(0xFFFFC857)
private val MoonColor = Color(0xFFE6ECFF)
private val CloudColor = Color(0xFFF2F5FF)
private val StormCloudColor = Color(0xFFD5DAE6)
private val RainColor = Color(0xFF7FB8FF)
private val BoltColor = Color(0xFFFFE066)

/** The "how's it out there" chip: icon, temperature and a short condition, or a prompt/placeholder. */
@Composable
fun WeatherPill(state: WeatherState, onClick: () -> Unit, modifier: Modifier = Modifier, date: String? = null) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Glass)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (date != null) {
            Text(date, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = White, maxLines = 1)
            Box(
                Modifier
                    .padding(horizontal = 10.dp)
                    .width(1.dp)
                    .height(18.dp)
                    .background(White.copy(alpha = 0.35f)),
            )
        }
        when (state) {
            is WeatherState.Ready -> {
                WeatherIcon(state.weather.kind, state.weather.isDay, Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Text("${state.weather.temp}°", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = White)
                Spacer(Modifier.width(6.dp))
                Text(
                    state.weather.label,
                    fontSize = 13.sp,
                    color = White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            WeatherState.NeedsPermission -> {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add weather", fontSize = 13.sp, color = White)
            }
            WeatherState.Loading -> Text("Weather…", fontSize = 13.sp, color = White.copy(alpha = 0.7f))
            WeatherState.Unavailable -> Text("Weather unavailable · retry", fontSize = 13.sp, color = White.copy(alpha = 0.7f))
        }
    }
}

/** Small hand-drawn weather glyphs, so there are no icon assets or extra dependencies. */
@Composable
fun WeatherIcon(kind: WeatherKind, isDay: Boolean, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) {
        val s = size.minDimension
        when (kind) {
            WeatherKind.CLEAR -> {
                if (isDay) sun(s * 0.5f, s * 0.5f, s * 0.2f) else moon(s * 0.5f, s * 0.5f, s * 0.32f)
            }
            WeatherKind.PARTLY -> {
                if (isDay) sun(s * 0.36f, s * 0.36f, s * 0.15f) else moon(s * 0.36f, s * 0.36f, s * 0.24f)
                cloud(s * 0.1f, s * 0.35f, s * 0.8f, CloudColor)
            }
            WeatherKind.CLOUDY -> cloud(s * 0.05f, s * 0.2f, s * 0.9f, CloudColor)
            WeatherKind.FOG -> {
                cloud(s * 0.1f, s * 0.02f, s * 0.8f, CloudColor)
                for (y in floatArrayOf(0.68f, 0.86f)) {
                    drawLine(CloudColor.copy(alpha = 0.9f), Offset(s * 0.15f, s * y), Offset(s * 0.85f, s * y), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
                }
            }
            WeatherKind.DRIZZLE -> {
                cloud(s * 0.08f, s * 0.02f, s * 0.84f, CloudColor)
                for (x in floatArrayOf(0.3f, 0.5f, 0.7f)) {
                    drawLine(RainColor, Offset(s * x, s * 0.7f), Offset(s * (x - 0.03f), s * 0.8f), strokeWidth = s * 0.06f, cap = StrokeCap.Round)
                }
            }
            WeatherKind.RAIN -> {
                cloud(s * 0.08f, s * 0.02f, s * 0.84f, CloudColor)
                for (x in floatArrayOf(0.3f, 0.5f, 0.7f)) {
                    drawLine(RainColor, Offset(s * x, s * 0.64f), Offset(s * (x - 0.06f), s * 0.86f), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
                }
            }
            WeatherKind.SNOW -> {
                cloud(s * 0.08f, s * 0.02f, s * 0.84f, CloudColor)
                drawCircle(CloudColor, s * 0.05f, Offset(s * 0.3f, s * 0.72f))
                drawCircle(CloudColor, s * 0.05f, Offset(s * 0.5f, s * 0.86f))
                drawCircle(CloudColor, s * 0.05f, Offset(s * 0.7f, s * 0.72f))
            }
            WeatherKind.STORM -> {
                cloud(s * 0.08f, s * 0.0f, s * 0.84f, StormCloudColor)
                val bolt = Path().apply {
                    moveTo(s * 0.56f, s * 0.46f)
                    lineTo(s * 0.38f, s * 0.76f)
                    lineTo(s * 0.5f, s * 0.76f)
                    lineTo(s * 0.43f, s * 0.98f)
                    lineTo(s * 0.66f, s * 0.64f)
                    lineTo(s * 0.53f, s * 0.64f)
                    close()
                }
                drawPath(bolt, BoltColor)
            }
        }
    }
}

private fun DrawScope.sun(cx: Float, cy: Float, r: Float) {
    drawCircle(SunColor, r, Offset(cx, cy))
    for (i in 0 until 8) {
        val a = (i * PI / 4).toFloat()
        val dx = cos(a)
        val dy = sin(a)
        drawLine(
            SunColor,
            Offset(cx + dx * r * 1.45f, cy + dy * r * 1.45f),
            Offset(cx + dx * r * 1.95f, cy + dy * r * 1.95f),
            strokeWidth = r * 0.28f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.moon(cx: Float, cy: Float, r: Float) {
    val full = Path().apply { addOval(Rect(center = Offset(cx, cy), radius = r)) }
    val cut = Path().apply { addOval(Rect(center = Offset(cx + r * 0.55f, cy - r * 0.3f), radius = r * 0.85f)) }
    drawPath(Path.combine(PathOperation.Difference, full, cut), MoonColor)
}

/** A puffy cloud whose top-left is ([x], [y]) and width is [w] (height is about 0.6 w). */
private fun DrawScope.cloud(x: Float, y: Float, w: Float, color: Color) {
    val h = w * 0.6f
    drawCircle(color, w * 0.2f, Offset(x + w * 0.28f, y + h * 0.62f))
    drawCircle(color, w * 0.28f, Offset(x + w * 0.52f, y + h * 0.42f))
    drawCircle(color, w * 0.2f, Offset(x + w * 0.75f, y + h * 0.62f))
    drawRoundRect(
        color,
        topLeft = Offset(x + w * 0.08f, y + h * 0.62f),
        size = Size(w * 0.87f, w * 0.2f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.1f),
    )
}
