package app.bunmusic

import app.bunmusic.data.Listening
import app.bunmusic.data.Source
import app.bunmusic.data.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The play log is the only place in the app that knows *when* something was
 * played, so these tests are about time boundaries rather than about counting.
 */
class ListeningTest {

    private val day = Listening.DAY

    private fun track(id: String, artist: String = "A", album: String = "") = Track(
        id = id, title = "T-$id", artist = artist, album = album,
        durationMs = 180_000L, source = Source.LOCAL, videoId = "", assetAudio = "a.mp3",
    )

    private fun event(id: String, at: Long, ms: Long = 180_000L) = Listening.Event(id, at, ms)

    // ---------------------------------------------------------------- codec

    @Test
    fun `encode and decode survive a round trip`() {
        val e = event("yt-1", 1_700_000_000_000L, 210_000L)
        assertEquals(e, Listening.decode(Listening.encode(e)))
    }

    @Test
    fun `a line without three fields is not an event`() {
        assertNull(Listening.decode("yt-1"))
        assertNull(Listening.decode("yt-1\u0001not-a-number\u0001180000"))
        assertNull(Listening.decode(""))
    }

    @Test
    fun `an event with a blank id is rejected rather than kept as an empty row`() {
        assertNull(Listening.decode("\u00011700000000000\u0001180000"))
    }

    // --------------------------------------------------------------- append

    @Test
    fun `append puts the newest play first`() {
        val log = Listening.append(listOf(event("a", 1000)), event("b", 2000))
        assertEquals(listOf("b", "a"), log.map { it.trackId })
    }

    @Test
    fun `a resume within the same minute does not count as a second play`() {
        // Pausing and resuming fires the play callback again. Counting it would
        // inflate every number on the year in review.
        val first = event("a", 10_000)
        val log = Listening.append(listOf(first), event("a", 40_000))
        assertEquals(1, log.size)
        assertEquals(40_000L, log.first().atMs)
    }

    @Test
    fun `a replay an hour later is a real second play`() {
        val log = Listening.append(listOf(event("a", 10_000)), event("a", 10_000 + 3_600_000))
        assertEquals(2, log.size)
    }

    @Test
    fun `the log is a ring and drops the oldest entry`() {
        var log = emptyList<Listening.Event>()
        repeat(5) { i -> log = Listening.append(log, event("t$i", i * 120_000L), cap = 3) }
        assertEquals(3, log.size)
        assertEquals(listOf("t4", "t3", "t2"), log.map { it.trackId })
    }

    @Test
    fun `an empty log accepts the first event`() {
        assertEquals(1, Listening.append(emptyList(), event("a", 1)).size)
    }

    // ----------------------------------------------------------------- days

    @Test
    fun `daily buckets land on the right day`() {
        val now = 10 * day + 12 * 3_600_000L      // day 10, noon
        val log = listOf(
            event("a", 10 * day + 3_600_000L),    // today
            event("b", 9 * day + 3_600_000L),     // yesterday
            event("c", 9 * day + 20 * 3_600_000L),
        )
        val bars = Listening.daily(log, days = 3, nowMs = now)
        assertEquals(listOf(0, 2, 1), bars)
    }

