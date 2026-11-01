package com.kodalabs.skydule.ui

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
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

// How long to wait for a page to open (`Motion.pageOpen()`) before focusing the editor.
const val OPEN_MS = 340

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
    /** Strength of the dimming behind the sheet. Zero when the sheet fills a card rather than the screen. */
    scrim: Float = 0.32f,
    /** Corner radius once fully open: square for a full-screen page, rounded when it fills a card. */
    endCorner: Dp = 0.dp,
    /** The sheet is already at its full size from the start (a card that slides in on its own). */
    startFull: Boolean = false,
    /** Fraction of the animation during which the corners keep their starting radius. */
    cornerHold: Float = 0.7f,
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

        val origin = if (startFull) Rect(0f, 0f, screenW, screenH) else from
        // The screen is much taller than wide, so moving all four edges at one rate makes a tall thin slab. Widen
        // first (ease-out) and let the height follow, which keeps the growing shape close to the button's own proportions.
        val px = 1f - (1f - p) * (1f - p)
        val left = mix(origin.left, 0f, px)
        val top = mix(origin.top, 0f, p)
        val right = mix(origin.right, screenW, px)
        val bottom = mix(origin.bottom, screenH, p)

        // Start colour becomes solid over the first half so whatever was under the sheet is covered as it grows.
        val fill = (p / 0.55f).coerceIn(0f, 1f)

        if (scrim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim * p)))

        Box(
            Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(
                    width = with(density) { (right - left).toDp() },
                    height = with(density) { (bottom - top).toDp() },
                )
                // Stay rounded for most of the way and square off only at the end, so a growing page never has sharp corners.
                .clip(RoundedCornerShape(lerp(startCorner, endCorner, ((p - cornerHold) / (1f - cornerHold)).coerceIn(0f, 1f))))
                .background(lerp(startColor, endColor, fill)),
        ) {
            if (p > 0.55f) {
                // Only once the panel is mostly open, so content isn't seen cropped inside a half-grown shape.
                val alpha = ((p - 0.55f) / 0.35f).coerceIn(0f, 1f)
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


/**
 * Where the event page/editor is shown. NONE: full screen (phones, cover screens). SLIDE: a card that slides in from the side while the content makes room (Week, Month).
 */
enum class PaneMode { NONE, SLIDE }

val LocalPaneMode = androidx.compose.runtime.compositionLocalOf { PaneMode.NONE }

/** Keeps a full-screen page clear of the status and navigation bars, except inside the side panel. */
@Composable
fun Modifier.pageInsets(): Modifier =
    if (LocalPaneMode.current != PaneMode.NONE) this
    else this.statusBarsPadding().navigationBarsPadding()
