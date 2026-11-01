package com.kodalabs.skydule

import com.kodalabs.skydule.reminders.ReminderScheduler
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Overlapping reschedules must run one at a time, so the last one to finish also saved the last snapshot. */
class ReminderSchedulerLockTest {

    @Test fun runsNeverOverlap() = runBlocking {
        val inside = AtomicInteger(0)
        val maxInside = AtomicInteger(0)
        List(8) {
            async(Dispatchers.Default) {
                ReminderScheduler.exclusive {
                    maxInside.accumulateAndGet(inside.incrementAndGet()) { a, b -> maxOf(a, b) }
                    delay(5)
                    inside.decrementAndGet()
                }
            }
        }.awaitAll()
        assertEquals(1, maxInside.get())
    }

    @Test fun laterSnapshotIsTheOneLeftBehind() = runBlocking {
        var saved = emptySet<String>()
        var source = setOf("old")
        val first = async(Dispatchers.Default) {
            ReminderScheduler.exclusive {
                val snapshot = source // read the (soon stale) calendar
                delay(50)             // slow provider query
                saved = snapshot
            }
        }
        delay(10)
        source = setOf("new") // the event changes while the first run is still going
        val second = async(Dispatchers.Default) { ReminderScheduler.exclusive { saved = source } }
        listOf(first, second).awaitAll()
        assertEquals(setOf("new"), saved)
    }
}
