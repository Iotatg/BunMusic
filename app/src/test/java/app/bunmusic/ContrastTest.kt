package app.bunmusic

import app.bunmusic.core.Contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The WCAG arithmetic, and the two decisions made with it. */
class ContrastTest {

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test
    fun `black is dark and white is bright`() {
        assertEquals(0.0, Contrast.luminance(black), 0.0001)
        assertEquals(1.0, Contrast.luminance(white), 0.0001)
    }

    @Test
    fun `black on white is the maximum ratio`() {
        assertEquals(21.0, Contrast.ratio(black, white), 0.01)
    }

    @Test
    fun `a colour against itself is one to one`() {
        assertEquals(1.0, Contrast.ratio(0xFF3366CC.toInt(), 0xFF3366CC.toInt()), 0.001)
    }

    @Test
    fun `the ink flips with the background`() {
        assertEquals(black, Contrast.bestInk(white))
        assertEquals(white, Contrast.bestInk(black))
        // Mid grey is where most "white text on a photo" mistakes happen.
        assertEquals(white, Contrast.bestInk(0xFF555555.toInt()))
    }

    @Test
    fun `a dark background needs no scrim`() {
        assertEquals(0f, Contrast.scrimAlpha(black), 0.001f)
    }

    @Test
    fun `a bright background gets enough scrim to read`() {
        val alpha = Contrast.scrimAlpha(0xFFE8E8E8.toInt())
        assertTrue("alpha was $alpha", alpha in 0.01f..1f)
        // Rebuild the blended colour the same way the function does and check it.
        val v = (232 * (1 - alpha)).toInt()
        val blended = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        assertTrue(Contrast.ratio(blended, white) >= 4.4)
    }

    @Test
    fun `a readable tint either passes or moves toward the ink`() {
        val accent = 0xFFFFE066.toInt() // pale yellow: fails on white
        val fixed = Contrast.readable(accent, white)
        assertTrue(Contrast.ratio(fixed, white) >= 4.4)
        // Something that already passes is returned untouched.
        assertEquals(black, Contrast.readable(black, white))
    }

    @Test
    fun `blend lands on both ends`() {
        assertEquals(black, Contrast.blend(black, white, 0f))
        assertEquals(white, Contrast.blend(black, white, 1f))
        val mid = Contrast.blend(black, white, 0.5f)
        assertEquals(127.0, ((mid shr 16) and 0xFF).toDouble(), 1.0)
    }

    @Test
    fun `the label is one decimal`() {
        assertEquals("21.0:1", Contrast.label(black, white))
    }

    @Test
    fun `the check reports the ratio with its verdict`() {
        val (ok, ratio) = Contrast.check(black, white)
        assertTrue(ok)
        assertTrue(ratio > 20)
    }
}
