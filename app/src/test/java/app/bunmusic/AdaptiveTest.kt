package app.bunmusic

import androidx.compose.ui.unit.dp
import app.bunmusic.core.BunHeight
import app.bunmusic.core.BunWidth
import app.bunmusic.core.BunWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The breakpoints are the whole contract: get them wrong and a phone gets a
 * navigation rail, or a tablet gets a 124 dp card floating in 1200 dp of page.
 * These are pure functions of the window size, so they can be tested without a
 * device — which is the only way they get tested at all here.
 */
class AdaptiveTest {

    private fun window(w: Int, h: Int) = BunWindow(w, h)

    @Test
    fun everyPhoneStaysCompact() {
        listOf(
            320 to 480,   // small / old
            360 to 800,   // the reference phone
            411 to 891,   // Pixel-ish
            480 to 1040,  // large phone
        ).forEach { (w, h) ->
            val x = window(w, h)
            assertEquals("${w}x$h width", BunWidth.COMPACT, x.width)
            assertFalse("${w}x$h must keep the bottom bar", x.wide)
            assertFalse("${w}x$h must not split the player", x.twoPane)
            assertEquals("${w}x$h gutter", 20.dp, x.gutter)
            assertEquals("${w}x$h content is uncapped", androidx.compose.ui.unit.Dp.Infinity, x.contentMax)
        }
    }

    @Test
    fun sixHundredIsWhereTheRailStarts() {
        assertFalse(window(599, 900).wide)
        assertTrue(window(600, 900).wide)
        assertEquals(BunWidth.MEDIUM, window(600, 900).width)
        assertEquals(BunWidth.MEDIUM, window(839, 900).width)
        assertEquals(BunWidth.EXPANDED, window(840, 900).width)
    }

    @Test
    fun aLandscapePhoneIsWideButShort() {
        val x = window(891, 411)
        assertEquals(BunWidth.EXPANDED, x.width)
        assertEquals(BunHeight.SHORT, x.height)
        assertTrue(x.landscape)
        assertTrue("no room to stack, so split", x.twoPane)
    }

    @Test
    fun aPortraitTabletKeepsOneColumn() {
        val medium = window(800, 1280)
        assertEquals(BunWidth.MEDIUM, medium.width)
        assertFalse("portrait has the height to stack", medium.twoPane)

        val big = window(1024, 1366)
        assertEquals(BunWidth.EXPANDED, big.width)
        assertFalse("portrait has the height to stack", big.twoPane)
    }

    @Test
    fun readingColumnNeverStretchesToTheBezels() {
        assertEquals(720.dp, window(700, 900).contentMax)
        assertEquals(1040.dp, window(1280, 800).contentMax)
        assertEquals(560.dp, window(1280, 800).readingMax)
    }

    @Test
    fun tilesGrowWithTheWindow() {
        val phone = window(360, 800).tile(124.dp).value
        val small = window(700, 900).tile(124.dp).value
        val large = window(1280, 800).tile(124.dp).value
        assertEquals(124f, phone, 0.01f)
        assertTrue("medium must be bigger than phone", small > phone)
        assertTrue("expanded must be bigger than medium", large > small)
    }

    @Test
    fun theMoodGridWidensWithThePage() {
        assertEquals(2, window(411, 891).columns(150.dp))
        assertEquals(3, window(600, 960).columns(150.dp))
        assertEquals(4, window(700, 960).columns(150.dp))
        assertEquals("four is the ceiling, five is a spreadsheet", 4, window(1280, 800).columns(150.dp))
    }

    @Test
    fun artworkIsOnlyCappedWhenHeightIsTheBindingConstraint() {
        assertEquals(
            "a tall phone keeps its width-driven artwork",
            androidx.compose.ui.unit.Dp.Infinity,
            window(411, 891).artCap(),
        )
        val short = window(891, 411).artCap().value
        assertTrue("landscape phone artwork must fit the height", abs(short - 189.06f) < 0.2f)
    }

    @Test
    fun theRailLeavesRoomForContent() {
        // A 600 dp window minus the 92 dp rail and its 12 dp inset still has to
        // fit a usable column — otherwise the rail is a downgrade on small tablets.
        val usable = 600 - window(600, 960).railWidth.value - 12f
        assertTrue("rail leaves $usable dp", usable >= 480f)
    }
}
