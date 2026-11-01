package app.bunmusic

import app.bunmusic.core.Palette
import app.bunmusic.core.Skin
import app.bunmusic.core.BunSkin
import app.bunmusic.widget.GlassPalette
import app.bunmusic.widget.WidgetLook
import app.bunmusic.widget.WidgetShape
import app.bunmusic.widget.WidgetSkin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Widget configuration beyond the round trip, and the one palette that is not a
 * bun.
 *
 * The glass widget is the only surface in the app that ignores the theme, which
 * makes it the only one that can be illegible on a wallpaper nobody anticipated.
 */
class WidgetLookTest {

    @Test
    fun `the default look is what an unconfigured widget gets`() {
        assertEquals(WidgetLook.Default, WidgetLook.decode(null))
        assertEquals(WidgetSkin.AUTO, WidgetLook.Default.skin)
        assertEquals(WidgetShape.ROUND, WidgetLook.Default.shape)
        assertTrue(WidgetLook.Default.progress)
        assertTrue(WidgetLook.Default.queue)
    }

    @Test
    fun `every flag can be turned off on its own`() {
        val base = WidgetLook.Default
        for (i in 0..3) {
            val look = when (i) {
                0 -> base.copy(progress = false)
                1 -> base.copy(times = false)
                2 -> base.copy(like = false)
                else -> base.copy(queue = false)
            }
            val back = WidgetLook.decode(look.encode())
            assertEquals("flag $i", look, back)
            assertNotEquals("flag $i must differ from the default", base, back)
        }
    }

    @Test
    fun `an encoded look is six fields`() {
        assertEquals(6, WidgetLook.Default.encode().split("|").size)
    }

    @Test
    fun `artwork corners are always tighter than the card`() {
        // A square cover in a pill card has to be rounded or it pokes through.
        WidgetShape.entries.forEach { shape ->
            assertTrue("$shape art radius", shape.artRadiusDp < shape.radiusDp)
        }
        assertTrue(WidgetShape.SHARP.radiusDp < WidgetShape.ROUND.radiusDp)
        assertTrue(WidgetShape.ROUND.radiusDp < WidgetShape.PILL.radiusDp)
    }

    @Test
    fun `every skin but auto and glass pins a real bun`() {
        val pinned = WidgetSkin.entries.filter { it != WidgetSkin.AUTO && it != WidgetSkin.GLASS }
        assertEquals(4, pinned.size)
        pinned.forEach { assertTrue("$it has no bun", it.bun() != null) }
        assertEquals(BunSkin.CLOUD, WidgetSkin.CLOUD.bun())
        assertEquals(BunSkin.PINK, WidgetSkin.PINK.bun())
    }

    @Test
    fun `glass does not follow the bun`() {
        // It is theme-independent by design: a smoked panel reads on any wallpaper.
        val glass = WidgetLook(skin = WidgetSkin.GLASS).palette
        assertEquals(GlassPalette, glass)
        assertFalse("glass is a dark surface", glass.light)
    }

    @Test
    fun `glass keeps its text legible on its own card`() {
        val p = GlassPalette
        val text = Palette.luminance(p.text)
        val card = Palette.luminance(p.card)
        assertTrue("text must be much lighter than the panel", text - card > 0.6f)
    }

    @Test
    fun `a skin that is not glass borrows the bun it names`() {
        val midnight = WidgetLook(skin = WidgetSkin.MIDNIGHT).palette
        assertEquals(Skin.paletteOf(BunSkin.MIDNIGHT), midnight)
        assertNotEquals(midnight, GlassPalette)
    }
}
