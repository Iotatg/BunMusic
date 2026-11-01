package app.bunmusic

import app.bunmusic.ui.fmtBytes
import app.bunmusic.ui.fmtDuration
import app.bunmusic.ui.fmtSpeed
import app.bunmusic.ui.fmtTime
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * The four formatters the interface cannot do without.
 *
 * Every one of them is on a row that is visible while the listener is looking at
 * something else — the seek bar, an album's total length, the cache size, a
 * download's speed — so a wrong number here is a wrong number on screen.
 */
class FormatTest {

    @Before
    fun pinLocale() {
        // The formatters use the JVM default locale on purpose; the assertions
        // below are written for the decimal point, so the default is pinned here
        // rather than left to whatever the build machine happens to be set to.
        Locale.setDefault(Locale.US)
    }

    // ------------------------------------------------------------------- time

    @Test
    fun `an unknown position prints a placeholder`() {
        assertEquals("--:--", fmtTime(0))
        assertEquals("--:--", fmtTime(-1))
        assertEquals("--:--", fmtTime(-90_000))
    }

    @Test
    fun `seconds are zero padded`() {
        assertEquals("0:01", fmtTime(1_000))
        assertEquals("0:09", fmtTime(9_999))
        assertEquals("0:10", fmtTime(10_000))
    }

    @Test
    fun `minutes roll over without padding`() {
        assertEquals("1:00", fmtTime(60_000))
        assertEquals("9:59", fmtTime(599_000))
        assertEquals("10:00", fmtTime(600_000))
    }

    @Test
    fun `the last second of a track does not round up`() {
        // 3:59.999 is still 3:59 — flooring is what keeps the clock from showing
        // a time the track has not reached yet.
        assertEquals("3:59", fmtTime(239_999))
        assertEquals("4:00", fmtTime(240_000))
    }

    @Test
    fun `sub second positions read as zero`() {
        assertEquals("0:00", fmtTime(999))
    }

    @Test
    fun `hours are shown as minutes past sixty`() {
        // fmtTime is a player clock, not a wall clock: a 61-minute mix reads
        // "61:00" because that is what a seek bar means.
        assertEquals("60:00", fmtTime(3_600_000))
        assertEquals("61:01", fmtTime(3_661_000))
    }

    // --------------------------------------------------------------- duration

    @Test
    fun `an empty collection says zero minutes`() {
        assertEquals("0 min", fmtDuration(0))
        assertEquals("0 min", fmtDuration(-5_000))
    }

    @Test
    fun `under a minute rounds down to zero`() {
        assertEquals("0 min", fmtDuration(59_999))
        assertEquals("1 min", fmtDuration(60_000))
    }

    @Test
    fun `minutes become hours past sixty`() {
        assertEquals("59 min", fmtDuration(59 * 60_000L))
        assertEquals("1 hr 0 min", fmtDuration(60 * 60_000L))
        assertEquals("1 hr 30 min", fmtDuration(90 * 60_000L))
        assertEquals("2 hr 5 min", fmtDuration(125 * 60_000L))
    }

    // ------------------------------------------------------------------ bytes

    @Test
    fun `zero and negative sizes read as zero`() {
        assertEquals("0 MB", fmtBytes(0))
        assertEquals("0 MB", fmtBytes(-1))
    }

    @Test
    fun `kilobytes are whole numbers`() {
        assertEquals("1 KB", fmtBytes(1_024))
        assertEquals("1 KB", fmtBytes(2_047))
        assertEquals("1023 KB", fmtBytes(1_048_575))
    }

    @Test
    fun `megabytes keep one decimal`() {
        assertEquals("1.0 MB", fmtBytes(1_048_576))
        assertEquals("5.0 MB", fmtBytes(5 * 1_048_576L))
        assertEquals("1.5 MB", fmtBytes(1_572_864))
    }

    @Test
    fun `bytes are formatted in the default locale`() {
        // A comma decimal separator would render "1,5 MB", which is correct for
        // that locale and must not be asserted away. This pins the mechanism:
        // the same number, formatted through the JVM's locale rules.
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1,5 MB", fmtBytes(1_572_864))
            Locale.setDefault(Locale.US)
            assertEquals("1.5 MB", fmtBytes(1_572_864))
        } finally {
            Locale.setDefault(previous)
        }
    }

    // ------------------------------------------------------------------ speed

    @Test
    fun `slow speeds are bytes per second`() {
        assertEquals("0 B/s", fmtSpeed(0))
        assertEquals("512 B/s", fmtSpeed(512))
        assertEquals("1023 B/s", fmtSpeed(1_023))
    }

    @Test
    fun `kilobytes per second have no decimal`() {
        assertEquals("1 KB/s", fmtSpeed(1_024))
        assertEquals("999 KB/s", fmtSpeed(999 * 1_024L))
    }

    @Test
    fun `single digit megabytes keep a decimal`() {
        assertEquals("1.0 MB/s", fmtSpeed(1_048_576))
        assertEquals("9.5 MB/s", fmtSpeed((9.5 * 1_048_576).toLong()))
    }

    @Test
    fun `double digit megabytes drop the decimal`() {
        // Past ten the extra digit is noise: "24.3 MB/s" reads slower than
        // "24 MB/s" even though it is the more precise number.
        assertEquals("10 MB/s", fmtSpeed(10 * 1_048_576L))
        assertEquals("24 MB/s", fmtSpeed(24 * 1_048_576L))
    }
}
