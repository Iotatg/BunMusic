package app.bunmusic

import app.bunmusic.data.Listening
import app.bunmusic.data.Wrapped
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * Regressions for A14 (timezone double-offset) and the hardening around it.
 *
 * The bug: `atMs + offsetMs` is local wall-clock time written as a pseudo-UTC
 * timestamp. Reading it back with `Calendar.getInstance()` (device zone) added
 * the offset a *second* time, so a play just before local New Year was filed
 * under the wrong month/year. These tests set the device zone explicitly, so
 * they fail against the old implementation, not just against UTC.
 */
class BugFixBatch5Test {

    private data class Case(
        val label: String,
        val offsetMs: Long,
        val utc: LocalDateTime,
        val year: Int,
        val month: Int, // 1..12
    )

    private val boundaryCases = listOf(
        // East of UTC: local 2024-12-31 23:30 == real UTC 2024-12-31 15:30.
        // A GMT+8 Calendar used to read the pseudo-UTC value as 2025-01-01 07:30.
        Case("east of UTC, just before New Year", 8 * 3_600_000L,
            LocalDateTime.of(2024, 12, 31, 15, 30), 2024, 12),
        // West of UTC: local 2025-01-01 00:30 == real UTC 2025-01-01 08:30.
        // A GMT-8 Calendar used to read it back as 2024-12-31 16:30.
        Case("west of UTC, just after New Year", -8 * 3_600_000L,
            LocalDateTime.of(2025, 1, 1, 8, 30), 2025, 1),
    )

    @Test
    fun `monthly and yearOf apply the offset exactly once in any device zone`() {
        val original = TimeZone.getDefault()
        try {
            for (zone in listOf("UTC", "GMT+08:00", "GMT-08:00", "Asia/Kolkata")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                for (c in boundaryCases) {
                    val ms = c.utc.toInstant(ZoneOffset.UTC).toEpochMilli()
                    val event = Listening.Event("a", ms, 60_000L)
                    val bars = Listening.monthly(listOf(event), c.year, c.offsetMs)
                    assertEquals("${c.label} / $zone: wrong month", 1, bars[c.month - 1])
                    assertEquals("${c.label} / $zone: play counted twice or lost", 1, bars.sum())
                    val otherYear = if (c.year == 2024) 2025 else 2024
                    assertEquals("${c.label} / $zone: leaked into $otherYear", 0,
                        Listening.monthly(listOf(event), otherYear, c.offsetMs).sum())
                    assertEquals("${c.label} / $zone: yearOf", c.year, Wrapped.yearOf(ms, c.offsetMs))
                }
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `localDate agrees with dayIndex on ordinary timestamps`() {
        assertEquals("1970-01-01", Listening.localDate(0L, 0L).toString())
        for (ms in listOf(1L, 86_400_000L, 1_700_000_000_000L, -86_400_000L)) {
            assertEquals(
                Listening.dayIndex(ms, 0L),
                Listening.localDate(ms, 0L).toEpochDay(),
            )
        }
        // The offset shifts the local date, exactly as dayIndex says.
        assertEquals(
            Listening.dayIndex(0L, 8 * 3_600_000L),
            Listening.localDate(0L, 8 * 3_600_000L).toEpochDay(),
        )
    }

    @Test
    fun `corrupt extreme timestamps never throw or poison the buckets`() {
        val offsets = listOf(0L, 8 * 3_600_000L, -8 * 3_600_000L, Long.MAX_VALUE, Long.MIN_VALUE)
        for (ms in listOf(Long.MIN_VALUE, Long.MAX_VALUE, 0L, -1L, 1_700_000_000_000L)) {
            for (off in offsets) {
                // None of these may throw (saturating add + bounded epoch day).
                val date = Listening.localDate(ms, off)
                assertTrue(date.year in -999_999_999..999_999_999)
                assertEquals(12, Listening.monthly(listOf(Listening.Event("a", ms, 0L)), 2024, off).size)
                Wrapped.yearOf(ms, off)
            }
        }
        // hourly must also survive the saturated value and still count the play.
        val hours = Listening.hourly(listOf(Listening.Event("a", Long.MAX_VALUE, 0L)), Long.MAX_VALUE)
        assertEquals(24, hours.size)
        assertEquals(1, hours.sum())
    }
}
