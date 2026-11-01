package com.kodalabs.skydule

import com.kodalabs.skydule.data.entriesToCopy
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyPrefsTest {
    @Test fun copiesIntoEmptyTarget() {
        assertEquals(mapOf("a" to 1, "b" to "x"), entriesToCopy(mapOf("a" to 1, "b" to "x"), emptyMap<String, Any>()))
    }
    @Test fun neverOverwritesNewerData() {
        assertEquals(emptyMap<String, Any?>(), entriesToCopy(mapOf("a" to 1), mapOf("a" to 2)))
    }
}
