package app.bunmusic

import app.bunmusic.data.Prefs
import app.bunmusic.data.StreamInfo
import app.bunmusic.data.YtStream
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which audio stream gets picked, and when a resolved URL goes stale.
 *
 * YouTube answers with a dozen adaptive formats per video. Choosing the wrong
 * one is not a crash — it is a listener on mobile data quietly pulling 256 kbps
 * after asking for "Low", or an Opus stream that the platform decoder refuses.
 */
class StreamPickTest {

    private fun format(mime: String) = buildJsonObject { put("mimeType", mime) }

    private val opus = format("audio/webm; codecs=opus")
    private val aac = format("audio/mp4; codecs=mp4a.40.2")
    private val bare = buildJsonObject { }

    @Test
    fun `no candidates means no answer`() {
        assertNull(YtStream.pick(emptyList(), Prefs.Quality.HIGH))
        assertNull(YtStream.pick(emptyList(), Prefs.Quality.NORMAL))
        assertNull(YtStream.pick(emptyList(), Prefs.Quality.LOW))
    }

    @Test
    fun `a single candidate is taken whatever the quality`() {
        val only = listOf(aac to 128_000L)
        for (q in Prefs.Quality.entries) {
            assertSame("quality $q", only[0].first, YtStream.pick(only, q)!!.first)
        }
    }

    @Test
    fun `high takes the highest bitrate`() {
        val candidates = listOf(aac to 128_000L, aac to 256_000L, aac to 70_000L)
        assertEquals(256_000L, YtStream.pick(candidates, Prefs.Quality.HIGH)!!.second)
    }

    @Test
    fun `high prefers opus when the bitrates are close`() {
        // Same quality, smaller file. The nudge is 20 kbps, so 240 kbps Opus
        // beats 250 kbps AAC.
        val candidates = listOf(aac to 250_000L, opus to 240_000L)
        assertSame(opus, YtStream.pick(candidates, Prefs.Quality.HIGH)!!.first)
    }

    @Test
    fun `high still takes a clearly better aac`() {
        // The nudge must not override a real gap, or "High" stops meaning high.
        val candidates = listOf(aac to 300_000L, opus to 240_000L)
        assertSame(aac, YtStream.pick(candidates, Prefs.Quality.HIGH)!!.first)
    }

    @Test
    fun `a format without a mime type is not treated as opus`() {
        val candidates = listOf(bare to 200_000L, aac to 190_000L)
        assertSame(bare, YtStream.pick(candidates, Prefs.Quality.HIGH)!!.first)
    }

    @Test
    fun `low takes the smallest stream under the data saver ceiling`() {
        val candidates = listOf(aac to 128_000L, aac to 48_000L, aac to 70_000L, aac to 256_000L)
        assertEquals(48_000L, YtStream.pick(candidates, Prefs.Quality.LOW)!!.second)
    }

    @Test
    fun `low still returns something when nothing is cheap`() {
        // A video whose only audio is 160 kbps should still play, not error out.
        val candidates = listOf(aac to 160_000L, aac to 200_000L)
        assertEquals(160_000L, YtStream.pick(candidates, Prefs.Quality.LOW)!!.second)
    }

    @Test
    fun `low treats exactly 96 kbps as cheap enough`() {
        val candidates = listOf(aac to 96_000L, aac to 130_000L)
        assertEquals(96_000L, YtStream.pick(candidates, Prefs.Quality.LOW)!!.second)
    }

    @Test
    fun `normal lands on the nearest bitrate to the target`() {
        // 128 kbps target: 130 is two away, 70 is fifty-eight away.
        val candidates = listOf(aac to 70_000L, aac to 130_000L, aac to 256_000L)
        assertEquals(130_000L, YtStream.pick(candidates, Prefs.Quality.NORMAL)!!.second)
    }

    @Test
    fun `normal can pick a stream above the target`() {
        // 128 kbps target, nothing at or below it: 150 is closer than 200.
        val candidates = listOf(aac to 150_000L, aac to 200_000L)
        assertEquals(150_000L, YtStream.pick(candidates, Prefs.Quality.NORMAL)!!.second)
    }

    @Test
    fun `the quality enum targets are ordered`() {
        assertTrue(Prefs.Quality.LOW.bitrate < Prefs.Quality.NORMAL.bitrate)
        assertTrue(Prefs.Quality.NORMAL.bitrate < Prefs.Quality.HIGH.bitrate)
    }

    // ------------------------------------------------------------- staleness

    @Test
    fun `a fresh stream is not stale`() {
        assertTrue(!info().stale)
    }

    @Test
    fun `a stream older than five hours is stale`() {
        val old = info(resolvedAt = System.currentTimeMillis() - 6 * 60 * 60 * 1000L)
        assertTrue("signed URLs expire around six hours", old.stale)
    }

    @Test
    fun `the cache key is content, not the signed url`() {
        // Two resolves of the same song hand back different URLs; the cache must
        // still treat them as one track or every replay is a re-download.
        val a = info(url = "https://rr1.example/one?sig=aaa")
        val b = info(url = "https://rr1.example/two?sig=bbb")
        assertEquals("yt:abc123:251", a.key)
        assertEquals(a.key, b.key)
    }

    private fun info(
        url: String = "https://rr1.example/one?sig=aaa",
        resolvedAt: Long = System.currentTimeMillis(),
    ) = StreamInfo(
        videoId = "abc123",
        url = url,
        itag = 251,
        mime = "audio/webm",
        bitrate = 128_000L,
        lengthBytes = 3_000_000,
        resolvedAt = resolvedAt,
    )
}
