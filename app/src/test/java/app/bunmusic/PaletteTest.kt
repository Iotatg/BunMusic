package app.bunmusic

import androidx.compose.ui.graphics.Color
import app.bunmusic.core.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The palette is arithmetic, so it can be pinned the way the crossfade curve is.
 *
 * What is *not* testable here is whether a given cover looks good — that is a
 * judgement, and no assertion makes it. These fix the properties that would be
 * bugs if they broke: a transparent image has no colour, a red cover does not
 * come out green, and the same cover always gives the same answer.
 */
class PaletteTest {

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun solid(r: Int, g: Int, b: Int, count: Int = 400): IntArray =
        IntArray(count) { argb(255, r, g, b) }

    @Test
    fun `nothing to look at returns nothing`() {
        assertNull("empty array", Palette.extract(IntArray(0)))
        assertNull("all transparent", Palette.extract(IntArray(64) { argb(0, 255, 0, 0) }))
        // A cover that is 95 % transparent shadow still has to be ignored.
        assertNull(
            Palette.extract(IntArray(64) { argb(40, 255, 0, 0) }),
        )
    }

    @Test
    fun `a single colour comes back as itself`() {
        val swatch = Palette.extract(solid(192, 57, 43))
        assertNotNull(swatch)
        val p = swatch!!.primary
        // Within a rounding step of #C0392B.
        assertTrue("red channel ${p.red}", abs(p.red - 192f / 255f) < 0.02f)
        assertTrue("green channel ${p.green}", abs(p.green - 57f / 255f) < 0.02f)
        assertTrue("blue channel ${p.blue}", abs(p.blue - 43f / 255f) < 0.02f)
    }

    @Test
    fun `a red cover does not come out green`() {
        // A cover that is mostly warm with a small cold corner: the winner has to
        // be the warm one, not whatever bucket happened to be widest.
        val pixels = solid(214, 69, 65, 900) + solid(24, 40, 90, 60)
        val swatch = Palette.extract(pixels)!!
        assertTrue("primary should be the warm one", swatch.primary.red > swatch.primary.blue)
    }

    @Test
    fun `two-tone covers give two different colours`() {
        val pixels = solid(30, 90, 168, 500) + solid(224, 138, 43, 500)
        val swatch = Palette.extract(pixels)!!
        val a = swatch.primary
        val b = swatch.secondary
        val distance = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)
        assertTrue("primary and secondary are too close: $distance", distance > 0.15f)
    }

    @Test
    fun `order of the pixels does not change the answer`() {
        val pixels = solid(30, 90, 168, 300) + solid(224, 138, 43, 300) + solid(12, 12, 16, 100)
        val first = Palette.extract(pixels)!!
        val shuffled = pixels.copyOf().apply { shuffle(kotlin.random.Random(7)) }
        val second = Palette.extract(shuffled)!!
        assertEquals(first.primary, second.primary)
        assertEquals(first.secondary, second.secondary)
    }

    @Test
    fun `dark covers are marked dark and light ones are not`() {
        assertTrue(Palette.extract(solid(16, 14, 22))!!.dark)
        assertTrue(!Palette.extract(solid(238, 232, 220))!!.dark)
    }

    @Test
    fun `saturation is zero for grey and one for a pure hue`() {
        assertEquals(0f, Palette.saturation(128, 128, 128), 0.0001f)
        assertEquals(0f, Palette.saturation(0, 0, 0), 0.0001f)
        assertEquals(1f, Palette.saturation(255, 0, 0), 0.0001f)
        assertEquals(0.5f, Palette.saturation(255, 128, 128), 0.01f)
    }

    @Test
    fun `luminance follows rec 709`() {
        // Pure green is far brighter than pure blue at the same 8-bit value; that
        // is the whole reason a green tint can carry white text and a blue one
        // sometimes cannot.
        val green = Palette.luminance(0, 255, 0)
        val blue = Palette.luminance(0, 0, 255)
        assertTrue("green $green should beat blue $blue", green > blue * 5)
        assertEquals(1f, Palette.luminance(Color.White), 0.01f)
        assertEquals(0f, Palette.luminance(Color.Black), 0.01f)
    }
}
