package app.bunmusic

import app.bunmusic.data.Listening
import app.bunmusic.data.Memory
import app.bunmusic.data.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Memory is arithmetic over a log, so it can be checked without a device.
 *
 * These tests exist because every failure mode here is silent: a memory shown on
 * the wrong day, a DNA axis stuck at 100, a streak that counts a gap. None of
 * that throws — it just quietly lies about the listener.
 */
class MemoryTest {

    private val day = Memory.DAY

    private fun t(id: String, artist: String = "A") =
        Track(id = id, title = id, artist = artist)

    private fun at(ms: Long) = Listening.Event("x", ms, 1_000)

    private fun event(id: String, atMs: Long) = Listening.Event(id, atMs, 1_000)

    /** Midday on a fixed date, so the tests never depend on when they run. */
    private val today: Long = LocalDate.of(2026, 11, 4).toEpochDay() * day + 12 * 3_600_000L

    // ------------------------------------------------------------- on this day

    @Test
    fun `the same date last year is a memory`() {
        val lastYear = today - 365 * day
        val events = listOf(event("a", lastYear), event("b", lastYear + 3_600_000L))
        val memories = Memory.onThisDay(events, { id -> t(id) }, today)
        assertEquals(1, memories.size)
        assertEquals(1, memories[0].yearsAgo)
        assertEquals(listOf("b", "a"), memories[0].tracks.map { it.id })
        assertEquals(2, memories[0].plays)
    }

    @Test
    fun `a different date is not a memory`() {
        val events = listOf(event("a", today - 365 * day + 40 * day))
        assertTrue(Memory.onThisDay(events, { id -> t(id) }, today).isEmpty())
    }

    @Test
    fun `one song played eleven times is a memory of one song`() {
        val lastYear = today - 365 * day
        val events = List(11) { event("a", lastYear + it * 60_000L) }
        val memories = Memory.onThisDay(events, { id -> t(id) }, today)
        assertEquals(1, memories[0].tracks.size)
        // ...but the count of plays is still eleven, because that is what happened.
        assertEquals(11, memories[0].plays)
    }

    @Test
    fun `memories come back newest first`() {
        // Calendar years, not 365-day blocks: three of those land a day off
        // because 2024 had an extra one.
        fun yearsAgo(n: Long) =
            LocalDate.of(2026, 11, 4).minusYears(n).toEpochDay() * day + 12 * 3_600_000L
        val events = listOf(
            event("old", yearsAgo(3)),
            event("new", yearsAgo(1)),
        )
        val memories = Memory.onThisDay(events, { id -> t(id) }, today)
        assertEquals(listOf(1, 3), memories.map { it.yearsAgo })
    }

    @Test
    fun `a leap day does not invent a memory on the 28th`() {
        val leapDay = LocalDate.of(2024, 2, 29).toEpochDay() * day + 12 * 3_600_000L
        // 2025 has no 29 February. The play from 2024 must not surface as
        // "a year ago" on 28 February 2025.
        val now = LocalDate.of(2025, 2, 28).toEpochDay() * day + 12 * 3_600_000L
        val memories = Memory.onThisDay(listOf(event("a", leapDay)), { id -> t(id) }, now)
        assertTrue(memories.isEmpty())
    }

    @Test
    fun `an empty log has no memories`() {
        assertTrue(Memory.onThisDay(emptyList(), { null }, today).isEmpty())
    }

    // -------------------------------------------------------------------- DNA

    private fun events(n: Int, id: String, start: Long, step: Long = 60_000L) =
        List(n) { event(id, start + it * step) }

    @Test
    fun `a handful of plays is not a profile`() {
        val few = events(5, "a", today - day)
        assertNull(Memory.dna(few, { t(it) }, today))
    }

    @Test
    fun `curiosity counts the tracks heard once`() {
        // 20 plays: ten one-off tracks, one track ten times.
        val oneOffs = (1..10).flatMap { events(1, "one$it", today - it * day) }
        val repeat = events(10, "repeat", today - day)
        val dna = Memory.dna(oneOffs + repeat, { t(it) }, today)
        assertNotNull(dna)
        // Half the plays are things tried once.
        assertEquals(50, dna!!.curiosity)
    }

    @Test
    fun `loyalty counts the tracks heard four times or more`() {
        val repeat = events(20, "repeat", today - day)
        val dna = Memory.dna(repeat, { t(it) }, today)!!
        assertEquals(100, dna.loyalty)
        assertEquals(0, dna.curiosity)
    }

