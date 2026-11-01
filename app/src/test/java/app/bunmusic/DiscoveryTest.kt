package app.bunmusic

import app.bunmusic.data.Discovery
import app.bunmusic.data.HistoryItem
import app.bunmusic.data.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * The discovery engine.
 *
 * Every function here is arithmetic over the play log, so every test is a
 * statement about what the app claims to know: a like counts double, a skip is
 * not a play, a mix never puts the same artist twice in a row.
 */
class DiscoveryTest {

    private val day = 86_400_000L
    private val now = 1_700_000_000_000L

    private fun t(id: String, artist: String = "A", duration: Long = 180_000L) =
        Track(id = id, title = id, artist = artist, durationMs = duration, videoId = "v$id")

    private fun h(track: Track, at: Long) = HistoryItem(track, at)

    private fun utc(year: Int, month: Int, dayOfMonth: Int, hour: Int = 12): Long {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        c.clear()
        c.set(year, month, dayOfMonth, hour, 0, 0)
        return c.timeInMillis
    }

    // ------------------------------------------------------------------ taste

    @Test
    fun `a like is worth two plays`() {
        val played = t("p", "Played")
        val liked = t("l", "Liked")
        val taste = Discovery.taste(listOf(h(played, now)), listOf(liked))
        assertEquals(listOf("Liked" to 2, "Played" to 1), taste.artists)
        assertEquals("Liked", taste.topArtist)
    }

    @Test
    fun `taste counts distinct artists, not tracks`() {
        val a1 = t("a1", "A")
        val a2 = t("a2", "A")
        val b = t("b", "B")
        val taste = Discovery.taste(
            listOf(h(a1, now), h(a2, now), h(b, now)),
            emptyList(),
        )
        assertEquals(2, taste.depth)
        assertEquals(3, taste.plays)
        assertEquals(listOf("A" to 2, "B" to 1), taste.artists)
    }

    // ------------------------------------------------------------------ mixes

    @Test
    fun `the first mix is called daily mix one`() {
        val all = (1..3).map { t("a$it", "Alpha") }
        val mixes = Discovery.mixes(all.map { h(it, now) }, emptyList(), all)
        assertEquals(1, mixes.size)
        assertEquals("Daily Mix 1 · Alpha", mixes.first().title)
    }

    @Test
    fun `a mix pads with liked songs from other artists`() {
        val alpha = t("a1", "Alpha")
        val beta = listOf(t("b1", "Beta"), t("b2", "Beta"))
        val mixes = Discovery.mixes(
            history = listOf(h(alpha, now)),
            liked = listOf(alpha) + beta,
            all = listOf(alpha) + beta,
        )
        val first = mixes.first { it.title.contains("Alpha") }
        assertTrue("padding should fill the mix", first.tracks.size > 1)
        assertEquals(alpha.id, first.tracks.first().id)
    }

    @Test
    fun `an artist with one song and nothing to pad with is not a mix`() {
        val only = t("solo", "Solo")
        val mixes = Discovery.mixes(listOf(h(only, now)), emptyList(), listOf(only))
        assertTrue(mixes.isEmpty())
    }

    // ---------------------------------------------------------- because liked

    @Test
    fun `because you liked picks the most liked artist and hides the liked songs`() {
        val likedA = listOf(t("a1", "Alpha"), t("a2", "Alpha"))
        val likedB = listOf(t("b1", "Beta"))
        val unheard = listOf(t("a3", "Alpha"), t("a4", "Alpha"), t("a5", "Alpha"))
        val pair = Discovery.becauseYouLiked(likedA + likedB, likedA + likedB + unheard, emptyList())
        assertEquals("Alpha", pair?.first)
        assertTrue(pair!!.second.none { it.id in setOf("a1", "a2") })
        assertEquals(listOf("a3", "a4", "a5"), pair.second.map { it.id })
    }

    @Test
    fun `because you liked refuses a shelf shorter than three`() {
        val liked = listOf(t("a1", "Alpha"))
        val one = t("a2", "Alpha")
        assertNull(Discovery.becauseYouLiked(liked, liked + one, emptyList()))
    }

    // ------------------------------------------------------------- time capsule

    @Test
    fun `time capsule finds the same day in an earlier year`() {
        val track = t("old")
        val lastYear = utc(2022, Calendar.JUNE, 14)
        val today = utc(2023, Calendar.JUNE, 14)
        val capsule = Discovery.timeCapsule(listOf(h(track, lastYear)), today, offsetMs = 0)
        assertEquals(listOf("old"), capsule.map { it.id })
    }

