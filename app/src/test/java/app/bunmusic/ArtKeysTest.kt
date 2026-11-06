package app.bunmusic

import app.bunmusic.data.hq
import app.bunmusic.widget.ArtKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cache-key bug that made "cached thumbnails don't show" true.
 *
 * The old disk key included the requested size and corner radius, so the
 * 96 px / 20 px queue row could never find the 180 px / 14 px file the main
 * widget had written. One canonical file per cover is the whole fix.
 */
class ArtKeysTest {

    private val raw = "https://i.ytimg.com/vi/abc123/hqdefault.jpg"
    private val high = hq(raw)

    @Test
    fun `one cover writes exactly one disk file`() {
        val file = ArtKeys.diskFile(raw)
        assertNotNull(file)
        assertEquals(file, ArtKeys.diskFile(raw))
        assertNotEquals(raw, high)
    }

    @Test
    fun `the raw and high resolution spellings share a file`() {
        // Playback hands Coil hq(artUrl); widgets were handed artUrl. Both must
        // land on the same entry or the notification can never find the cover
        // the player already cached.
        assertEquals(ArtKeys.canonical(raw), ArtKeys.canonical(high))
        assertEquals(ArtKeys.diskFile(raw), ArtKeys.diskFile(high))
    }

    @Test
    fun `size and radius are memory-only derivations, not disk keys`() {
        val queue = ArtKeys.memory(raw, 96, 20)
        val compact = ArtKeys.memory(raw, 320, 12)
        val main = ArtKeys.memory(raw, 180, 14)
        assertNotNull(queue)
        assertNotEquals(queue, compact)

        // All three derive from the same canonical md5 prefix.
        assertEquals(queue!!.substringBefore('_'), compact!!.substringBefore('_'))
        assertEquals(queue.substringBefore('_'), main!!.substringBefore('_'))
        assertEquals(ArtKeys.canonical(raw), queue.substringBefore('_'))
    }

    @Test
    fun `different covers never collide`() {
        val other = "https://i.ytimg.com/vi/xyz789/hqdefault.jpg"
        assertNotEquals(ArtKeys.canonical(raw), ArtKeys.canonical(other))
        assertNotEquals(ArtKeys.diskFile(raw), ArtKeys.diskFile(other))
    }

    @Test
    fun `blank urls have no key at all`() {
        assertNull(ArtKeys.canonical(null))
        assertNull(ArtKeys.canonical(""))
        assertNull(ArtKeys.canonical("   "))
        assertNull(ArtKeys.diskFile(null))
        assertNull(ArtKeys.memory(null, 96, 20))
    }

    @Test
    fun `legacy size-suffixed files are recognised as stale`() {
        assertTrue(ArtKeys.isCanonicalName("0123456789abcdef0123456789abcdef.png"))
        assertFalse(ArtKeys.isCanonicalName("0123456789abcdef0123456789abcdef_96_20.png"))
        assertFalse(ArtKeys.isCanonicalName("cover.png"))
        assertFalse(ArtKeys.isCanonicalName("0123456789abcdef0123456789abcdef.jpg"))
        assertFalse(ArtKeys.isCanonicalName("0123456789abcdef0123456789abcde.png"))
    }
}
