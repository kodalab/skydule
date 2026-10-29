package com.example.gradule.ui

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.layer.CompositingStrategy as LayerCompositing
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** Height of the floating tab bar (including the system navigation inset), so screens can keep clear of it. */
internal val LocalBarInset = compositionLocalOf { 0.dp }

/**
 * Lets [content] scroll underneath the floating tab bar: along the bottom (the bar's height plus a little above it)
 * the content melts into a blurred, slightly darkened copy of itself. The blur needs Android 12 (API 31); older
 * phones get the darkened fade without the blur.
 */
@Composable
internal fun BottomBlur(barPx: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()
    val blurLayer = rememberGraphicsLayer()
    val blurOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box(
        modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
                val w = size.width
                val band = (barPx + 28.dp.toPx()).coerceAtMost(size.height)
                if (barPx > 0 && band > 0f) {
                    val top = size.height - band
                    // 0 at the top of the band, 1 from about 40% down: how much the sharp content gives way.
                    val ramp = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.4f to Color.Black,
                        1f to Color.Black,
                        startY = top,
                        endY = size.height,
                    )
                    // Replace the sharp content in the band by fading it out...
                    drawRect(
                        brush = ramp,
                        topLeft = Offset(0f, top),
                        size = Size(w, band),
                        alpha = if (blurOk) 1f else 0.85f,
                        blendMode = BlendMode.DstOut,
                    )
                    // ...and fading a blurred copy in.
                    if (blurOk) {
                        blurLayer.compositingStrategy = LayerCompositing.Offscreen
                        blurLayer.renderEffect = BlurEffect(10.dp.toPx(), 10.dp.toPx(), TileMode.Clamp)
                        blurLayer.record(size = IntSize(w.toInt(), band.toInt())) {
                            translate(top = -top) { drawLayer(layer) }
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to Color.Transparent, 0.4f to Color.Black, 1f to Color.Black,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                        translate(top = top) { drawLayer(blurLayer) }
                    }
                    // A little darkening toward the bottom keeps the bar's labels readable over bright cards.
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color(0xFF0B1030).copy(alpha = if (blurOk) 0.45f else 0.35f),
                            startY = top,
                            endY = size.height,
                        ),
                        topLeft = Offset(0f, top),
                        size = Size(w, band),
                    )
                }
            },
    ) { content() }
}
