package app.bunmusic

import androidx.media3.session.CommandButton
import app.bunmusic.player.PlayerState
import app.bunmusic.player.SessionGlyphs
import app.bunmusic.player.SessionControls
import app.bunmusic.data.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lock screen's rules, tested as arithmetic.
 *
 * There is no emulator here, so nothing in this file proves the notification
 * *looks* right. What it does prove is the part that used to be wrong and that a
 * screenshot would not have caught either: whether the session offers a skip
 * button at all, and whether the numbers under the bar add up.
 */
class SessionControlsTest {

    private fun track(id: String) = Track(id = id, title = "Track $id", artist = "Artist", videoId = id)

    private fun state(
        queue: List<Track> = emptyList(),
        index: Int = -1,
        repeat: Int = 0,
        shuffle: Boolean = false,
    ) = PlayerState(
        track = queue.getOrNull(index),
        queue = queue,
        index = index,
        repeat = repeat,
        shuffle = shuffle,
    )

    // ---------------------------------------------------------------- skipping

    @Test
    fun `an empty queue offers neither skip`() {
        val s = state()
        assertFalse(SessionControls.hasNext(s))
        assertFalse(SessionControls.hasPrev(s))
    }

    @Test
    fun `one track with repeat off has no next`() {
        // This is the bug the lock screen showed: a next button that did nothing.
        val s = state(listOf(track("a")), 0)
        assertFalse(SessionControls.hasNext(s))
    }

    @Test
    fun `one track with repeat all does have a next, because it restarts`() {
        val s = state(listOf(track("a")), 0, repeat = 1)
        assertTrue(SessionControls.hasNext(s))
    }

    @Test
    fun `a queue offers next until the end`() {
        val q = listOf(track("a"), track("b"), track("c"))
        assertTrue(SessionControls.hasNext(state(q, 0)))
        assertTrue(SessionControls.hasNext(state(q, 1)))
        assertFalse(SessionControls.hasNext(state(q, 2)))
    }

    @Test
    fun `repeat all keeps next alive on the last track`() {
        val q = listOf(track("a"), track("b"))
        assertTrue(SessionControls.hasNext(state(q, 1, repeat = 1)))
    }

    @Test
    fun `previous is offered whenever something is loaded, because it restarts`() {
        val q = listOf(track("a"), track("b"))
        assertTrue(SessionControls.hasPrev(state(q, 0)))
        assertTrue(SessionControls.hasPrev(state(q, 1)))
        assertTrue(SessionControls.hasPrev(state(listOf(track("a")), 0)))
    }

    @Test
    fun `shuffle does not invent a next out of nothing`() {
        val s = state(listOf(track("a")), 0, shuffle = true)
        assertFalse(SessionControls.hasNext(s))
    }

    // ---------------------------------------------------------------- progress

    @Test
    fun `progress is a permille of the track`() {
        assertEquals(0, SessionControls.progressPermille(0, 120_000))
        assertEquals(250, SessionControls.progressPermille(30_000, 120_000))
        assertEquals(1000, SessionControls.progressPermille(120_000, 120_000))
    }

    @Test
    fun `an unknown length reports no progress instead of dividing by zero`() {
        assertEquals(0, SessionControls.progressPermille(5_000, 0))
        assertEquals(0, SessionControls.progressPermille(5_000, -1))
    }

    @Test
    fun `a position past the end is clamped, not printed as 1200`() {
        assertEquals(1000, SessionControls.progressPermille(130_000, 120_000))
    }

    // ------------------------------------------------------------------- clock

    @Test
    fun `the clock pads seconds and drops the hour below one`() {
        assertEquals("0:00", SessionControls.clock(0))
        assertEquals("0:59", SessionControls.clock(59_999))
        assertEquals("1:05", SessionControls.clock(65_000))
        assertEquals("1:00:00", SessionControls.clock(3_600_000))
        assertEquals("1:02:05", SessionControls.clock(3_725_000))
    }

    @Test
    fun `a negative position reads as zero rather than -1`() {
        assertEquals("0:00", SessionControls.clock(-5_000))
    }

    @Test
    fun `remaining counts down and admits when it does not know`() {
        assertEquals("-0:45", SessionControls.remaining(15_000, 60_000))
        assertEquals("-0:00", SessionControls.remaining(70_000, 60_000))
        assertEquals("–:––", SessionControls.remaining(15_000, 0))
    }

    // ------------------------------------------------------------ button glyphs

    @Test
    fun `repeat has three distinct glyphs, one per mode`() {
        assertEquals(CommandButton.ICON_REPEAT_OFF, SessionGlyphs.repeatIcon(0))
        assertEquals(CommandButton.ICON_REPEAT_ALL, SessionGlyphs.repeatIcon(1))
        assertEquals(CommandButton.ICON_REPEAT_ONE, SessionGlyphs.repeatIcon(2))
        // Anything out of range falls back to "off" rather than crashing.
        assertEquals(CommandButton.ICON_REPEAT_OFF, SessionGlyphs.repeatIcon(9))
    }

    @Test
    fun `like and shuffle glyphs follow the state they describe`() {
        assertEquals(CommandButton.ICON_HEART_FILLED, SessionGlyphs.likeIcon(true))
        assertEquals(CommandButton.ICON_HEART_UNFILLED, SessionGlyphs.likeIcon(false))
        assertEquals(CommandButton.ICON_SHUFFLE_ON, SessionGlyphs.shuffleIcon(true))
        assertEquals(CommandButton.ICON_SHUFFLE_OFF, SessionGlyphs.shuffleIcon(false))
    }

    @Test
    fun `labels say what pressing the button will do, not what is true now`() {
        assertEquals("Like", SessionGlyphs.likeLabel(false))
        assertEquals("Unlike", SessionGlyphs.likeLabel(true))
        assertEquals("Turn shuffle off", SessionGlyphs.shuffleLabel(true))
        assertEquals("Turn shuffle on", SessionGlyphs.shuffleLabel(false))
        assertEquals("Repeat one track", SessionGlyphs.repeatLabel(2))
        assertEquals("Turn repeat on", SessionGlyphs.repeatLabel(0))
    }
}
