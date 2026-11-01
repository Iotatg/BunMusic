package app.bunmusic

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.bunmusic.core.BunHeight
import app.bunmusic.core.BunWidth
import app.bunmusic.core.BunWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rest of the window contract: heights, gutters, caps and the rails.
 *
 * [AdaptiveTest] covers where the breakpoints are; this file covers what each
 * breakpoint then *does*, because a breakpoint that resolves to the wrong gutter
 * or the wrong column count is a layout bug that only shows up on the one device
 * nobody owns.
 */
class WindowTest {

    @Test
    fun `the default window is the reference phone`() {
        val w = BunWindow()
        assertEquals(360, w.widthDp)
        assertEquals(800, w.heightDp)
        assertEquals(BunWidth.COMPACT, w.width)
    }

    @Test
    fun `height has three bands and they meet where they should`() {
        assertEquals(BunHeight.SHORT, BunWindow(400, 479).height)
        assertEquals(BunHeight.REGULAR, BunWindow(400, 480).height)
        assertEquals(BunHeight.REGULAR, BunWindow(400, 899).height)
        assertEquals(BunHeight.TALL, BunWindow(400, 900).height)
    }

    @Test
    fun `a square window is not landscape`() {
        // Tablets and foldables sit at 1:1 more often than phones do; treating a
        // square as landscape would split the player for no gain.
        assertFalse(BunWindow(600, 600).landscape)
        assertTrue(BunWindow(601, 600).landscape)
    }

    @Test
    fun `two panes need both width and landscape`() {
        assertFalse("a portrait tablet has the height to stack", BunWindow(1024, 1366).twoPane)
        assertTrue("a landscape tablet has room side by side", BunWindow(1366, 1024).twoPane)
        assertFalse("a portrait phone never splits", BunWindow(411, 891).twoPane)
    }

    @Test
    fun `the gutter widens with the page`() {
        assertEquals(20.dp, BunWindow(360, 800).gutter)
        assertEquals(28.dp, BunWindow(600, 960).gutter)
        assertEquals(36.dp, BunWindow(1280, 800).gutter)
    }

    @Test
    fun `the reading column is capped only where it would stretch`() {
        assertEquals(Dp.Infinity, BunWindow(360, 800).readingMax)
        assertEquals(560.dp, BunWindow(600, 960).readingMax)
        assertEquals("a wide page does not widen the reading column", 560.dp, BunWindow(1280, 800).readingMax)
    }

    @Test
    fun `tiles grow by a fixed factor not by a guess`() {
        assertEquals(100f, BunWindow(360, 800).tile(100.dp).value, 0.01f)
        assertEquals(112f, BunWindow(700, 900).tile(100.dp).value, 0.01f)
        assertEquals(124f, BunWindow(1280, 800).tile(100.dp).value, 0.01f)
    }

    @Test
    fun `the grid never drops below two columns`() {
        // One column of mood tiles on a 320 dp phone is a list, not a grid.
        assertEquals(2, BunWindow(320, 640).columns(300.dp))
        assertEquals(2, BunWindow(360, 800).columns(160.dp))
    }

    @Test
    fun `the grid never grows past four columns`() {
        assertEquals(4, BunWindow(1600, 1000).columns(80.dp))
        assertEquals(4, BunWindow(1280, 800).columns(100.dp))
    }

    @Test
    fun `a wider gap fits fewer columns`() {
        val tight = BunWindow(700, 960).columns(150.dp, gap = 8.dp)
        val loose = BunWindow(700, 960).columns(150.dp, gap = 40.dp)
        assertTrue("tight $tight should fit at least as many as loose $loose", tight >= loose)
    }

    @Test
    fun `the gutter is part of the column arithmetic`() {
        // Same window, same tile: a bigger gutter has to reduce the count or the
        // grid overflows the page it was sized for.
        // 600 dp less two 28 dp gutters leaves 544 dp: three 150 dp tiles and
        // their gaps, not four.
        assertEquals(3, BunWindow(600, 960).columns(150.dp, gap = 10.dp))
    }

    @Test
    fun `artwork is uncapped in portrait and capped in landscape`() {
        assertEquals(Dp.Infinity, BunWindow(411, 891).artCap())
        val cap = BunWindow(891, 411).artCap().value
        assertTrue("cap $cap should be under half the height", cap < 411 * 0.5f)
        assertTrue("cap $cap should still be usable", cap > 100f)
    }

    @Test
    fun `the chrome widths are fixed constants`() {
        assertEquals(92.dp, BunWindow(600, 960).railWidth)
        assertEquals(560.dp, BunWindow(1280, 800).navMax)
        assertEquals(780.dp, BunWindow(1280, 800).miniMax)
    }

    @Test
    fun `a phone keeps the bottom bar and a tablet does not`() {
        assertFalse(BunWindow(360, 800).wide)
        assertFalse(BunWindow(599, 800).wide)
        assertTrue(BunWindow(600, 800).wide)
        assertTrue(BunWindow(1280, 800).wide)
    }

    @Test
    fun `a landscape phone is short but still wide`() {
        val w = BunWindow(891, 411)
        assertTrue(w.short)
        assertTrue(w.wide)
        assertFalse("short is a height fact, not a width one", BunWindow(360, 411).wide)
        assertTrue(BunWindow(360, 411).short)
    }
}
