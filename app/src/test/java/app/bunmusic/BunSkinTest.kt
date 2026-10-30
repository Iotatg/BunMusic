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
     * Every tone that carries words has to clear WCAG AA — 4.5:1 — on the page
     * *and* on a card, because both are used as text backgrounds.
     *
     * This test used to allow muted at 3.5:1 and did not check faint at all.
     * Faint is the caption tone (timestamps, section overlines, hints) and it sat
     * at 2.0–2.9:1 in all four buns: legible in a mockup, invisible in daylight.
     * That is the bug this guards against.
     */
    @Test
    fun everyBunKeepsItsTextReadable() {
        BunSkin.entries.forEach { bun ->
            val p = Skin.paletteOf(bun)
            assertTrue("$bun body text", contrast(p.text, p.base) > 12f)
            assertTrue("$bun body text on card", contrast(p.text, p.card) > 12f)
            assertTrue("$bun muted text", contrast(p.muted, p.base) >= 4.5f)
            assertTrue("$bun muted text on card", contrast(p.muted, p.card) >= 4.5f)
            assertTrue("$bun caption text", contrast(p.faint, p.base) >= 4.5f)
            assertTrue("$bun caption text on card", contrast(p.faint, p.card) >= 4.5f)
            assertTrue("$bun accent as text", contrast(p.accent, p.base) >= 4.5f)
            assertTrue("$bun button label", contrast(p.onAccent, p.accent) >= 4.5f)
            assertTrue("$bun nav pill", contrast(p.navActiveInk, over(p.navActiveBg, p.navBg)) >= 4.5f)
            assertTrue("$bun mini player", contrast(p.miniText, p.miniBg) > 7f)
            assertTrue("$bun hero ink", contrast(over(p.heroInk, p.hero.first()), p.hero.first()) >= 4.5f)
            assertTrue("$bun hero sub", contrast(over(p.heroSub, p.hero.first()), p.hero.first()) >= 4.5f)
            assertTrue("$bun hero button", contrast(p.heroBtnInk, p.heroBtn) >= 4.5f)
        }
    }

    /** A pure-white page is the harshest light surface there is, and it erases cards. */
    @Test
    fun noBunShipsAPureWhitePage() {
        BunSkin.entries.filter { Skin.paletteOf(it).light }.forEach { bun ->
            val p = Skin.paletteOf(bun)
            assertNotEquals("$bun page", 0xFFFFFFFF.toInt(), p.base.value.toLong().toInt())
            // A card has to be distinguishable from the page it sits on.
            assertTrue("$bun card edge", contrast(p.card, p.base) > 1.03f)
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

    /** Alpha-composite a foreground over an opaque backdrop, the way it renders. */
    private fun over(fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color) =
        androidx.compose.ui.graphics.Color(
            red = fg.red * fg.alpha + bg.red * (1 - fg.alpha),
            green = fg.green * fg.alpha + bg.green * (1 - fg.alpha),
            blue = fg.blue * fg.alpha + bg.blue * (1 - fg.alpha),
        )

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
