package app.bunmusic

import app.bunmusic.core.BunSkin
import app.bunmusic.core.Icons
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
        val cloud = Skin.paletteOf(BunSkin.CLOUD)
        val pink = Skin.paletteOf(BunSkin.PINK)
        assertNotEquals(midnight.base, charcoal.base)
        assertNotEquals(midnight.text, charcoal.text)
        assertNotEquals(midnight.accent, charcoal.accent)
        assertNotEquals(charcoal.accent, cloud.accent)
        assertNotEquals(charcoal.text, cloud.text)
        assertNotEquals(cloud.accent, pink.accent)
        assertNotEquals(cloud.base, pink.base)
    }

    @Test
    fun onlyMidnightIsDark() {
        assertFalse(Skin.paletteOf(BunSkin.MIDNIGHT).light)
        assertTrue(Skin.paletteOf(BunSkin.CHARCOAL).light)
        assertTrue(Skin.paletteOf(BunSkin.CLOUD).light)
        assertTrue(Skin.paletteOf(BunSkin.PINK).light)
    }

    /**
     * Body text has to clear AAA; a button label only has to clear AA. Cloud's
     * accent is the electric blue from the artwork (#2E6BFF), and a blue that
     * vivid cannot reach 7:1 against white without turning navy — 4.5:1 is the
     * honest bar for a filled pill with 12.5 sp semibold on it.
     */
    @Test
    fun everyBunKeepsItsTextReadable() {
        BunSkin.entries.forEach { bun ->
            val p = Skin.paletteOf(bun)
            assertTrue("$bun body text", contrast(p.text, p.base) > 12f)
            assertTrue("$bun muted text", contrast(p.muted, p.base) > 3.5f)
            assertTrue("$bun button label", contrast(p.onAccent, p.accent) > 4.5f)
            assertTrue("$bun mini player", contrast(p.miniText, p.miniBg) > 7f)
            assertTrue("$bun hero ink", contrast(p.heroInk, p.hero.first()) > 3f)
        }
    }

    @Test
    fun unknownIdFallsBackToMidnight() {
        assertEquals(BunSkin.MIDNIGHT, BunSkin.of("CHARCOAL-NOT-REAL"))
        assertEquals(BunSkin.MIDNIGHT, BunSkin.of(null))
        assertEquals(BunSkin.CHARCOAL, BunSkin.of("CHARCOAL"))
    }

    /**
     * Two buns sharing an alias or an icon would leave one of them unlaunchable.
     * The previews are built from the two adaptive layers, never from the
     * adaptive-icon XML itself — that XML is an AdaptiveIconDrawable and Compose
     * cannot paint it (the 1.0.7 Settings crash).
     */
    @Test
    fun everyBunHasItsOwnLauncherEntry() {
        val aliases = BunSkin.entries.map { Icons.aliasOf(it) }
        val icons = BunSkin.entries.map { Icons.layerOf(it) }
        val backgrounds = BunSkin.entries.map { Icons.backgroundOf(it) }
        assertEquals(BunSkin.entries.size, aliases.toSet().size)
        assertEquals(BunSkin.entries.size, icons.toSet().size)
        assertEquals(BunSkin.entries.size, backgrounds.toSet().size)
        aliases.forEach { assertTrue(it.startsWith(".")) }
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