    @Test
    fun `daily ignores plays older than the window`() {
        val now = 30 * day
        val bars = Listening.daily(listOf(event("a", 20 * day)), days = 7, nowMs = now)
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 0), bars)
    }

    @Test
    fun `a play just after local midnight belongs to the new day`() {
        // 00:30 local at UTC+2 is 22:30 UTC the day before. Day maths in UTC
        // would file it under yesterday for half the planet.
        val offset = 2 * 3_600_000L
        val now = 10 * day + 12 * 3_600_000L
        val play = 10 * day + 30 * 60_000L - offset   // 00:30 local on day 10
        val bars = Listening.daily(listOf(event("a", play)), days = 2, nowMs = now, offsetMs = offset)
        assertEquals(listOf(0, 1), bars)
    }

    @Test
    fun `daily with a zero window is empty rather than a crash`() {
        assertTrue(Listening.daily(listOf(event("a", 0)), days = 0, nowMs = 0).isEmpty())
    }

    // ---------------------------------------------------------------- hours

    @Test
    fun `hourly puts a play in the hour it started`() {
        val base = 10 * day
        val bars = Listening.hourly(listOf(event("a", base + 21 * 3_600_000L), event("b", base + 21 * 3_600_000L + 59_000)))
        assertEquals(2, bars[21])
        assertEquals(0, bars[22])
        assertEquals(24, bars.size)
    }

    @Test
    fun `the peak hour is null when nothing was played`() {
        assertNull(Listening.peakHour(emptyList()))
    }

    @Test
    fun `the peak hour is the busiest one`() {
        val base = 10 * day
        val log = listOf(
            event("a", base + 2 * 3_600_000L),
            event("b", base + 8 * 3_600_000L),
            event("c", base + 8 * 3_600_000L + 60_000),
        )
        assertEquals(8, Listening.peakHour(log))
    }

    // ----------------------------------------------------------------- tops

    @Test
    fun `top artists count plays and skip ids the registry forgot`() {
        val tracks = mapOf("1" to track("1", "Radiohead"), "2" to track("2", "Björk"))
        val resolve: (String) -> Track? = { tracks[it] }
        val log = listOf(event("1", 0), event("1", 1), event("2", 2), event("gone", 3))
        assertEquals(listOf("Radiohead" to 2, "Björk" to 1), Listening.topArtists(log, resolve))
    }

    @Test
    fun `top tracks returns the track objects, not just ids`() {
        val tracks = mapOf("1" to track("1"))
        val log = listOf(event("1", 0), event("1", 1))
        val top = Listening.topTracks(log, { tracks[it] })
        assertEquals("T-1", top.first().first.title)
        assertEquals(2, top.first().second)
    }

    @Test
    fun `an artist with a blank name is not grouped under an empty key`() {
        val tracks = mapOf("1" to track("1", artist = "  "))
        val top = Listening.topArtists(listOf(event("1", 0)), { tracks[it] })
        assertTrue(top.isEmpty())
    }

    @Test
    fun `top albums ignore tracks that have no album`() {
        val tracks = mapOf("1" to track("1", album = "Kid A"), "2" to track("2", album = ""))
        val top = Listening.topAlbums(listOf(event("1", 0), event("2", 1)), { tracks[it] })
        assertEquals(listOf("Kid A" to 1), top)
    }

    @Test
    fun `top lists respect the limit`() {
        val tracks = (1..9).associate { "$it" to track("$it", "Artist $it") }
        val log = (1..9).map { event("$it", it.toLong()) }
        assertEquals(3, Listening.topArtists(log, { tracks[it] }, limit = 3).size)
    }

    // --------------------------------------------------------------- ranges

    @Test
    fun `inRange is half open so a play on the boundary is not counted twice`() {
        val log = listOf(event("a", 100), event("b", 200))
        assertEquals(1, Listening.inRange(log, 100, 200).size)
        assertEquals(1, Listening.inRange(log, 200, 300).size)
    }

    @Test
    fun `day index respects the offset`() {
        assertEquals(1L, Listening.dayIndex(day, 0))
        assertEquals(1L, Listening.dayIndex(day - 3_600_000L, 3_600_000L))
    }

    @Test
    fun `monthly only counts the requested year`() {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.set(2025, java.util.Calendar.MARCH, 15, 12, 0, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val march2025 = cal.timeInMillis

        cal.set(2024, java.util.Calendar.MARCH, 15, 12, 0, 0)
        val march2024 = cal.timeInMillis

        val bars = Listening.monthly(listOf(event("a", march2025), event("b", march2024)), 2025)
        assertEquals(1, bars[2])
        assertEquals(0, bars.sum() - 1)
    }
}
