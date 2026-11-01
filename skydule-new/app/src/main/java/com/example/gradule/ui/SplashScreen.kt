package com.example.gradule.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.gradule.R

/** The wordmark font, the same as the app icon (IBM Plex Serif Bold, SIL Open Font Licence). */
private val WordmarkFont = FontFamily(Font(R.font.ibmplexserif_bold, FontWeight.Bold))

/** Pale translucent discs as (centreX, centreY, radius, alpha), as fractions of the screen. */
private val splashBubbles = listOf(
    floatArrayOf(0.00f, 0.08f, 0.55f, 0.16f),
    floatArrayOf(0.92f, 0.02f, 0.36f, 0.16f),
    floatArrayOf(0.33f, 0.23f, 0.12f, 0.14f),
    floatArrayOf(0.74f, 0.21f, 0.14f, 0.12f),
    floatArrayOf(0.98f, 0.98f, 0.50f, 0.14f),
    floatArrayOf(0.28f, 0.93f, 0.22f, 0.12f),
)

/**
 * Launch splash: the same time-of-day sky as the app background, with soft discs and the wordmark. It's shown for
 * a moment after launch (see LaunchGate in MainActivity), then fades into the app.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    // Picked once per launch, so it matches the background the app is about to show.
    val (top, bottom) = remember { skyColors(currentHour()) }
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, bottom)))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            for (b in splashBubbles) {
                drawCircle(
                    color = Color.White.copy(alpha = b[3]),
                    radius = b[2] * w,
                    center = Offset(b[0] * w, b[1] * h),
                )
            }
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            // The little sun from the app icon.
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFFFFECAA), Color(0xFFFFA55A)))),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Skydule",
                fontFamily = WordmarkFont,
                fontWeight = FontWeight.Bold,
                fontSize = 52.sp,
                color = Color.White,
                style = TextStyle(shadow = Shadow(Color(0x40000000), Offset(0f, 4f), 8f)),
            )
        }
    }
}
