package com.example.gradule.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.LaunchedEffect
import com.example.gradule.MainActivity
import androidx.core.content.ContextCompat
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.coroutineScope
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.unit.dp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gradule.MainViewModel
import com.example.gradule.data.CalendarEvent

/**
 * Floating frosted pill with the three views. Tap a tab, or drag a finger across the bar to switch as you go.
 * The selection is a translucent pill that slides to the chosen tab.
 */
@Composable
internal fun GlassTabBar(selected: GraduleTab, onSelect: (GraduleTab) -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    val density = LocalDensity.current
    val tabs = GraduleTab.entries
    // Measured cell of each tab, relative to the tab row.
    val cells = remember { mutableStateListOf<Rect?>(*arrayOfNulls<Rect>(tabs.size)) }
    val leftEdge = remember { Animatable(0f) }
    val rightEdge = remember { Animatable(0f) }
    var rowHeight by remember { mutableIntStateOf(0) }
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)

    LaunchedEffect(selected, cells.toList()) {
        val target = cells.getOrNull(selected.ordinal) ?: return@LaunchedEffect
        if (rightEdge.value == 0f && leftEdge.value == 0f) {
            leftEdge.snapTo(target.left)
            rightEdge.snapTo(target.right)
            return@LaunchedEffect
        }
        // Both edges move together on the same spring: a plain slide.
        coroutineScope {
            launch { leftEdge.animateTo(target.left, spring(stiffness = Spring.StiffnessMedium)) }
            launch { rightEdge.animateTo(target.right, spring(stiffness = Spring.StiffnessMedium)) }
        }
    }

    Box(
        Modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.30f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .padding(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    fun indexAt(x: Float): Int {
                        val hit = cells.indexOfFirst { c -> c != null && x >= c.left && x < c.right }
                        if (hit >= 0) return hit
                        // Between tabs or past the ends: the nearest tab wins.
                        return cells.indices.minByOrNull { i -> cells[i]?.let { abs((it.left + it.right) / 2f - x) } ?: Float.MAX_VALUE } ?: -1
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun pick(x: Float) {
                            val i = indexAt(x)
                            if (i >= 0 && tabs[i] != currentSelected) currentOnSelect(tabs[i])
                        }
                        pick(down.position.x)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            pick(change.position.x)
                        }
                    }
                },
        ) {
            val left = leftEdge.value
            val right = rightEdge.value
            if (right > left && rowHeight > 0) {
                Box(
                    Modifier
                        .offset { IntOffset(left.roundToInt(), 0) }
                        .size(
                            width = with(density) { (right - left).toDp() },
                            height = with(density) { rowHeight.toDp() },
                        )
                        .clip(RoundedCornerShape(26.dp))
                        // Clear glass, not a white fill: the bar shows through.
                        .background(Color.White.copy(alpha = 0.20f))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(26.dp)),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { rowHeight = it.height },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tabs.forEachIndexed { i, t ->
                    TabItem(
                        t,
                        selected = t == selected,
                        onSelect = { currentOnSelect(t) },
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { c ->
                                val p = c.positionInParent()
                                cells[i] = Rect(p.x, p.y, p.x + c.size.width, p.y + c.size.height)
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun TabItem(tab: GraduleTab, selected: Boolean, onSelect: () -> Unit, modifier: Modifier) {
    val fg by animateColorAsState(if (selected) White else White.copy(alpha = 0.7f), label = "tabFg")
    Row(
        // The bar itself reads raw touches, so TalkBack gets its tab semantics (role, selected, activate) here.
        modifier
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                this.selected = selected
                onClick(label = "Show ${tab.label}") { onSelect(); true }
            }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabIcon(tab, fg)
        Spacer(Modifier.width(8.dp))
        Text(tab.label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Tiny hand-drawn glyphs: list lines, week columns, month dots. */
@Composable
private fun TabIcon(tab: GraduleTab, tint: Color) {
    Canvas(Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        when (tab) {
            GraduleTab.Schedule -> {
                for (i in 0..2) {
                    val y = h * (0.2f + 0.3f * i)
                    drawCircle(tint, radius = w * 0.07f, center = Offset(w * 0.1f, y))
                    drawLine(tint, Offset(w * 0.3f, y), Offset(w * 0.95f, y), strokeWidth = w * 0.1f, cap = StrokeCap.Round)
                }
            }
            GraduleTab.Week -> {
                val tops = floatArrayOf(0.15f, 0.4f, 0.05f)
                val heights = floatArrayOf(0.4f, 0.3f, 0.55f)
                for (i in 0..2) {
                    drawRoundRect(
                        tint,
                        topLeft = Offset(w * (0.08f + 0.3f * i), h * tops[i]),
                        size = Size(w * 0.22f, h * heights[i]),
                        cornerRadius = CornerRadius(w * 0.06f),
                    )
                }
            }
            GraduleTab.Month -> {
                for (r in 0..2) {
                    for (c in 0..2) {
                        drawCircle(tint, radius = w * 0.085f, center = Offset(w * (0.18f + 0.32f * c), h * (0.18f + 0.32f * r)))
                    }
                }
            }
        }
    }
}
