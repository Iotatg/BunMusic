package app.bunmusic

import app.bunmusic.data.hq
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * `hq()` is called from several paths (YtMusic parsing, Playback metadata, the
 * palette extractor, and ArtKeys cache keys), so it must be a pure normalisation
 * with a fixed point. The old chained `replace` calls double-prefixed the
 * filename (`maxresmaxresdefault.jpg`) and grew on every call — which broke the
 * artwork cache key and made cached thumbnails unfindable.
 */
class HqTest {

    private val variants = listOf(
        "hqdefault",
        "sddefault",
        "mqdefault",
        "default",
        "maxresdefault",
    )

    @Test
    fun `every vi variant normalises to maxresdefault`() {
        for (v in variants) {
            val url = "https://i.ytimg.com/vi/abc123/$v.jpg"
            assertEquals(
                "https://i.ytimg.com/vi/abc123/maxresdefault.jpg",
                hq(url),
            )
        }
    }

    @Test
    fun `normalisation is idempotent`() {
        for (v in variants) {
            val url = "https://i.ytimg.com/vi/abc123/$v.jpg"
            val once = hq(url)
            assertEquals("hq(hq(u)) must equal hq(u) for $v", once, hq(once))
            assertEquals(once, hq(hq(hq(url))))
        }
        val sized = "https://lh3.googleusercontent.com/abc=w60-h60-l90-rj"
        assertEquals(hq(sized), hq(hq(sized)))
    }

    @Test
    fun `never double-prefixes maxres`() {
        val once = hq("https://i.ytimg.com/vi/abc123/hqdefault.jpg")
        assertFalse(
            "must not contain the corrupted maxresmaxres token",
            once.contains("maxresmaxres"),
        )
    }

    @Test
    fun `sized googleuser urls are upgraded once`() {
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w1080-h1080-l90-rj",
            hq("https://lh3.googleusercontent.com/abc=w60-h60-l90-rj"),
        )
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w1080-h1080-l90-rj",
            hq("https://lh3.googleusercontent.com/abc=w1080-h1080-l90-rj"),
        )
    }

    @Test
    fun `blank and unrelated urls pass through unchanged`() {
        assertEquals("", hq(""))
        assertEquals("   ", hq("   "))
        assertEquals("https://example.com/cover.png", hq("https://example.com/cover.png"))
        // A vi-style URL with a query string is not touched by the /vi/ branch.
        assertEquals(
            "https://i.ytimg.com/vi/abc123/hqdefault.jpg?sqp=1",
            hq("https://i.ytimg.com/vi/abc123/hqdefault.jpg?sqp=1"),
        )
    }
}
