package app.bunmusic

import app.bunmusic.data.Track
import app.bunmusic.player.NotificationPolicy
import app.bunmusic.player.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that keep the card live, tested without a service or a session.
 *
 * The old watcher republished only on `isPlaying && secondChanged`. A like, a
 * pause, a track change or a buffering stall drew nothing, and Media3's
 * shouldShowNotification() re-check on the next update could cancel the card.
 */
class NotificationLiveTest {

    private fun track(id: String = "t1") = Track(id = id, title = "Song", artist = "Artist")

    private fun state(
        track: Track? = track(),
        playing: Boolean = true,
        buffering: Boolean = false,
        positionMs: Long = 0L,
        durationMs: Long = 200_000L,
        queue: List<Track> = listOf(track()),
        liked: Boolean = false,
    ) = PlayerState(
        track = track,
        isPlaying = playing,
        buffering = buffering,
        positionMs = positionMs,
        durationMs = durationMs,
        queue = queue,
    ).let { NotificationPolicy.Shape.of(it, liked) }

    @Test
    fun `a track is live in every playback state`() {
        assertTrue(NotificationPolicy.shouldBeLive(hasTrack = true))
        assertFalse(NotificationPolicy.shouldBeLive(hasTrack = false))
        // Paused, buffering and restored-but-unprepared all still have a track.
        assertTrue(NotificationPolicy.shouldBeLive(state(playing = false).trackId != null))
        assertTrue(NotificationPolicy.shouldBeLive(state(buffering = true).trackId != null))
    }

    @Test
    fun `seconds are bucketed and never negative`() {
        assertEquals(0L, NotificationPolicy.secondOf(-5_000L))
        assertEquals(0L, NotificationPolicy.secondOf(999L))
        assertEquals(1L, NotificationPolicy.secondOf(1_000L))
        assertEquals(1L, NotificationPolicy.secondOf(1_999L))
        assertEquals(61L, NotificationPolicy.secondOf(61_842L))
    }

    @Test
    fun `a shape change republishes even while paused`() {
        // This is the case the old rule missed entirely: tapping like, shuffle,
        // repeat, or pausing has to redraw the card immediately.
        assertTrue(NotificationPolicy.shouldRepublish(live = true, shapeChanged = true, playing = false, secondChanged = false))
        assertTrue(NotificationPolicy.shouldRepublish(live = true, shapeChanged = true, playing = true, secondChanged = false))
    }

    @Test
    fun `a playing second still republishes`() {
        assertTrue(NotificationPolicy.shouldRepublish(live = true, shapeChanged = false, playing = true, secondChanged = true))
    }

    @Test
    fun `nothing is republished for an empty player or an unchanged paused frame`() {
        assertFalse(NotificationPolicy.shouldRepublish(live = false, shapeChanged = true, playing = true, secondChanged = true))
        assertFalse(NotificationPolicy.shouldRepublish(live = true, shapeChanged = false, playing = false, secondChanged = true))
        assertFalse(NotificationPolicy.shouldRepublish(live = true, shapeChanged = false, playing = true, secondChanged = false))
    }

    @Test
    fun `the shape notices every control the card draws`() {
        val base = state()
        assertNotEquals(base, state(playing = false))
        assertNotEquals(base, state(buffering = true))
        assertNotEquals(base, state(liked = true))
        assertNotEquals(base, state(durationMs = 240_000L))
        assertNotEquals(base, state(track = track("t2")))
        assertNotEquals(base, state(queue = listOf(track(), track("t2"))))
        assertEquals(base, state(positionMs = 62_000L))
    }

    @Test
    fun `the elapsed second is deliberately not part of the shape`() {
        // Twice-a-second publish() calls must not turn into twice-a-second
        // notification rebuilds; only the whole-second tick does that.
        assertEquals(state(positionMs = 0L), state(positionMs = 500L))
        assertEquals(state(positionMs = 0L), state(positionMs = 999L))
    }
}
