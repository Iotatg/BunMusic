package app.bunmusic

import app.bunmusic.data.Listening
import app.bunmusic.data.Source
import app.bunmusic.data.Track
import app.bunmusic.data.Wrapped
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The year in review is arithmetic pretending to be a story, so the tests are
 * about the arithmetic. Every number on the screen and in the share image comes
 * out of [Wrapped.summarise] and [Wrapped.cards].
 */
class WrappedTest {

    private val day = Listening.DAY

    private val tracks = mapOf(
        "1" to Track("1", "Everything In Its Right Place", "Radiohead", "Kid A", durationMs = 240_000, source = Source.LOCAL, assetAudio = "1.mp3"),
        "2" to Track("2", "Jóga", "Björk", "Homogenic", durationMs = 300_000, source = Source.LOCAL, assetAudio = "2.mp3"),
        "3" to Track("3", "Weird Fishes", "Radiohead", "In Rainbows", durationMs = 330_000, source = Source.LOCAL, assetAudio = "3.mp3"),
    )

    private val resolve: (String) -> Track? = { tracks[it] }

    /** Built in UTC on purpose: [Wrapped.summarise] is called with offset 0 here. */
    private fun at(year: Int, month: Int, dayOfMonth: Int, hour: Int = 12): Long {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.set(year, month, dayOfMonth, hour, 0, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun ev(id: String, ms: Long) = Listening.Event(id, ms, tracks[id]?.durationMs ?: 0L)

    // ------------------------------------------------------------ thresholds

    @Test
    fun `a handful of plays is not a year in review`() {
        val events = List(3) { ev("1", at(2025, 5, 1) + it * 1_000L) }
        val s = Wrapped.summarise(events, resolve, 2025)
        assertFalse(s.hasStory)
        assertEquals(3, s.plays)
    }

    @Test
    fun `ten plays is enough to tell a story`() {
        val events = List(Wrapped.MIN_PLAYS) { ev("1", at(2025, 5, 1) + it * 1_000L) }
        assertTrue(Wrapped.summarise(events, resolve, 2025).hasStory)
    }

    @Test
    fun `plays outside the year are ignored`() {
        val events = listOf(
            ev("1", at(2025, 2, 1)),
            ev("1", at(2024, 2, 1)),
            ev("2", at(2026, 1, 1)),
        )
        assertEquals(1, Wrapped.summarise(events, resolve, 2025).plays)
    }

    // ------------------------------------------------------------------ sums

    @Test
    fun `minutes are the claimed durations, rounded down`() {
        // 240 s + 300 s = 540 s = 9 minutes exactly.
        val events = listOf(ev("1", at(2025, 0, 1)), ev("2", at(2025, 0, 2)))
        assertEquals(9L, Wrapped.summarise(events, resolve, 2025).minutes)
    }

    @Test
    fun `a track played twice counts once in the track total and twice in plays`() {
        val events = listOf(ev("1", at(2025, 0, 1)), ev("1", at(2025, 0, 2)), ev("2", at(2025, 0, 3)))
        val s = Wrapped.summarise(events, resolve, 2025)
        assertEquals(3, s.plays)
        assertEquals(2, s.tracks)
        assertEquals(2, s.artists)
    }

    @Test
    fun `top artist is the one with the most plays`() {
        val events = listOf(
            ev("1", at(2025, 0, 1)), ev("3", at(2025, 0, 2)), ev("3", at(2025, 0, 3)),
        )
        assertEquals("Radiohead" to 3, Wrapped.summarise(events, resolve, 2025).topArtist)
    }

    @Test
    fun `top track carries the track itself, not just a title`() {
        val events = listOf(ev("2", at(2025, 0, 1)), ev("2", at(2025, 0, 2)))
        val top = Wrapped.summarise(events, resolve, 2025).topTrack
        assertEquals("Jóga", top?.first?.title)
        assertEquals(2, top?.second)
    }

    @Test
    fun `top album is the album, not the track`() {
        val events = listOf(ev("1", at(2025, 0, 1)), ev("1", at(2025, 0, 2)))
        assertEquals("Kid A" to 2, Wrapped.summarise(events, resolve, 2025).topAlbum)
    }

    @Test
    fun `the artist share is a fraction of all plays`() {
        val events = listOf(ev("1", at(2025, 0, 1)), ev("2", at(2025, 0, 2)), ev("2", at(2025, 0, 3)), ev("2", at(2025, 0, 4)))
        val s = Wrapped.summarise(events, resolve, 2025)
        assertEquals("Björk", s.topArtist?.first)
        assertEquals(0.75f, s.topArtistShare, 0.001f)
    }

    // ------------------------------------------------------------------ days

    @Test
    fun `the busiest day is the one with the most plays`() {
        val events = listOf(
            ev("1", at(2025, 3, 1)),
            ev("1", at(2025, 3, 2)),
            ev("2", at(2025, 3, 2)),
            ev("3", at(2025, 3, 2)),
        )
        val s = Wrapped.summarise(events, resolve, 2025)
        assertEquals(3, s.busiestDay?.second)
    }

    @Test
    fun `streak counts consecutive days, not consecutive plays`() {
        val events = listOf(
            ev("1", at(2025, 0, 1)),
            ev("1", at(2025, 0, 2)),
            ev("1", at(2025, 0, 3)),
            ev("1", at(2025, 0, 9)),
        )
        assertEquals(3, Wrapped.summarise(events, resolve, 2025).streak)
    }

    @Test
    fun `a gap of one day breaks the streak`() {
        val events = listOf(
            ev("1", at(2025, 0, 1)),
            ev("1", at(2025, 0, 3)),
        )
        assertEquals(1, Wrapped.summarise(events, resolve, 2025).streak)
    }

    @Test
    fun `longest streak handles unsorted and duplicate days`() {
        assertEquals(3, Wrapped.longestStreak(listOf(5L, 3L, 4L, 3L, 9L)))
        assertEquals(0, Wrapped.longestStreak(emptyList()))
        assertEquals(1, Wrapped.longestStreak(listOf(7L)))
    }

    // ----------------------------------------------------------------- night

    @Test
    fun `plays after ten and before four count as night listening`() {
        val events = listOf(
            ev("1", at(2025, 0, 1, hour = 23)),
            ev("1", at(2025, 0, 2, hour = 2)),
            ev("1", at(2025, 0, 3, hour = 12)),
            ev("1", at(2025, 0, 4, hour = 4)),
        )
        val s = Wrapped.summarise(events, resolve, 2025)
        assertEquals(2, s.nightPlays)
        assertEquals(0.5f, s.nightShare, 0.001f)
    }

    // ----------------------------------------------------------- discovery

    @Test
    fun `tracks played exactly once are the discoveries`() {
        val events = listOf(
            ev("1", at(2025, 0, 1)), ev("1", at(2025, 0, 2)),
            ev("2", at(2025, 0, 3)), ev("3", at(2025, 0, 4)),
        )
        assertEquals(2, Wrapped.summarise(events, resolve, 2025).discoveries)
    }

    @Test
    fun `an id the registry forgot is skipped instead of counted as a blank track`() {
        val events = listOf(Listening.Event("ghost", at(2025, 0, 1), 1000))
        val s = Wrapped.summarise(events, resolve, 2025)
        assertEquals(1, s.plays)
        assertNull(s.topTrack)
        assertEquals(0, s.artists)
    }

    // ----------------------------------------------------------------- cards

    @Test
    fun `the reel opens with the year and closes with the month`() {
        val events = List(12) { ev("1", at(2025, 4, 1) + it * 1_000L) }
        val cards = Wrapped.cards(Wrapped.summarise(events, resolve, 2025))
        assertEquals(Wrapped.Kind.INTRO, cards.first().kind)
        assertEquals("2025 in review", cards.first().kicker)
        assertEquals(Wrapped.Kind.OUTRO, cards.last().kind)
        assertEquals("May", cards.last().headline)
    }

    @Test
    fun `a single-track year says so instead of pretending to be broad`() {
        val events = List(12) { ev("1", at(2025, 4, 1) + it * 1_000L) }
        val cards = Wrapped.cards(Wrapped.summarise(events, resolve, 2025))
        assertEquals("One song, over and over.", cards.first().body)
    }

    @Test
    fun `no streak card below three days`() {
        val events = listOf(ev("1", at(2025, 0, 1)), ev("2", at(2025, 0, 5)))
        val cards = Wrapped.cards(Wrapped.summarise(events, resolve, 2025))
        assertTrue(cards.none { it.kind == Wrapped.Kind.STREAK })
    }

    @Test
    fun `the night card only appears when something was played at night`() {
        val events = List(12) { ev("1", at(2025, 4, 1, hour = 12) + it * 1_000L) }
        val cards = Wrapped.cards(Wrapped.summarise(events, resolve, 2025))
        assertTrue(cards.none { it.kind == Wrapped.Kind.NIGHT })
    }

    @Test
    fun `month and day labels are injected, not read from the device locale`() {
        val events = List(12) { ev("1", at(2025, 4, 1) + it * 1_000L) }
        val s = Wrapped.summarise(events, resolve, 2025)
        val cards = Wrapped.cards(s, monthName = { "M$it" }, dayLabel = { "D$it" })
        assertEquals("M4", cards.last().headline)
    }

    // -------------------------------------------------------------- minutes

    @Test
    fun `minutes read as minutes, then hours, then many hours`() {
        assertEquals("9 min", Wrapped.fmtMinutes(9))
        assertEquals("1 h 5 min", Wrapped.fmtMinutes(65))
        assertEquals("120 hours", Wrapped.fmtMinutes(60 * 120L))
    }

    @Test
    fun `an empty log produces a summary with no story and no crash`() {
        val s = Wrapped.summarise(emptyList(), resolve, 2025)
        assertFalse(s.hasStory)
        assertEquals(0, s.plays)
        assertNull(s.topArtist)
        assertNull(s.busiestDay)
        assertEquals(0f, s.nightShare, 0.0001f)
    }
}
