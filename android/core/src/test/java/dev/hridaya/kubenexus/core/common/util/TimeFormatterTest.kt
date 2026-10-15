package dev.hridaya.kubenexus.core.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class TimeFormatterTest {

    private val utc = ZoneOffset.UTC
    private val now = Instant.parse("2026-09-27T15:00:00Z").toEpochMilli()

    private fun refreshed(at: String, now: Long = this.now) =
        TimeFormatter.formatLastRefreshed(Instant.parse(at).toEpochMilli(), now, utc)

    @Test
    fun `formatLastRefreshed is relative within the last hour`() {
        assertEquals("Never refreshed", TimeFormatter.formatLastRefreshed(null, now, utc))
        assertEquals("Refreshed just now", refreshed("2026-09-27T14:59:58Z"))
        assertEquals("Refreshed 42s ago", refreshed("2026-09-27T14:59:18Z"))
        assertEquals("Refreshed 30m ago", refreshed("2026-09-27T14:30:00Z"))
    }

    @Test
    fun `formatLastRefreshed shows the time of day for earlier today`() {
        assertEquals("Refreshed at 12:00:05", refreshed("2026-09-27T12:00:05Z"))
    }

    // Offline caches can be days old; "Refreshed at 23:30:00" would read as today.
    @Test
    fun `formatLastRefreshed includes the date for anything before today`() {
        val lateLastNight = refreshed(
            "2026-09-26T23:30:00Z",
            now = Instant.parse("2026-09-27T01:00:00Z").toEpochMilli()
        )
        val lastWeek = refreshed("2026-09-20T09:00:00Z")

        listOf(lateLastNight, lastWeek).forEach { text ->
            assertTrue(text, text.startsWith("Refreshed on "))
            assertTrue(text, text.contains("2026"))
        }
        assertNotEquals(lateLastNight, lastWeek)
    }

    @Test
    fun `formatIsoToLocal returns NA for null or blank input`() {
        assertEquals("N/A", TimeFormatter.formatIsoToLocal(null))
        assertEquals("N/A", TimeFormatter.formatIsoToLocal(""))
        assertEquals("N/A", TimeFormatter.formatIsoToLocal("   "))
    }

    @Test
    fun `formatIsoToLocal correctly parses ISO-8601 UTC timestamp`() {
        val result = TimeFormatter.formatIsoToLocal("2026-08-17T06:30:00Z")
        assertNotEquals("N/A", result)
        assertNotEquals("2026-08-17T06:30:00Z", result)
    }

    @Test
    fun `formatIsoToLocal returns original string on unparseable invalid format`() {
        val invalid = "not-a-timestamp"
        val result = TimeFormatter.formatIsoToLocal(invalid)
        assertEquals(invalid, result)
    }
}
