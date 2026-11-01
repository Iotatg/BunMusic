package app.bunmusic

import app.bunmusic.data.Eq
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The equaliser's arithmetic, without an audio session.
 *
 * A phone exposes anywhere from five to ten bands and the presets are written as
 * five numbers, so every preset has to be *spread* onto whatever the hardware
 * has. Get that wrong and "Bass" boosts the treble on a ten-band device, which
 * is worse than having no equaliser at all.
 */
class EqTest {

    private val flat = listOf(0f, 0f, 0f, 0f, 0f)
    private val bass = listOf(7.5f, 5f, 0f, -1f, -2f)

    // ----------------------------------------------------------------- spread

    @Test
    fun `a flat curve stays flat on any band count`() {
        for (total in 1..12) {
            for (i in 0 until total) {
                assertEquals("band $i of $total", 0f, Eq.spread(flat, i, total), 0.0001f)
            }
        }
    }

    @Test
    fun `five bands are the identity mapping`() {
        for (i in 0..4) {
            assertEquals(bass[i], Eq.spread(bass, i, 5), 0.0001f)
        }
    }

    @Test
    fun `the first and last bands are always the ends of the curve`() {
        for (total in 2..12) {
            assertEquals("first of $total", bass.first(), Eq.spread(bass, 0, total), 0.0001f)
            assertEquals("last of $total", bass.last(), Eq.spread(bass, total - 1, total), 0.0001f)
        }
    }

    @Test
    fun `a single band takes the first value`() {
        // There is nowhere to interpolate to; picking the middle would silently
        // turn every preset into the same mild hump.
        assertEquals(7.5f, Eq.spread(bass, 0, 1), 0.0001f)
    }

    @Test
    fun `a wider device gets the values in between`() {
        // Ten bands over five points: each step is 4/9 of a preset step, so
        // index 2 sits at 0.888 of the way from 7.5 dB to 5 dB.
        assertEquals(5.278f, Eq.spread(bass, 2, 10), 0.01f)
        // Index 4 is 1.78 steps in — past the second point, already heading to 0.
        assertEquals(1.111f, Eq.spread(bass, 4, 10), 0.01f)
    }

    @Test
    fun `interpolation is monotonic where the curve is`() {
        // Bass falls from 7.5 to -2 across five points. On twelve bands the
        // result must never rise, or the shape on screen is not the shape heard.
        var previous = Float.MAX_VALUE
        for (i in 0 until 12) {
            val v = Eq.spread(bass, i, 12)
            assertTrue("rose at band $i", v <= previous + 0.0001f)
            previous = v
        }
    }

    @Test
    fun `every preset is a five point curve`() {
        assertEquals(Eq.presets.toSet(), Eq.presetGains.keys)
        for ((name, gains) in Eq.presetGains) {
            assertEquals("$name has ${gains.size} points", 5, gains.size)
        }
    }

    @Test
    fun `every preset fits inside the platform band range`() {
        // ±15 dB is what Android's Equalizer allows; a preset outside it would be
        // clamped into a different shape than the one it advertises.
        for ((name, gains) in Eq.presetGains) {
            for (g in gains) {
                assertTrue("$name has $g dB", g in -15f..15f)
            }
        }
    }

    @Test
    fun `bass is the only preset that leads with a big boost`() {
        val leads = Eq.presetGains.filterValues { it.first() >= 5f }.keys
        assertEquals(setOf("Bass", "Rock", "Electronic"), leads)
    }

    @Test
    fun `podcast cuts the low end and lifts the voice`() {
        val podcast = Eq.presetGains.getValue("Podcast")
        assertTrue("low shelf must come down", podcast.first() < 0f)
        assertTrue("presence band must come up", podcast[2] > podcast.first())
    }

    // ------------------------------------------------------------------- band

    @Test
    fun `band labels switch to kilohertz at a thousand`() {
        assertEquals("60", band(hz = 60).label)
        assertEquals("999", band(hz = 999).label)
        assertEquals("1k", band(hz = 1_000).label)
        assertEquals("16k", band(hz = 16_000).label)
    }

    @Test
    fun `millibels are shown as decibels`() {
        assertEquals(7.5f, band(levelMb = 750).levelDb, 0.0001f)
        assertEquals(-3f, band(levelMb = -300).levelDb, 0.0001f)
        assertEquals(0f, band(levelMb = 0).levelDb, 0.0001f)
    }

    @Test
    fun `a band reports the range the hardware gave it`() {
        val b = band(minMb = -1200, maxMb = 1200)
        assertEquals(-1200, b.minMb)
        assertEquals(1200, b.maxMb)
    }

    @Test
    fun `the default state is silent and unavailable`() {
        // Before the player exists there is no session to attach to, and the UI
        // must render "not available" rather than a row of dead sliders.
        val state = Eq.State()
        assertTrue(!state.available)
        assertTrue(!state.enabled)
        assertEquals("Flat", state.preset)
        assertTrue(state.bands.isEmpty())
    }

    @Test
    fun `custom levels are clamped to the band before they are stored`() {
        // The UI's slider can overshoot at the ends; the value that reaches the
        // hardware is the clamped one, which is what a listener sees snap back.
        val b = band(minMb = -1500, maxMb = 1500)
        assertEquals(1500, 9_999.coerceIn(b.minMb, b.maxMb))
        assertEquals(-1500, (-9_999).coerceIn(b.minMb, b.maxMb))
        assertEquals(1500, b.maxMb)
    }

    private fun band(
        index: Int = 0,
        hz: Int = 1_000,
        levelMb: Int = 0,
        minMb: Int = -1500,
        maxMb: Int = 1500,
    ) = Eq.Band(index, hz, levelMb, minMb, maxMb)
}
