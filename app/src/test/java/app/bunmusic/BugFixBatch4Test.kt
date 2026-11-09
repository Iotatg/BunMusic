package app.bunmusic

import app.bunmusic.data.Auth
import app.bunmusic.data.Eq
import app.bunmusic.data.Listening
import app.bunmusic.data.Memory
import app.bunmusic.data.Session
import app.bunmusic.data.Storage
import app.bunmusic.data.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressions for the second batch of the 2.11.x bug sweep (A13/A15/A16/A17/A19).
 * Each guard is exposed as a pure function so these hold without a device or clock.
 */
class BugFixBatch4Test {

    // ------------------------------------------------------------- A13: Eq range

    @Test
    fun `a missing or short band range falls back to a valid ordered range`() {
        // null and a single-element OEM array used to throw on range[1].
        for (raw in listOf(null, shortArrayOf(), shortArrayOf(400))) {
            val r = Eq.safeBandRange(raw)
            assertTrue("ordered for $raw", r.first <= r.last)
            assertTrue("non-empty for $raw", r.first < r.last)
            assertTrue(r.first <= 0)
        }
        assertEquals(-1500, Eq.safeBandRange(null).first)
        assertEquals(1500, Eq.safeBandRange(null).last)
    }

    @Test
    fun `a reversed or degenerate band range cannot make coerceIn throw`() {
        val reversed = Eq.safeBandRange(shortArrayOf(1500, -1500))
        assertEquals(-1500, reversed.first)
        assertEquals(1500, reversed.last)
        // Equal endpoints would be an empty coerceIn range; guard widens it.
        val degenerate = Eq.safeBandRange(shortArrayOf(0, 0))
        assertTrue(degenerate.first < degenerate.last)
        // Any level must clamp without an IllegalArgumentException.
        for (r in listOf(reversed, degenerate, Eq.safeBandRange(shortArrayOf(900, -900)))) {
            assertTrue(9_999.coerceIn(r.first, r.last) >= r.first)
            assertTrue((-9_999).coerceIn(r.first, r.last) <= r.last)
        }
    }

    // ------------------------------------------------------------- A15: Storage unlimited

    @Test
    fun `an unlimited cache limit plans no cleanup and never claims to be over`() {
        val buckets = Storage.buckets(100, 2, 500, 200, 10)
        // -1 (unlimited) used to reach cleanupPlan as a negative byte count:
        // over = total - (negative) made everything look reclaimable.
        assertTrue(Storage.cleanupPlan(buckets, limitBytes = -1L).isEmpty())
        val verdict = Storage.verdict(810L, -1L)
        assertTrue(verdict.contains("no limit", ignoreCase = true))
        assertTrue(!verdict.contains("over your limit"))
        // Normal positive limits are untouched.
        assertTrue(Storage.cleanupPlan(buckets, limitBytes = 100L).isNotEmpty())
    }

    // ------------------------------------------------------------- A16: Memory capacity

    @Test
    fun `a hostile yearsBack cannot allocate a giant array or loop forever`() {
        val events = listOf(Listening.Event("a", 0L, 1000L))
        // Int.MAX_VALUE as ArrayList capacity used to OOM before any work.
        val result = Memory.onThisDay(
            events, { _ -> null }, nowMs = 0L, offsetMs = 0L,
            yearsBack = Int.MAX_VALUE,
        )
        assertTrue(result.size <= 50)
    }

    // ------------------------------------------------------------- A17: OTP clock rollback

    @Test
    fun `a clock rollback cannot lock the user out of OTP forever`() {
        val cooldown = 30_000L
        // last in the future (clock moved back): elapsed is negative, must allow.
        assertEquals(0L, Auth.otpCooldownWaitSeconds(1_000L, 9_999_999L, cooldown))
        // No previous request.
        assertEquals(0L, Auth.otpCooldownWaitSeconds(1_000L, 0L, cooldown))
        // Cooldown already elapsed.
        assertEquals(0L, Auth.otpCooldownWaitSeconds(40_000L, 5_000L, cooldown))
        // A genuine recent request still waits a positive number of seconds.
        assertTrue(Auth.otpCooldownWaitSeconds(10_000L, 5_000L, cooldown) > 0L)
    }

    // ------------------------------------------------------------- A19: Session sanitize

    @Test
    fun `a corrupt persisted session is clamped before it reaches the player`() {
        val tracks = listOf(Track("a", "A", "X"), Track("b", "B", "X"))
        val clean = Session.sanitize(
            tracks = tracks,
            index = 999,
            positionMs = -5_000L,
            speed = Float.NaN,
            repeat = 99,
            shuffle = false,
            savedAt = 1L,
        )
        assertEquals(tracks.lastIndex, clean.index)
        assertEquals(0L, clean.positionMs)
        assertEquals(1f, clean.speed, 0f)
        assertEquals(2, clean.repeat)
        assertEquals(1L, clean.savedAt)

        // Zero/negative/NaN/Inf speeds all become a legal playback speed; an
        // out-of-range speed clamps into the player's 0.5f..2f window.
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -1f)) {
            val s = Session.sanitize(tracks, 0, 0L, bad, 0, false, 0L)
            assertEquals(1f, s.speed, 0f)
            assertTrue(s.speed in 0.5f..2f)
        }
        assertEquals(2f, Session.sanitize(tracks, 0, 0L, 9f, 0, false, 0L).speed, 0f)
        assertEquals(0.5f, Session.sanitize(tracks, 0, 0L, 0.1f, 0, false, 0L).speed, 0f)
    }
}