    @Test
    fun `night counts the small hours`() {
        // Anchor at 23:00 local, then twenty plays an hour apart: 23, 00, 01, 02
        // are night; 03 onwards is morning.
        val start = LocalDate.of(2026, 11, 2).toEpochDay() * day + 23 * 3_600_000L
        val all = List(20) { event("a", start + it * 3_600_000L) }
        val dna = Memory.dna(all, { t(it) }, today)!!
        // 23, 00, 01, 02, 03 = 5 of 20. The window is 22:00–04:00, so 03:00
        // still counts and 04:00 does not.
        assertEquals(25, dna.night)
    }

    @Test
    fun `consistency is days with a play over days in the window`() {
        // Twenty plays on two days of a ninety-day window.
        val all = events(10, "a", today - day) + events(10, "b", today - 2 * day)
        val dna = Memory.dna(all, { t(it) }, today, days = 10)!!
        assertEquals(20, dna.consistency)
    }

    @Test
    fun `range is capped and survives a missing artist`() {
        val all = (1..25).flatMap { events(1, "t$it", today - it * 3_600_000L) }
        val dna = Memory.dna(all, { Track(id = it, title = it, artist = "") }, today)!!
        assertEquals(0, dna.range)
        val wide = Memory.dna(all, { Track(id = it, title = it, artist = it) }, today)!!
        assertTrue("range ${wide.range}", wide.range in 0..100)
    }

    @Test
    fun `every axis stays inside the chart`() {
        val messy = (1..40).flatMap { i ->
            events(1 + i % 5, "t${i % 9}", today - i * 3_600_000L, 7_000L)
        }
        val dna = Memory.dna(messy, { t(it) }, today)!!
        dna.axes.forEach { assertTrue("${it.label} ${it.value}", it.value in 0..100) }
        assertEquals(5, dna.axes.size)
        assertTrue(dna.strongest.value >= dna.weakest.value)
    }

    @Test
    fun `plays outside the window do not count`() {
        val old = events(30, "a", today - 200 * day)
        assertNull(Memory.dna(old, { t(it) }, today))
    }

    // ----------------------------------------------------------------- streak

    @Test
    fun `a streak counts back from today`() {
        assertEquals(3, Memory.currentStreak(listOf(100L, 99L, 98L), 100L))
    }

    @Test
    fun `a streak survives a quiet morning`() {
        // Nothing played yet today, but yesterday and the day before were busy.
        assertEquals(2, Memory.currentStreak(listOf(99L, 98L), 100L))
    }

    @Test
    fun `a gap ends the streak`() {
        assertEquals(2, Memory.currentStreak(listOf(100L, 99L, 97L, 96L), 100L))
        assertEquals(0, Memory.currentStreak(listOf(97L, 96L), 100L))
        assertEquals(0, Memory.currentStreak(emptyList(), 100L))
    }

    // ------------------------------------------------------------------ radar

    @Test
    fun `a zero sits at the centre and a hundred on the rim`() {
        val points = Memory.radar(listOf(0, 100, 100, 100, 100), 100f)
        assertEquals(5, points.size)
        assertEquals(0f, points[0].first, 0.001f)
        assertEquals(0f, points[0].second, 0.001f)
    }

    @Test
    fun `the first axis points up`() {
        val points = Memory.radar(listOf(100, 0, 0, 0, 0), 50f)
        // Screen y grows downward, so "up" is negative.
        assertEquals(0f, points[0].first, 0.001f)
        assertEquals(-50f, points[0].second, 0.001f)
    }

    @Test
    fun `the second axis points right, because the chart runs clockwise`() {
        val points = Memory.radar(listOf(0, 100, 0, 0, 0), 50f)
        assertTrue("x ${points[1].first}", points[1].first > 40f)
        assertTrue("y ${points[1].second}", points[1].second < 5f)
    }

    @Test
    fun `a nonsense value is clamped, not drawn outside the chart`() {
        val points = Memory.radar(listOf(400, -20), 50f)
        assertEquals(50f, Math.hypot(points[0].first.toDouble(), points[0].second.toDouble()).toFloat(), 0.01f)
        assertEquals(0f, points[1].first, 0.01f)
    }

    @Test
    fun `no axes means no polygon`() {
        assertTrue(Memory.radar(emptyList(), 50f).isEmpty())
        assertTrue(Memory.radar(listOf(10), 0f).isEmpty())
    }

    @Test
    fun `labels read like a person wrote them`() {
        assertEquals("a year ago", Memory.ago(1))
        assertEquals("three years ago", Memory.ago(3))
        assertEquals("9 years ago", Memory.ago(9))
    }
}
