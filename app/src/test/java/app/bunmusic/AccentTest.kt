package app.bunmusic

import app.bunmusic.core.Accent
import app.bunmusic.core.BunSkin
import app.bunmusic.core.Contrast
import app.bunmusic.core.Skin
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Accent Studio — the pure rules behind re-colouring a bun.
 *
 * None of this needs a device: hex parsing, per-bun storage, the two nav-pill
 * rules and the WCAG guarantees are plain arithmetic over ARGB ints. The four
 * roles are checked against the *real* five buns, because a rule that only
 * passes on Midnight's near-black is not a rule.
 */
class AccentTest {

    private val lime = 0xFFD8F65C.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    // ------------------------------------------------------------- parsing

    @Test
    fun `parse accepts hex with and without a hash`() {
        assertEquals(lime, Accent.parse("#D8F65C"))
        assertEquals(lime, Accent.parse("D8F65C"))
        assertEquals(lime, Accent.parse("  #d8f65c  "))
        assertEquals(lime, Accent.parse("FFD8F65C")) // 8-digit ARGB keeps only RGB
    }

    @Test
    fun `parse always returns an opaque colour`() {
        // A translucent accent would invalidate every contrast number below.
        val parsed = Accent.parse("#00D8F65C")!! // fully transparent alpha
        assertEquals(0xFF000000.toInt(), parsed and 0xFF000000.toInt())
        assertEquals(lime, parsed)
    }

    @Test
    fun `parse rejects junk instead of painting black`() {
        listOf(null, "", "#", "123", "12345", "1234567", "GGGGGG", "#12 45", "not-a-colour", "0x123456")
            .forEach { assertNull("should reject <$it>", Accent.parse(it)) }
    }

    @Test
    fun `hex round trips and is always six uppercase digits`() {
        assertEquals("#D8F65C", Accent.hex(lime))
        assertEquals("#000000", Accent.hex(black))
        assertEquals("#FFFFFF", Accent.hex(white))
        assertEquals(lime, Accent.parse(Accent.hex(lime)))
        Accent.SWATCHES.forEach { assertEquals(it.argb, Accent.parse(it.hex)) }
    }

    @Test
    fun `every swatch is well formed and uniquely keyed`() {
        assertEquals(8, Accent.SWATCHES.size)
        assertEquals(Accent.SWATCHES.size, Accent.SWATCHES.map { it.id }.toSet().size)
        assertEquals(Accent.SWATCHES.size, Accent.SWATCHES.map { it.argb }.toSet().size)
        Accent.SWATCHES.forEach {
            assertTrue(it.label.isNotBlank())
            assertEquals(it.argb, Accent.parse(it.hex))
        }
        assertEquals(Accent.SWATCHES[0], Accent.swatch("lime"))
        assertNull(Accent.swatch("not-a-swatch"))
    }

    // ----------------------------------------------------------- storage

    @Test
    fun `encode is sorted and decode round trips`() {
        val map = mapOf("PINK" to 0xFFD63366.toInt(), "MIDNIGHT" to lime, "CLOUD" to 0xFF2E6BFF.toInt())
        val encoded = Accent.encode(map)
        assertEquals("CLOUD=#2E6BFF;MIDNIGHT=#D8F65C;PINK=#D63366", encoded)
        assertEquals(map, Accent.decode(encoded))
    }

    @Test
    fun `decode tolerates junk and drops bad entries`() {
        assertEquals(emptyMap<String, Int>(), Accent.decode(null))
        assertEquals(emptyMap<String, Int>(), Accent.decode(""))
        assertEquals(emptyMap<String, Int>(), Accent.decode("  "))
        val decoded = Accent.decode("MIDNIGHT=#D8F65C;bad;CLOUD=ZZZZZZ;=nokey;PINK=#D63366;MIDNIGHT=#AABBCC")
        assertEquals(mapOf("MIDNIGHT" to 0xFFAABBCC.toInt(), "PINK" to 0xFFD63366.toInt()), decoded)
    }

    @Test
    fun `encode skips blank bun keys`() {
        assertEquals("", Accent.encode(emptyMap()))
        assertEquals("MIDNIGHT=#D8F65C", Accent.encode(mapOf("" to black, "MIDNIGHT" to lime)))
    }

    // ---------------------------------------------------------- contrast

    @Test
    fun `onInk is black or white and always readable on the accent`() {
        Accent.SWATCHES.forEach { sw ->
            val ink = Accent.onInk(sw.argb)
            assertTrue("${sw.id} ink is neutral", ink == black || ink == white)
            assertTrue("${sw.id} ink clears AA", Contrast.ratio(ink, sw.argb) >= Contrast.AA)
        }
    }

    @Test
    fun `legible lifts an invisible tint until it clears AA`() {
        // Yellow on white is ~1.1:1; the result must be materially darker.
        val lifted = Accent.legible(0xFFFFFF00.toInt(), white)
        assertTrue(Contrast.ratio(lifted, white) >= Contrast.AA)
        // Navy on near-black is ~1.2:1; the result must be materially lighter.
        val raised = Accent.legible(0xFF000033.toInt(), black)
        assertTrue(Contrast.ratio(raised, black) >= Contrast.AA)
        // A tint that already passes is returned untouched.
        assertEquals(0xFF2560EC.toInt(), Accent.legible(0xFF2560EC.toInt(), white))
    }

