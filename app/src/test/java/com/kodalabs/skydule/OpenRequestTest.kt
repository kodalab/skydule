package com.kodalabs.skydule

import com.kodalabs.skydule.data.CalendarEvent
import com.kodalabs.skydule.data.DateWindow
import com.kodalabs.skydule.data.LoadedEvents
import com.kodalabs.skydule.ui.OpenResolution
import com.kodalabs.skydule.ui.resolveOpenRequest
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a tapped reminder does, decided from one snapshot of the loaded events. */
class OpenRequestTest {

    private val zone = ZoneId.of("Europe/London")
    private val window = DateWindow(LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 1))
    private fun ms(d: LocalDate, hour: Int = 9) = d.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun event(id: Long, d: LocalDate, begin: Long = ms(d), allDay: Boolean = false) = CalendarEvent(
        id = id, title = "t", begin = begin, end = begin + 3_600_000L, allDay = allDay, location = null, color = 0,
        calendarName = "c", startDay = d, endDay = d,
    )

    private fun loaded(vararg events: CalendarEvent, w: DateWindow? = window) =
        LoadedEvents(w, events.groupBy { it.startDay })

    @Test fun notLoadedWaitsEvenWithAnEmptyMap() {
        assertEquals(OpenResolution.Wait, resolveOpenRequest(1, ms(LocalDate.of(2026, 11, 2)), LoadedEvents.NOT_LOADED, zone))
    }

    @Test fun exactOccurrenceOfARepeatingEventOpens() {
        val a = event(1, LocalDate.of(2026, 11, 2)); val b = event(1, LocalDate.of(2026, 11, 9))
        val c = event(1, LocalDate.of(2026, 11, 16))
        assertEquals(OpenResolution.Open(b), resolveOpenRequest(1, b.begin, loaded(a, b, c), zone))
    }

    @Test fun beyondTheWindowLoadsADayEitherSide() {
        val day = LocalDate.of(2027, 2, 10)
        assertEquals(OpenResolution.Load(day.minusDays(1), day.plusDays(1)), resolveOpenRequest(1, ms(day), loaded(), zone))
    }

    @Test fun rangeGrownButDataNotYetArrivedStillLoads() {
        val day = LocalDate.of(2027, 2, 10)
        val stale = loaded() // snapshot still has the old window
        assertTrue(resolveOpenRequest(1, ms(day), stale, zone) is OpenResolution.Load)
        assertTrue(resolveOpenRequest(1, ms(day), stale, zone) is OpenResolution.Load)
    }

    @Test fun loadedButMissingIsGone() {
        assertEquals(OpenResolution.Gone, resolveOpenRequest(1, ms(LocalDate.of(2026, 11, 2)), loaded(), zone))
    }

    @Test fun editedOccurrenceOpensTheNearest() {
        val moved = event(1, LocalDate.of(2026, 11, 2), begin = ms(LocalDate.of(2026, 11, 2), 10))
        val other = event(1, LocalDate.of(2026, 11, 20))
        assertEquals(OpenResolution.Open(moved), resolveOpenRequest(1, ms(LocalDate.of(2026, 11, 2), 9), loaded(moved, other), zone))
    }

    @Test fun allDayWestOfUtcStillOpens() {
        val la = ZoneId.of("America/Los_Angeles")
        val utcMidnight = LocalDate.of(2026, 11, 2).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() // 1 Nov locally
        val e = event(1, LocalDate.of(2026, 11, 2), begin = utcMidnight, allDay = true)
        assertEquals(OpenResolution.Open(e), resolveOpenRequest(1, utcMidnight, loaded(e), la))
        assertEquals(OpenResolution.Gone, resolveOpenRequest(2, utcMidnight, loaded(e), la))
    }

    @Test fun noBeginOpensTheFirstMatchOtherwiseGone() {
        val e = event(1, LocalDate.of(2026, 11, 2))
        assertEquals(OpenResolution.Open(e), resolveOpenRequest(1, -1, loaded(e), zone))
        assertEquals(OpenResolution.Gone, resolveOpenRequest(9, -1, loaded(e), zone))
    }
}
