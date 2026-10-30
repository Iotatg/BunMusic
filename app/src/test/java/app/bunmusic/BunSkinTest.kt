package app.bunmusic

import app.bunmusic.core.BunSkin
import app.bunmusic.core.Skin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** The skin table is pure data, so it can be checked without an Android runtime. */
class BunSkinTest {

    @Test
    fun everyBunHasItsOwnPalette() {
        val midnight = Skin.paletteOf(BunSkin.MIDNIGHT)
        val charcoal = Skin.paletteOf(BunSkin.CHARCOAL)
        assertNotEquals(midnight.base, charcoal.base)
        assertNotEquals(midnight.text, charcoal.text)
        assertNotEquals(midnight.accent, charcoal.accent)
    }

    @Test
    fun onlyCharcoalIsLight() {
        assertFalse(Skin.paletteOf(BunSkin.MIDNIGHT).light)
        assertTrue(Skin.paletteOf(BunSkin.CHARCOAL).light)
    }

    @Test
    fun bothBunsKeepTheirTextReadable() {
        BunSkin.entries.forEach { bun ->
            val p = Skin.paletteOf(bun)
            assertTrue("$bun body text", contrast(p.text, p.base) > 12f)
            assertTrue("$bun muted text", contrast(p.muted, p.base) > 3.5f)
            assertTrue("$bun button label", contrast(p.onAccent, p.accent) > 7f)
            assertTrue("$bun mini player", contrast(p.miniText, p.miniBg) > 7f)
        }
    }

    @Test
    fun unknownIdFallsBackToMidnight() {
        assertEquals(BunSkin.MIDNIGHT, BunSkin.of("CHARCOAL-NOT-REAL"))
        assertEquals(BunSkin.MIDNIGHT, BunSkin.of(null))
        assertEquals(BunSkin.CHARCOAL, BunSkin.of("CHARCOAL"))
    }

    private fun channel(v: Float): Float =
        if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)

    private fun luminance(c: androidx.compose.ui.graphics.Color): Float =
        0.2126f * channel(c.red) + 0.7152f * channel(c.green) + 0.0722f * channel(c.blue)

    private fun contrast(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Float {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }
}
