package app.bunmusic

import app.bunmusic.data.Downloads
import app.bunmusic.data.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parts of the download queue that are arithmetic rather than I/O.
 *
 * The engine itself needs a Context and a socket; what is pinned here is the
 * thing the screen reads — a percentage that never exceeds 100, and a spinner
 * that only appears when the size is genuinely unknown.
 */
class DownloadTest {

    private fun track(id: String) = Track(id = id, title = "Song $id", artist = "Someone")

    @Test
    fun `fraction is clamped and survives an unknown size`() {
        val unknown = Downloads.Job(track("a"), Downloads.State.RUNNING, doneBytes = 512, totalBytes = 0)
        assertEquals(0f, unknown.fraction, 0.0001f)
        assertTrue("no length means a spinner, not 0 %", unknown.indeterminate)

        val half = Downloads.Job(track("b"), Downloads.State.RUNNING, 500, 1_000)
        assertEquals(0.5f, half.fraction, 0.0001f)
        assertFalse(half.indeterminate)

        // A server that lies about the length must not paint past the end.
        val over = Downloads.Job(track("c"), Downloads.State.RUNNING, 2_000, 1_000)
        assertEquals(1f, over.fraction, 0.0001f)
    }

    @Test
    fun `only queued and running jobs count as active`() {
        assertTrue(Downloads.Job(track("a"), Downloads.State.QUEUED).active)
        assertTrue(Downloads.Job(track("a"), Downloads.State.RUNNING).active)
        assertFalse("held back is not running", Downloads.Job(track("a"), Downloads.State.WAITING).active)
        assertFalse("failed is not active", Downloads.Job(track("a"), Downloads.State.FAILED).active)
    }

    @Test
    fun `a finished job is not indeterminate even with no length`() {
        val queued = Downloads.Job(track("a"), Downloads.State.QUEUED, totalBytes = 0)
        assertFalse("a queued job has no bytes yet, not a spinner", queued.indeterminate)
    }
}