    @Test
    fun `time capsule falls back to the same month`() {
        val track = t("month")
        val earlier = utc(2022, Calendar.JUNE, 2)
        val today = utc(2023, Calendar.JUNE, 14)
        val capsule = Discovery.timeCapsule(listOf(h(track, earlier)), today, offsetMs = 0)
        assertEquals(listOf("month"), capsule.map { it.id })
    }

    @Test
    fun `time capsule ignores this year`() {
        val track = t("fresh")
        val today = utc(2023, Calendar.JUNE, 14)
        val thisYear = utc(2023, Calendar.JUNE, 13)
        assertTrue(Discovery.timeCapsule(listOf(h(track, thisYear)), today, 0).isEmpty())
    }

    // ----------------------------------------------------------- smart shuffle

    @Test
    fun `smart shuffle drops a song skipped three times`() {
        val a = t("a", "A")
        val b = t("b", "B")
        val out = Discovery.smartShuffle(listOf(a, b), skips = mapOf("a" to 3))
        assertEquals(listOf("b"), out.map { it.id })
    }

    @Test
    fun `smart shuffle moves recent songs behind the rest`() {
        // One artist, so the spread rule cannot reorder anything and the bucket
        // order is the only thing under test.
        val tracks = (1..4).map { t("s$it", "Same") }
        val out = Discovery.smartShuffle(tracks, recentIds = setOf("s1"))
        assertEquals("s1", out.last().id)
    }

    @Test
    fun `smart shuffle never puts the same artist back to back`() {
        val tracks = listOf(
            t("a1", "A"), t("a2", "A"), t("b1", "B"), t("b2", "B"), t("c1", "C"), t("c2", "C"),
        )
        val out = Discovery.smartShuffle(tracks)
        val artists = out.map { Discovery.artistOf(it) }
        artists.zipWithNext().forEach { (x, y) ->
            assertTrue("$x followed by $y", x != y)
        }
        assertEquals(6, out.size)
    }

    @Test
    fun `smart shuffle keeps every song when nothing is skipped`() {
        val tracks = (1..5).map { t("k$it", "Artist$it") }
        assertEquals(5, Discovery.smartShuffle(tracks).size)
    }

    // ------------------------------------------------------------------ blend

    @Test
    fun `blend is two liked songs then one from a followed artist`() {
        val liked = listOf(t("l1", "L"), t("l2", "L"))
        val follow = listOf(t("f1", "Followed"), t("f2", "Followed"))
        val out = Discovery.blend(liked, follow, setOf("Followed"))
        // Two of yours, one of theirs, repeating — duplicates collapse, so the
        // order is l1, l2, f1, f2 rather than the raw interleave.
        assertEquals(listOf("l1", "l2", "f1", "f2"), out.map { it.id })
    }

    @Test
    fun `blend is empty without follows`() {
        assertTrue(Discovery.blend(listOf(t("a")), listOf(t("b", "B")), emptySet()).isEmpty())
    }

    // ------------------------------------------------------------- fresh finds

    @Test
    fun `fresh finds are by liked artists and never heard`() {
        val liked = listOf(t("a1", "Alpha"))
        val unheard = t("a2", "Alpha")
        val heard = t("a3", "Alpha")
        val other = t("b1", "Beta")
        val out = Discovery.freshFinds(liked, listOf(liked[0], unheard, heard, other), listOf(h(heard, now)))
        assertEquals(listOf("a2"), out.map { it.id })
    }

    @Test
    fun `deep cuts are the songs played exactly once`() {
        val all = listOf(t("once"), t("twice"), t("never"))
        val out = Discovery.deepCuts(all, mapOf("once" to 1, "twice" to 2))
        assertEquals(listOf("once"), out.map { it.id })
    }

    // ---------------------------------------------------------------- numbers

    @Test
    fun `discovery share counts plays outside the all-time top five`() {
        val top = (1..5).map { t("top$it", "Top$it") }
        val fresh = t("new", "Newcomer")
        val history = top.map { h(it, now - day) } + listOf(h(fresh, now))
        val share = Discovery.discoveryShare(history, now, days = 30)
        assertEquals(1f / 6f, share, 0.001f)
    }

    @Test
    fun `hour mood has hard edges`() {
        assertEquals(Discovery.Mood.MORNING, Discovery.hourMood(5))
        assertEquals(Discovery.Mood.MORNING, Discovery.hourMood(11))
        assertEquals(Discovery.Mood.AFTERNOON, Discovery.hourMood(12))
        assertEquals(Discovery.Mood.EVENING, Discovery.hourMood(17))
        assertEquals(Discovery.Mood.NIGHT, Discovery.hourMood(22))
        assertEquals(Discovery.Mood.NIGHT, Discovery.hourMood(3))
    }
}
