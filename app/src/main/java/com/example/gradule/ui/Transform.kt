package com.example.gradule.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlin.math.roundToInt

// Roughly what Google Calendar does: ~300 ms out with a fast start, a touch quicker back in.
const val OPEN_MS = 340
const val CLOSE_MS = 260
val OpenEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
val CloseEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * Container transform. A sheet starts exactly on [from] (the tapped card or button) and grows to fill the
 * screen: corners square off, [startColor] turns into the solid [endColor], and the UI behind it dims.
 * [content] is faded in once the sheet is mostly open. [progress] runs 0 (at [from]) to 1 (full screen);
 * animating it back to 0 shrinks the sheet into [from] again.
 */
@Composable
fun TransformSheet(
    from: Rect,
    progress: State<Float>,
    startColor: Color,
    endColor: Color,
    startCorner: Dp,
    content: @Composable (Modifier) -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // Swallow touches so nothing underneath reacts while the sheet is up.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        val p = progress.value.coerceIn(0f, 1f)
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()

        val left = mix(from.left, 0f, p)
        val top = mix(from.top, 0f, p)
        val right = mix(from.right, screenW, p)
        val bottom = mix(from.bottom, screenH, p)

        // Start colour becomes solid over the first half so whatever was under the sheet is covered as it grows.
        val fill = (p / 0.55f).coerceIn(0f, 1f)

        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f * p)))

        Box(
            Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(
                    width = with(density) { (right - left).toDp() },
                    height = with(density) { (bottom - top).toDp() },
                )
                .clip(RoundedCornerShape(lerp(startCorner, 0.dp, p)))
                .background(lerp(startColor, endColor, fill)),
        ) {
            if (p > 0.4f) {
                val alpha = ((p - 0.4f) / 0.4f).coerceIn(0f, 1f)
                // Laid out at full-screen size and clipped by the growing sheet, so text never re-wraps mid-animation.
                content(
                    Modifier
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .size(
                            width = with(density) { screenW.toDp() },
                            height = with(density) { screenH.toDp() },
                        )
                        .graphicsLayer { this.alpha = alpha },
                )
            }
        }
    }
}