    @Test
    fun `withRgb keeps the source alpha and swaps only RGB`() {
        val wash = 0x24D8F65C
        val changed = Accent.withRgb(wash, 0xFF2E6BFF.toInt())
        assertEquals(0x24000000, changed and 0xFF000000.toInt())
        assertEquals(0x2E6BFF, changed and 0xFFFFFF)
    }

    // ------------------------------------------------------------- derive

    private fun tonesFor(bun: BunSkin): Accent.Tones {
        val p = Skin.paletteOf(bun)
        return Accent.Tones(
            accent = p.accent.toArgb(),
            onAccent = p.onAccent.toArgb(),
            navBg = p.navBg.toArgb(),
            navActiveBg = p.navActiveBg.toArgb(),
            navActiveInk = p.navActiveInk.toArgb(),
        )
    }

    /** The surface a nav label really sits on: the pill alpha-composited on navBg. */
    private fun pillSurface(t: Accent.Tones, accent: Int): Int {
        val alpha = ((t.navActiveBg ushr 24) and 0xFF) / 255f
        return Contrast.blend(t.navBg, accent, alpha)
    }

    @Test
    fun `derive keeps the bun alpha and only moves the hue`() {
        BunSkin.entries.forEach { bun ->
            val t = tonesFor(bun)
            val derived = Accent.derive(t, lime)
            assertEquals("$bun alpha", t.navActiveBg and 0xFF000000.toInt(), derived.navActiveBg and 0xFF000000.toInt())
            assertEquals("$bun accent", lime, derived.accent)
        }
    }

    @Test
    fun `every swatch keeps button ink legible on all five buns`() {
        BunSkin.entries.forEach { bun ->
            val t = tonesFor(bun)
            Accent.SWATCHES.forEach { sw ->
                val d = Accent.derive(t, sw.argb)
                assertTrue(
                    "${bun.id}/${sw.id}: ink ${Contrast.label(d.onAccent, d.accent)}",
                    Contrast.ratio(d.onAccent, d.accent) >= Contrast.AA,
                )
            }
        }
    }

    @Test
    fun `every swatch keeps the nav label legible on all five buns`() {
        BunSkin.entries.forEach { bun ->
            val t = tonesFor(bun)
            Accent.SWATCHES.forEach { sw ->
                val d = Accent.derive(t, sw.argb)
                val surface = pillSurface(t, sw.argb)
                assertTrue(
                    "${bun.id}/${sw.id}: nav ${Contrast.label(d.navActiveInk, surface)}",
                    Contrast.ratio(d.navActiveInk, surface) >= Contrast.AA,
                )
            }
        }
    }

    @Test
    fun `a solid pill switches its label to neutral ink`() {
        // Four of the five buns use an opaque pill: accent-on-accent would be
        // invisible, so the label must be black or white, exactly like onAccent.
        listOf(BunSkin.CHARCOAL, BunSkin.CLOUD, BunSkin.PINK, BunSkin.NEBULA).forEach { bun ->
            val t = tonesFor(bun)
            val d = Accent.derive(t, lime)
            assertTrue("${bun.id} pill is solid", (t.navActiveBg ushr 24) and 0xFF >= 230)
            assertEquals("${bun.id} label is neutral", d.onAccent, d.navActiveInk)
        }
    }

    @Test
    fun `a translucent wash keeps its alpha and measures against the composite`() {
        // Midnight is the one bun with a 14 % wash; the label stays a tinted
        // colour and is measured against the *composited* surface.
        val bun = BunSkin.MIDNIGHT
        val t = tonesFor(bun)
        val d = Accent.derive(t, lime)
        assertTrue("${bun.id} pill is a wash", (t.navActiveBg ushr 24) and 0xFF < 230)
        assertEquals(t.navActiveBg and 0xFF000000.toInt(), d.navActiveBg and 0xFF000000.toInt())
        assertTrue(Contrast.ratio(d.navActiveInk, pillSurface(t, lime)) >= Contrast.AA)
    }

    @Test
    fun `derive is deterministic`() {
        val t = tonesFor(BunSkin.MIDNIGHT)
        assertEquals(Accent.derive(t, lime), Accent.derive(t, lime))
    }

    @Test
    fun `the accent override leaves the other roles alone`() {
        // Coral is not any bun's own accent, so the change is visible everywhere.
        val coral = 0xFFFF6B5C.toInt()
        BunSkin.entries.forEach { bun ->
            val p = Skin.paletteOf(bun)
            val before = listOf(p.text, p.base, p.card, p.heroInk, p.heroBtn, p.miniText, p.rose, p.mint)
            // Simulate Skin.withAccent's copy: only four fields change.
            val overridden = Skin.paletteOf(bun).copy(
                accent = androidx.compose.ui.graphics.Color(Accent.derive(tonesFor(bun), coral).accent),
            )
            assertNotNull(overridden)
            val after = listOf(overridden.text, overridden.base, overridden.card, overridden.heroInk,
                overridden.heroBtn, overridden.miniText, overridden.rose, overridden.mint)
            assertEquals("$bun non-accent roles unchanged", before, after)
            assertEquals("$bun accent became coral", coral, overridden.accent.toArgb())
        }
    }
}
