package app.bunmusic

import app.bunmusic.data.HistoryItem
import app.bunmusic.data.Library
import app.bunmusic.data.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rest of the auto-playlist arithmetic.
 *
 * These lists are derived at read time from counters the app already keeps, so
 * every edge here is a real one: the hundredth most-played track, a song played
 * exactly at the edge of the window, and a like that has never been played.
 */
class LibraryMathTest {

    private val day = 86_400_000L
    private val now = 1_700_000_000_000L

    private fun t(id: String) = Track(id = id, title = id, artist = "A")

    // ------------------------------------------------------------------- rank

    @Test
    fun `rank honours its limit`() {
        val tracks = (1..20).map { t("t$it") }
        val counts = tracks.associate { it.id to it.id.removePrefix("t").toInt() }
        val top = Library.rank(counts, tracks, limit = 5)
        assertEquals(5, top.size)
        assertEquals(listOf("t20", "t19", "t18", "t17", "t16"), top.map { it.id })
    }

    @Test
    fun `rank keeps registry order when the counts tie`() {
        // A stable sort means the list does not reshuffle itself between frames.
        val tracks = listOf(t("a"), t("b"), t("c"))
        val counts = mapOf("a" to 3, "b" to 3, "c" to 3)
        assertEquals(listOf("a", "b", "c"), Library.rank(counts, tracks).map { it.id })
    }

    @Test
    fun `rank ignores counts for tracks that are not in the registry`() {
        val tracks = listOf(t("a"))
        val ranked = Library.rank(mapOf("a" to 1, "ghost" to 99), tracks)
        assertEquals(listOf("a"), ranked.map { it.id })
    }

    @Test
    fun `rank of nothing is nothing`() {
        assertTrue(Library.rank(emptyMap(), emptyList()).isEmpty())
        assertTrue(Library.rank(mapOf("a" to 1), emptyList()).isEmpty())
    }

    // --------------------------------------------------------------- on repeat

    @Test
    fun `on repeat puts the most played first`() {
        val history = listOf(
            HistoryItem(t("twice"), now - day),
            HistoryItem(t("twice"), now - day * 2),
            HistoryItem(t("four"), now - day),
            HistoryItem(t("four"), now - day * 2),
            HistoryItem(t("four"), now - day * 3),
            HistoryItem(t("four"), now - day * 4),
        )
        assertEquals(listOf("four", "twice"), Library.onRepeat(history, now = now).map { it.id })
    }

    @Test
    fun `a play exactly at the window edge still counts`() {
        val history = listOf(
            HistoryItem(t("edge"), now - day * 7),
            HistoryItem(t("edge"), now - day * 7),
        )
        assertEquals(listOf("edge"), Library.onRepeat(history, days = 7, min = 2, now = now).map { it.id })
    }

    @Test
    fun `a play just outside the window does not`() {
        val history = listOf(
            HistoryItem(t("edge"), now - day * 7 - 1),
            HistoryItem(t("edge"), now - day * 7 - 1),
        )
        assertTrue(Library.onRepeat(history, days = 7, min = 2, now = now).isEmpty())
    }

    @Test
    fun `min of one lists everything in the window`() {
        val history = listOf(HistoryItem(t("a"), now), HistoryItem(t("b"), now - day))
        assertEquals(2, Library.onRepeat(history, min = 1, now = now).size)
    }

    @Test
    fun `on repeat of an empty history is empty`() {
        assertTrue(Library.onRepeat(emptyList(), now = now).isEmpty())
    }

    // --------------------------------------------------------------- forgotten

    @Test
    fun `the cutoff is strict`() {
        // Exactly thirty days is "a month ago", not "more than a month ago".
        val liked = listOf(t("edge"))
        val history = listOf(HistoryItem(t("edge"), now - day * 30))
        assertTrue(Library.forgotten(liked, history, days = 30, now = now).isEmpty())

        val older = listOf(HistoryItem(t("edge"), now - day * 30 - 1))
        assertEquals(1, Library.forgotten(liked, older, days = 30, now = now).size)
    }

    @Test
    fun `forgotten keeps the order it was given`() {
        val liked = listOf(t("a"), t("b"), t("c"))
        val history = listOf(HistoryItem(t("b"), now - day))
        assertEquals(listOf("a", "c"), Library.forgotten(liked, history, now = now).map { it.id })
    }

    @Test
    fun `forgotten of nothing is nothing`() {
        assertTrue(Library.forgotten(emptyList(), emptyList(), now = now).isEmpty())
    }
}
