package app.bunmusic

import app.bunmusic.core.LoadGuard
import app.bunmusic.data.Downloads
import app.bunmusic.ui.LibraryTabs
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressions for the third batch of the 2.11.x bug sweep (B7/B8/B9, C2, C5,
 * C9, C12, C15). The framework halves — coroutine registration windows, file
 * renames, Compose observable state, main-thread offloading — are annotated in
 * the sources; these assert every pure rule those fixes are built on, so the
 * logic holds on the JVM without a device or an emulator.
 */
class BugFixBatch7Test {

    // -------------------------------------------------------- C2: load guard

    @Test
    fun `a load is stale only when a newer one has started`() {
        assertFalse(LoadGuard.isStale(7L, 7L))
        assertTrue(LoadGuard.isStale(6L, 7L))
        assertTrue(LoadGuard.isStale(0L, 1L))
    }

    @Test
    fun `cancellation and errors are fatal but ordinary failures are not`() {
        assertTrue(LoadGuard.isFatal(CancellationException("cancelled")))
        assertTrue(LoadGuard.isFatal(OutOfMemoryError()))
        assertFalse(LoadGuard.isFatal(IllegalStateException("network")))
        assertFalse(LoadGuard.isFatal(RuntimeException("403")))
    }

    // ------------------------------------------- C5 + C2: pending seek value

    @Test
    fun `a resumption position is stashed, but unset and negative values are not`() {
        val unset = Long.MIN_VALUE // C.TIME_UNSET
        assertEquals(42_000L, LoadGuard.pendingSeek(42_000L, unset))
        assertEquals(0L, LoadGuard.pendingSeek(0L, unset))
        assertEquals(0L, LoadGuard.pendingSeek(-1L, unset))
        assertEquals(0L, LoadGuard.pendingSeek(unset, unset))
    }

    // ------------------------------------------------------- C9: library tabs

    @Test
    fun `the saved tab yields its downloads so bulk actions have rows`() {
        val liked = listOf("liked")
        val recents = listOf("recent")
        val downloads = listOf("d1", "d2", "d3")
        assertEquals(liked, LibraryTabs.shownFor(1, liked, recents, downloads))
        assertEquals(recents, LibraryTabs.shownFor(2, liked, recents, downloads))
        // The bug: tab 3 used to fall through to emptyList(), so select-all,
        // bulk like / queue / download all silently operated on nothing.
        assertEquals(downloads, LibraryTabs.shownFor(3, liked, recents, downloads))
        assertTrue(LibraryTabs.shownFor(0, liked, recents, downloads).isEmpty())
        assertTrue(LibraryTabs.shownFor(99, liked, recents, downloads).isEmpty())
    }

    // ------------------------------------------- B7: download registry merge

    private fun entry(id: String, at: Long) = Downloads.Entry(
        trackId = id, title = "T-$id", artist = "A", artUrl = null,
        path = "/files/$id", bytes = 100L, atMs = at,
    )

    @Test
    fun `folding a finished download keeps every other row`() {
        val current = listOf(entry("a", 100L), entry("b", 200L))
        val out = Downloads.mergeEntry(current, entry("c", 300L))
        assertEquals(3, out.size)
        assertEquals(setOf("a", "b", "c"), out.map { it.trackId }.toSet())
    }

    @Test
    fun `a re-downloaded track replaces its old row and stays newest first`() {
        val current = listOf(entry("a", 500L), entry("b", 200L))
        val out = Downloads.mergeEntry(current, entry("a", 900L))
        assertEquals(2, out.size)
        assertEquals(listOf("a", "b"), out.map { it.trackId })
        assertEquals(900L, out.first().atMs)
    }

    @Test
    fun `merge is deterministic regardless of arrival order`() {
        val a = entry("a", 100L)
        val b = entry("b", 200L)
        val one = Downloads.mergeEntry(Downloads.mergeEntry(emptyList(), a), b)
        val two = Downloads.mergeEntry(Downloads.mergeEntry(emptyList(), b), a)
        assertEquals(one, two)
    }
}
