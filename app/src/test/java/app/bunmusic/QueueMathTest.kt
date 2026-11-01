package app.bunmusic

import app.bunmusic.data.QueueMath
import app.bunmusic.data.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The queue split into past, present and future — and the index arithmetic. */
class QueueMathTest {

    private fun t(id: String, duration: Long = 60_000L) = Track(id = id, title = id, artist = "A", durationMs = duration)

    private val queue = listOf(t("a"), t("b"), t("c"), t("d"))

    @Test
    fun `upcoming is everything after the current index`() {
        assertEquals(listOf("c", "d"), QueueMath.upcoming(queue, 1).map { it.id })
    }

    @Test
    fun `upcoming is empty on the last track`() {
        assertTrue(QueueMath.upcoming(queue, 3).isEmpty())
        assertTrue(QueueMath.upcoming(queue, -1).isEmpty())
    }

    @Test
    fun `played is everything before the current index, most recent first`() {
        assertEquals(listOf("b", "a"), QueueMath.played(queue, 2).map { it.id })
        assertTrue(QueueMath.played(queue, 0).isEmpty())
    }

    @Test
    fun `insert next puts the song directly after the current one`() {
        val out = QueueMath.insertNext(queue, 1, t("x"))
        assertEquals(listOf("a", "b", "x", "c", "d"), out.map { it.id })
    }

    @Test
    fun `insert next at the end appends`() {
        val out = QueueMath.insertNext(queue, 3, t("x"))
        assertEquals("x", out.last().id)
        assertEquals(5, out.size)
    }

    @Test
    fun `remove ignores an index that is not there`() {
        assertEquals(queue, QueueMath.remove(queue, 9))
        assertEquals(listOf("a", "c", "d"), QueueMath.remove(queue, 1).map { it.id })
    }

    @Test
    fun `jump target clamps instead of wrapping`() {
        assertEquals(0, QueueMath.jumpTarget(queue, 0, -1))
        assertEquals(3, QueueMath.jumpTarget(queue, 3, 1))
        assertEquals(2, QueueMath.jumpTarget(queue, 1, 1))
        assertEquals(-1, QueueMath.jumpTarget(emptyList(), 0, 1))
    }

    @Test
    fun `shuffling upcoming leaves the past and the current song in place`() {
        val out = QueueMath.shuffleUpcoming(queue, 1, seed = 7)
        assertEquals(listOf("a", "b"), out.take(2).map { it.id })
        assertEquals(setOf("c", "d"), out.drop(2).map { it.id }.toSet())
    }

    @Test
    fun `total time adds every track`() {
        assertEquals(240_000L, QueueMath.totalMs(queue))
    }

    @Test
    fun `position label reads like a queue`() {
        assertEquals("2 of 4", QueueMath.positionLabel(1, 4))
        assertEquals("Not playing", QueueMath.positionLabel(-1, 4))
    }
}
