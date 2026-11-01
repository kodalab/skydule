package com.kodalabs.skydule.ui

import com.kodalabs.skydule.data.CalendarEvent
import com.kodalabs.skydule.data.LoadedEvents
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/** What to do about a tapped reminder, decided from one snapshot of the loaded events (no Android calls: unit tested). */
sealed interface OpenResolution {
    /** The first load isn't in yet. */
    data object Wait : OpenResolution

    /** That occurrence's days aren't in the snapshot's window: load them, then decide again on the next snapshot. */
    data class Load(val from: LocalDate, val to: LocalDate) : OpenResolution

    /** The days are loaded and the event still isn't there (deleted, or its calendar is hidden). */
    data object Gone : OpenResolution

    data class Open(val event: CalendarEvent) : OpenResolution
}

internal fun resolveOpenRequest(id: Long, begin: Long, loaded: LoadedEvents, zone: ZoneId): OpenResolution {
    if (loaded.window == null) return OpenResolution.Wait
    val same = loaded.byDay.values.asSequence().flatten().filter { it.id == id }.distinct().toList()
    same.firstOrNull { it.begin == begin }?.let { return OpenResolution.Open(it) }

    if (begin <= 0) return same.firstOrNull()?.let { OpenResolution.Open(it) } ?: OpenResolution.Gone

    // All-day instances start at UTC midnight, so the local day can be one off: check a day either side.
    val day = Instant.ofEpochMilli(begin).atZone(zone).toLocalDate()
    val from = day.minusDays(1)
    val to = day.plusDays(1)
    // Decided from the snapshot's own window, not the ViewModel's: until a snapshot for the larger window arrives
    // this keeps saying Load, never Gone.
    if (!loaded.covers(from, to)) return OpenResolution.Load(from, to)

    // Loaded for that range and the exact start is gone (edited or moved): nearest occurrence, else gone.
    return same.minByOrNull { abs(it.begin - begin) }?.let { OpenResolution.Open(it) } ?: OpenResolution.Gone
}
