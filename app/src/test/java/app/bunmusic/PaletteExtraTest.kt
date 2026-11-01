package app.bunmusic

import app.bunmusic.core.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Edge cases of the colour extractor.
 *
 * The extractor runs on a 64 px decode of whatever the cover actually is, and
 * covers are not all photographs: there are transparent PNGs, two-colour sleeve
 * designs, and the occasional pure white album art that used to produce a white
 * tint under white text.
 */
class PaletteExtraTest {

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun solid(r: Int, g: Int, b: Int, count: Int = 200, alpha: Int = 255): IntArray =
        IntArray(count) { argb(alpha, r, g, b) }

    @Test
    fun `translucent pixels are ignored entirely`() {
        // Under ~78 % alpha is a shadow or a rounded corner; averaging those in
        // drags every swatch toward the background.
        assertNull(Palette.extract(solid(255, 0, 0, alpha = 199)))
    }

    @Test
    fun `the alpha threshold includes exactly two hundred`() {
        assertNotNull(Palette.extract(solid(255, 0, 0, alpha = 200)))
    }

    @Test
    fun `one opaque pixel among transparent ones still gives a colour`() {
        val pixels = solid(0, 0, 0, count = 50, alpha = 0) + solid(20, 200, 60, count = 1)
        val swatch = Palette.extract(pixels)!!
        assertEquals(20f, swatch.primary.red * 255f, 1f)
        assertEquals(200f, swatch.primary.green * 255f, 1f)
        assertEquals(60f, swatch.primary.blue * 255f, 1f)
    }

    @Test
    fun `a single box averages the whole cover`() {
        val pixels = solid(255, 0, 0) + solid(0, 0, 255)
        val swatch = Palette.extract(pixels, boxes = 1)!!
        assertEquals(127f, swatch.primary.red * 255f, 1.5f)
        assertEquals(0f, swatch.primary.green * 255f, 1f)
        assertEquals(127f, swatch.primary.blue * 255f, 1.5f)
    }

    @Test
    fun `the same pixels always give the same swatch`() {
        val pixels = solid(10, 20, 30) + solid(240, 30, 90) + solid(20, 200, 120)
        val a = Palette.extract(pixels)!!
        val b = Palette.extract(pixels)!!
        assertEquals(a, b)
    }

    @Test
    fun `a black cover is marked dark`() {
        assertTrue(Palette.extract(solid(0, 0, 0))!!.dark)
    }

    @Test
    fun `a white cover is not marked dark`() {
        assertFalse(Palette.extract(solid(255, 255, 255))!!.dark)
    }

    @Test
    fun `a light grey cover is not marked dark`() {
        // The threshold is 0.55 luminance: 160/255 is 0.63 and keeps dark text,
        // 128/255 is 0.50 and flips to white.
        assertFalse(Palette.extract(solid(160, 160, 160))!!.dark)
        assertTrue(Palette.extract(solid(128, 128, 128))!!.dark)
    }

    @Test
    fun `the secondary colour is a different hue from the primary`() {
        val pixels = solid(220, 40, 40) + solid(40, 80, 220)
        val swatch = Palette.extract(pixels)!!
        val dr = (swatch.primary.red - swatch.secondary.red)
        val db = (swatch.primary.blue - swatch.secondary.blue)
        assertTrue("primary and secondary are the same colour", dr * dr + db * db > 0.01f)
    }

    @Test
    fun `an empty array has no colour`() {
        assertNull(Palette.extract(IntArray(0)))
    }
}
