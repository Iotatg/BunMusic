package app.bunmusic

import app.bunmusic.data.Session
import app.bunmusic.data.Store
import app.bunmusic.data.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What comes back after the process dies.
 *
 * The queue is written to disk every few seconds while something is playing, so
 * the trimming arithmetic runs constantly and has to hold for every shape a
 * queue can be in: a hundred-track album, a radio station that grew to four
 * hundred, and — the case that used to throw — a cursor left over from a queue
 * the listener had already shortened.
 */
class SessionTest {

    private fun queue(n: Int) = (0 until n).map { Track(id = "t$it", title = "Song $it", artist = "A") }

    @Test
    fun `a short queue is kept whole`() {
        val cut = Session.slice(queue(3), index = 0)
        assertEquals(3, cut.queue.size)
        assertEquals(0, cut.index)
    }

    @Test
    fun `the cursor stays on the same song`() {
        // Whatever is trimmed, cut.queue[cut.index] must be the track that was
        // playing — otherwise reopening the app resumes the wrong song.
        val all = queue(30)
        for (index in all.indices) {
            val cut = Session.slice(all, index)
            assertEquals("index $index", all[index].id, cut.queue[cut.index].id)
        }
    }

    @Test
    fun `a little history is kept behind the cursor`() {
        val cut = Session.slice(queue(30), index = 20)
        assertEquals(5, cut.index)
        assertEquals("t15", cut.queue.first().id)
        assertEquals("t20", cut.queue[cut.index].id)
    }

    @Test
    fun `nothing is trimmed in front of the fifth track`() {
        val cut = Session.slice(queue(30), index = 4)
        assertEquals("the cursor is where it was", 4, cut.index)
        assertEquals("nothing was dropped", 30, cut.queue.size)
    }

    @Test
    fun `a long queue is capped`() {
        val cut = Session.slice(queue(400), index = 0)
        assertEquals(100, cut.queue.size)
    }

    @Test
    fun `the cap keeps the cursor inside the saved window`() {
        val cut = Session.slice(queue(400), index = 300)
        assertEquals(100, cut.queue.size)
        assertEquals(5, cut.index)
        assertEquals("t300", cut.queue[cut.index].id)
    }

    @Test
    fun `a stale cursor past the end does not throw`() {
        // Removing tracks from the queue can leave the index pointing past the
        // end for a frame; the save that happens in that frame used to crash.
        val cut = Session.slice(queue(3), index = 99)
        assertTrue(cut.queue.isNotEmpty())
        assertTrue("index ${cut.index} out of bounds", cut.index in cut.queue.indices)
    }

    @Test
    fun `a negative cursor clamps to the start`() {
        val cut = Session.slice(queue(5), index = -3)
        assertEquals(0, cut.index)
        assertEquals(5, cut.queue.size)
    }

    @Test
    fun `an empty queue saves nothing`() {
        val cut = Session.slice(emptyList(), index = 4)
        assertTrue(cut.queue.isEmpty())
        assertEquals(0, cut.index)
    }

    @Test
    fun `a title with a line break cannot split the saved queue`() {
        // The queue is one string of encoded rows. With a newline separator, one
        // such title turned into two broken rows and the restore silently lost
        // everything after it.
        val rows = queue(3).mapIndexed { i, t ->
            if (i == 1) t.copy(title = "First\nsecond") else t
        }
        val encoded = rows.joinToString("\u0002") { Store.encodeTrack(it) }
        val back = encoded.split('\u0002').mapNotNull { Store.decodeTrack(it) }
        assertEquals(3, back.size)
        assertEquals("First\nsecond", back[1].title)
    }

    @Test
    fun `a saved session knows which track it is on`() {
        val saved = Session.Saved(
            queue = queue(3),
            index = 1,
            positionMs = 12_000L,
            speed = 1f,
            repeat = 0,
            shuffle = false,
            savedAt = 0L,
        )
        assertEquals("t1", saved.track!!.id)
        assertNull(saved.copy(index = 9).track)
    }
}
