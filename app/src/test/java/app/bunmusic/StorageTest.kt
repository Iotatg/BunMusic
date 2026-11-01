package app.bunmusic

import app.bunmusic.data.Storage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The storage split: four buckets, one of them precious. */
class StorageTest {

    private fun buckets(downloads: Long = 0, streams: Long = 0, images: Long = 0, library: Long = 0) =
        Storage.buckets(downloads, if (downloads > 0) 3 else 0, streams, images, library)

    @Test
    fun `total is the sum of every bucket`() {
        val list = buckets(100, 50, 25, 5)
        assertEquals(180L, Storage.total(list))
    }

    @Test
    fun `the plan only removes buckets that can be rebuilt`() {
        val list = buckets(downloads = 900, streams = 400, images = 100, library = 10)
        val plan = Storage.cleanupPlan(list, limitBytes = 500)
        assertTrue(plan.none { it.id == "downloads" })
        assertTrue(plan.any { it.id == "streams" })
    }

    @Test
    fun `nothing is planned when under the limit`() {
        assertTrue(Storage.cleanupPlan(buckets(10, 10), limitBytes = 100).isEmpty())
    }

    @Test
    fun `the plan is big enough to clear the overage`() {
        val list = buckets(0, 800, 300, 0)
        val plan = Storage.cleanupPlan(list, limitBytes = 500)
        assertTrue(Storage.total(plan) >= 600)
    }

    @Test
    fun `a zero bucket is never offered for cleaning`() {
        val list = buckets(streams = 0, images = 500)
        val plan = Storage.cleanupPlan(list, limitBytes = 100)
        assertEquals(listOf("images"), plan.map { it.id })
    }

    @Test
    fun `verdict speaks in the units it is given`() {
        assertEquals("Nothing stored yet", Storage.verdict(0, 100))
        assertTrue(Storage.verdict(50, 100).contains("under your limit"))
        assertTrue(Storage.verdict(150, 100).contains("over your limit"))
    }

    @Test
    fun `human rounds to the largest sensible unit`() {
        assertEquals("512 B", Storage.human(512))
        assertEquals("1 KB", Storage.human(1024))
        assertEquals("2 MB", Storage.human(2L * 1024 * 1024))
        assertEquals("1.5 GB", Storage.human(1_610_612_736L))
    }
}
