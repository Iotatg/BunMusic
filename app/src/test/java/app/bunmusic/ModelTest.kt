package app.bunmusic

import app.bunmusic.data.CollectionKind
import app.bunmusic.data.LyricLine
import app.bunmusic.data.Lyrics
import app.bunmusic.data.Playlist
import app.bunmusic.data.SearchResults
import app.bunmusic.data.Source
import app.bunmusic.data.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The small derivations on the models.
 *
 * These are one-liners, and every one of them is read on a screen: a track's
 * URI decides whether it plays at all, `synced` decides whether the lyrics panel
 * scrolls, and an empty search result decides whether "nothing found" is shown
 * or a blank page.
 */
class ModelTest {

    @Test
    fun `a streamed track addresses itself by video id`() {
        val t = Track(id = "x", title = "t", artist = "a", videoId = "abc123")
        assertEquals("yt://abc123", t.uri())
        assertFalse(t.isLocal)
    }

    @Test
    fun `a bundled track addresses itself as an asset`() {
        val t = Track(
            id = "x", title = "t", artist = "a",
            source = Source.LOCAL, assetAudio = "audio/bun.ogg",
        )
        assertEquals("asset:///audio/bun.ogg", t.uri())
        assertTrue(t.isLocal)
    }

    @Test
    fun `a local track with no asset still produces a valid uri`() {
        // Better a URI that fails to open than a null pointer deep in the player.
        val t = Track(id = "x", title = "t", artist = "a", source = Source.LOCAL)
        assertEquals("asset:///", t.uri())
    }

    @Test
    fun `synced lyrics are the ones with timed lines`() {
        assertTrue(Lyrics(lines = listOf(LyricLine(0, "hi"))).synced)
        assertFalse(Lyrics(plain = "hi").synced)
        assertFalse(Lyrics().synced)
    }

    @Test
    fun `blank plain lyrics count as empty`() {
        assertTrue(Lyrics().isEmpty)
        assertTrue(Lyrics(plain = "   ").isEmpty)
        assertFalse(Lyrics(plain = "a line").isEmpty)
        assertFalse(Lyrics(lines = listOf(LyricLine(0, "x"))).isEmpty)
    }

    @Test
    fun `a search with nothing in any bucket is empty`() {
        assertTrue(SearchResults().isEmpty)
        assertFalse(SearchResults(songs = listOf(Track(id = "x", title = "t", artist = "a"))).isEmpty)
    }

    @Test
    fun `a playlist reports its own size`() {
        assertEquals(0, Playlist(id = "p", name = "Empty").size)
        assertEquals(2, Playlist(id = "p", name = "Two", trackIds = listOf("a", "b")).size)
    }

    @Test
    fun `collections default to a playlist`() {
        assertEquals(CollectionKind.PLAYLIST, app.bunmusic.data.Collection(id = "c", title = "t").kind)
    }
}
