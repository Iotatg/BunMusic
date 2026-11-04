package app.bunmusic

import app.bunmusic.core.SwipeSkip
import app.bunmusic.data.HistoryItem
import app.bunmusic.data.Library
import app.bunmusic.data.Track
import app.bunmusic.player.Crossfade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two pieces of 1.2.0 that are pure arithmetic: the crossfade curve and the
 * auto playlists. Both decide what the listener hears, so both are pinned.
 */
class MotionTest {

    private fun t(id: String, title: String = id, artist: String = "A") =
        Track(id = id, title = title, artist = artist)

    // ------------------------------------------------------------- crossfade

    @Test
    fun `fade is off at zero`() {
        assertEquals(1f, Crossfade.gain(5_000, 200_000, 0), 0.0001f)
    }

    @Test
    fun `middle of a track is untouched`() {
        assertEquals(1f, Crossfade.gain(100_000, 200_000, 6_000), 0.0001f)
    }

    @Test
    fun `the end ramps to silence`() {
        // 1 s before the end of a 6 s fade is a sixth of the way up the curve.
        val nearEnd = Crossfade.gain(199_000, 200_000, 6_000)
        assertTrue("expected a fade near the end, got $nearEnd", nearEnd < 0.2f)
        assertEquals(0f, Crossfade.gain(200_000, 200_000, 6_000), 0.0001f)
    }

    @Test
    fun `the start ramps up from silence`() {
        assertEquals(0f, Crossfade.gain(0, 200_000, 6_000), 0.0001f)
        val early = Crossfade.gain(3_000, 200_000, 6_000)
        assertTrue("expected a half-way fade, got $early", early in 0.4f..0.6f)
    }

    @Test
    fun `a track shorter than two fades does not cancel itself out`() {
        // 4 s track, 6 s fade: both fades halve to 2 s, so the middle still plays.
        val mid = Crossfade.gain(2_000, 4_000, 6_000)
        assertTrue("the middle of a short track must not be silent, got $mid", mid > 0.9f)
    }

    @Test
    fun `gain never leaves zero to one`() {
        for (pos in listOf(-1L, 0L, 1L, 500L, 1_000L, 50_000L, 99_999L, 100_000L)) {
            val g = Crossfade.gain(pos, 100_000, 6_000)
            assertTrue("gain out of range at $pos: $g", g in 0f..1f)
        }
    }

    // --------------------------------------------------------- auto playlists

    @Test
    fun `rank orders by play count and drops unplayed`() {
        val tracks = listOf(t("a"), t("b"), t("c"))
        val ranked = Library.rank(mapOf("a" to 2, "c" to 9), tracks)
        assertEquals(listOf("c", "a"), ranked.map { it.id })
    }

    @Test
    fun `on repeat counts only inside the window`() {
        val now = 1_700_000_000_000L
        val day = 86_400_000L
        val history = listOf(
            HistoryItem(t("x"), now - day),
            HistoryItem(t("x"), now - day * 2),
            HistoryItem(t("y"), now - day * 40),
            HistoryItem(t("y"), now - day * 41),
        )
        val repeat = Library.onRepeat(history, days = 7, min = 2, now = now)
        assertEquals(listOf("x"), repeat.map { it.id })
    }

    @Test
    fun `forgotten excludes anything played recently`() {
        val now = 1_700_000_000_000L
        val day = 86_400_000L
        val liked = listOf(t("old"), t("fresh"))
        val history = listOf(
            HistoryItem(t("fresh"), now - day),
            HistoryItem(t("old"), now - day * 60),
        )
        val forgotten = Library.forgotten(liked, history, days = 30, now = now)
        assertEquals(listOf("old"), forgotten.map { it.id })
    }

    @Test
    fun `a liked track never played counts as forgotten`() {
        val forgotten = Library.forgotten(listOf(t("never")), emptyList(), now = 1_700_000_000_000L)
        assertEquals(listOf("never"), forgotten.map { it.id })
    }
    // ------------------------------------------------------------ swipe skip

    @Test
    fun `a short drag puts the strip back`() {
        assertEquals(0, SwipeSkip.direction(40f, 400f))
        assertTrue(!SwipeSkip.commits(40f, 400f))
    }

    @Test
    fun `a third of the width commits, and the sign picks the track`() {
        // Dragging left is "next" — the strip moves the way the music is going.
        assertEquals(-1, SwipeSkip.direction(-140f, 400f))
        // Dragging right is "previous".
        assertEquals(1, SwipeSkip.direction(140f, 400f))
    }

    @Test
    fun `the threshold is a third, not a pixel count`() {
        // The same drag commits on a phone strip and not on a tablet one, which
        // is the point of measuring in fractions: 120 dp is most of a phone and a
        // sliver of a tablet.
        assertTrue(SwipeSkip.commits(120f, 300f))
        assertTrue(!SwipeSkip.commits(120f, 900f))
    }

    @Test
    fun `the strip cannot be dragged off the screen`() {
        assertEquals(400f * SwipeSkip.LIMIT, SwipeSkip.clamp(9_999f, 400f), 0.01f)
        assertEquals(-400f * SwipeSkip.LIMIT, SwipeSkip.clamp(-9_999f, 400f), 0.01f)
        assertEquals(0f, SwipeSkip.clamp(0f, 400f), 0.01f)
    }

    @Test
    fun `an unmeasured strip does not divide by zero`() {
        // The first frame has no width yet. A threshold of NaN would make every
        // gesture commit and every skip fire on the first pixel of a drag.
        assertEquals(0f, SwipeSkip.clamp(50f, 0f), 0f)
        assertTrue(!SwipeSkip.commits(50f, 0f))
        assertEquals(0, SwipeSkip.direction(50f, 0f))
        assertEquals(1f, SwipeSkip.alpha(50f, 0f), 0f)
    }

    @Test
    fun `the strip fades but never disappears before it commits`() {
        // Fully pulled, it is still 70 % opaque: a row that goes transparent looks
        // like it has already gone, and then it springs back.
        assertEquals(1f, SwipeSkip.alpha(0f, 400f), 0.0001f)
        val pulled = SwipeSkip.alpha(-400f, 400f)
        assertTrue(pulled in 0.69f..0.71f)
        assertTrue(SwipeSkip.alpha(-800f, 400f) >= 0.69f)
    }

}
