package app.bunmusic

import app.bunmusic.data.SortKey
import app.bunmusic.data.SortSpec
import app.bunmusic.data.Sorting
import app.bunmusic.data.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sorting, filtering and the duplicate finder.
 *
 * These used to be three `when` branches inside the library screen, which meant
 * a sort could only be tested by looking at it. They are values now, and a value
 * can be asserted on.
 */
class SortingTest {

    private fun t(
        id: String,
        title: String = id,
        artist: String = "A",
        duration: Long = 180_000L,
    ) = Track(id = id, title = title, artist = artist, durationMs = duration)

    private val a = t("a", "Alpha", "Zed", 300_000L)
    private val b = t("b", "beta", "Alpha", 120_000L)
    private val c = t("c", "Gamma", "Mid", 240_000L)
    private val list = listOf(a, b, c)

    @Test
    fun `title sort is case insensitive`() {
        val out = Sorting.sort(list, SortSpec(SortKey.TITLE))
        assertEquals(listOf("a", "b", "c"), out.map { it.id })
    }

    @Test
    fun `descending reverses the whole order`() {
        val out = Sorting.sort(list, SortSpec(SortKey.TITLE, descending = true))
        assertEquals(listOf("c", "b", "a"), out.map { it.id })
    }

    @Test
    fun `artist sort falls back to title inside one artist`() {
        val x = t("x", "Two", "Same")
        val y = t("y", "One", "Same")
        val out = Sorting.sort(listOf(x, y), SortSpec(SortKey.ARTIST))
        assertEquals(listOf("y", "x"), out.map { it.id })
    }

    @Test
    fun `duration sorts by the number, not the label`() {
        val out = Sorting.sort(list, SortSpec(SortKey.DURATION))
        assertEquals(listOf("b", "c", "a"), out.map { it.id })
    }

    @Test
    fun `plays sort ascending is least played first`() {
        val out = Sorting.sort(list, SortSpec(SortKey.PLAYS), plays = mapOf("c" to 9, "a" to 2))
        assertEquals(listOf("b", "a", "c"), out.map { it.id })
    }

    @Test
    fun `plays sort descending is most played first`() {
        val out = Sorting.sort(list, SortSpec(SortKey.PLAYS, descending = true), plays = mapOf("c" to 9, "a" to 2))
        assertEquals(listOf("c", "a", "b"), out.map { it.id })
    }

    @Test
    fun `recent sort uses the added order and sends strangers to the end`() {
        val out = Sorting.sort(list, SortSpec(SortKey.RECENT), addedOrder = listOf("c", "a"))
        assertEquals(listOf("c", "a", "b"), out.map { it.id })
    }

    @Test
    fun `filter matches title, artist and album`() {
        assertEquals(listOf("a"), Sorting.filter(list, query = "zed").map { it.id })
        assertEquals(listOf("b"), Sorting.filter(list, query = "beta").map { it.id })
        assertTrue(Sorting.filter(list, query = "nothing").isEmpty())
    }

    @Test
    fun `filter can keep only downloads`() {
        val out = Sorting.filter(list, downloadedOnly = true, downloadedIds = setOf("a"))
        assertEquals(listOf("a"), out.map { it.id })
    }

    @Test
    fun `filter can keep only likes`() {
        val out = Sorting.filter(list, likedOnly = true, likedIds = setOf("b", "c"))
        assertEquals(listOf("b", "c"), out.map { it.id })
    }

    @Test
    fun `duplicates ignore a remaster suffix`() {
        val one = t("1", "Song (Remastered 2011)", "Band")
        val two = t("2", "Song", "Band")
        val three = t("3", "Other", "Band")
        val groups = Sorting.duplicates(listOf(one, two, three))
        assertEquals(1, groups.size)
        assertEquals(listOf("1", "2"), groups.first().map { it.id })
    }

    @Test
    fun `duplicates need both title and artist`() {
        val one = t("1", "Song", "A")
        val two = t("2", "Song", "B")
        assertTrue(Sorting.duplicates(listOf(one, two)).isEmpty())
    }

    @Test
    fun `summary shows hours and minutes`() {
        assertEquals("3 songs · 11m", Sorting.summary(list))
        val long = listOf(t("x", duration = 3_600_000L), t("y", duration = 1_800_000L))
        assertEquals("2 songs · 1h 30m", Sorting.summary(long))
        assertEquals("Empty", Sorting.summary(emptyList()))
    }
}
