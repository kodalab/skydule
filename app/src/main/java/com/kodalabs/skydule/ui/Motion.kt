package com.kodalabs.skydule.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring

/**
 * Skydule's motion, in the spirit of Material 3 Expressive: everything moves on springs, not timed curves, so a tap
 * during an animation reverses smoothly from where it is. Two families, like Expressive's motion scheme:
 *  - spatial springs move and resize things (a little bounce when the thing is small, none for full-screen pages);
 *  - effects springs fade and recolour (never bounce).
 * Each has fast / default / slow. The numbers follow Expressive's published tokens and were tuned by feel; change the
 * feel of the whole app here. (The Compose Material library's own MotionScheme is still experimental, so we don't
 * depend on it.)
 */
object Motion {
    // Spatial: position, size, shape.
    fun <T> fastSpatial(threshold: T? = null): SpringSpec<T> = spring(0.6f, 800f, threshold)
    fun <T> defaultSpatial(threshold: T? = null): SpringSpec<T> = spring(0.8f, 380f, threshold)
    fun <T> slowSpatial(threshold: T? = null): SpringSpec<T> = spring(0.8f, 200f, threshold)

    // Effects: opacity, colour.
    fun <T> fastEffects(threshold: T? = null): SpringSpec<T> = spring(1f, 3800f, threshold)
    fun <T> defaultEffects(threshold: T? = null): SpringSpec<T> = spring(1f, 1600f, threshold)
    fun <T> slowEffects(threshold: T? = null): SpringSpec<T> = spring(1f, 800f, threshold)

    /** A full-screen page growing out of a button, or a panel sliding in: lively but no visible overshoot. */
    fun <T> container(threshold: T? = null): SpringSpec<T> = spring(0.9f, 420f, threshold)

    /** Things whose size drives layout (the month's day panel): critically damped, so cells never overshoot. */
    fun <T> panel(threshold: T? = null): SpringSpec<T> = spring(1f, 500f, threshold)

    /**
     * Full-screen pages (Settings, the event page, the editor, the widescreen side card) stay on timed curves, like
     * Google Calendar: about a third of a second out with a fast start, a touch quicker back in. We tried springs here
     * and a recording showed them finishing in a couple of frames, so the page no longer visibly grew.
     */
    private const val PAGE_OPEN_MS = 340
    private const val PAGE_CLOSE_MS = 260
    private val pageOpenEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    private val pageCloseEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    fun <T> pageOpen(): TweenSpec<T> = tween(PAGE_OPEN_MS, easing = pageOpenEasing)
    fun <T> pageClose(): TweenSpec<T> = tween(PAGE_CLOSE_MS, easing = pageCloseEasing)
}
