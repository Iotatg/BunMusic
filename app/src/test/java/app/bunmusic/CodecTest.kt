package app.bunmusic

import app.bunmusic.data.Backup
import app.bunmusic.data.Source
import app.bunmusic.data.Store
import app.bunmusic.data.Track
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two encoders a track has to survive.
 *
 * One is the row format used by playlists, history and the restored queue; the
 * other is the JSON in a backup file. Both are lossy if a field is dropped
 * quietly, and neither failure shows up until the listener restores a backup
 * three months later and finds the album names gone.
 */
class CodecTest {

    private val youtube = Track(
        id = "yt:abc",
        title = "Midnight City",
        artist = "M83",
        album = "Hurry Up, We're Dreaming",
        artUrl = "https://img.example/art.jpg",
        durationMs = 244_000L,
        source = Source.YOUTUBE,
        videoId = "abc",
    )

    private val local = Track(
        id = "local:1",
        title = "Bundled",
        artist = "BunMusic",
        album = "",
        artAsset = "art/bun.png",
        durationMs = 30_000L,
        source = Source.LOCAL,
        assetAudio = "audio/bun.ogg",
    )

    // ------------------------------------------------------------- row format

    @Test
    fun `a streamed track round trips through the row format`() {
        assertEquals(youtube, Store.decodeTrack(Store.encodeTrack(youtube)))
    }

    @Test
    fun `a local track keeps its asset path`() {
        val back = Store.decodeTrack(Store.encodeTrack(local))!!
        assertEquals(local, back)
        assertEquals("audio/bun.ogg", back.assetAudio)
        assertTrue(back.isLocal)
    }

    @Test
    fun `a blank artwork url decodes back to null not to an empty string`() {
        val back = Store.decodeTrack(Store.encodeTrack(local))!!
        assertNull(back.artUrl)
        assertEquals("art/bun.png", back.artAsset)
    }

    @Test
    fun `a truncated row is rejected rather than half read`() {
        assertNull(Store.decodeTrack("a\u0001b\u0001c"))
        assertNull(Store.decodeTrack(""))
    }

    @Test
    fun `a row without a title is rejected`() {
        val broken = Store.encodeTrack(youtube).replace("\u0001Midnight City\u0001", "\u0001\u0001")
        assertNull(Store.decodeTrack(broken))
    }

    @Test
    fun `an unparseable duration becomes zero`() {
        val row = Store.encodeTrack(youtube).replace("\u0001244000\u0001", "\u0001not-a-number\u0001")
        assertEquals(0L, Store.decodeTrack(row)!!.durationMs)
    }

    @Test
    fun `an unknown source falls back to youtube`() {
        val row = Store.encodeTrack(youtube).replace("\u0001YOUTUBE\u0001", "\u0001SPOTIFY\u0001")
        assertEquals(Source.YOUTUBE, Store.decodeTrack(row)!!.source)
    }

    @Test
    fun `a title with a line break survives the row format`() {
        // This is the reason the session stores rows with \u0002 and not \n: an
        // ID3 title can contain a break, and the queue is one joined string.
        val odd = youtube.copy(title = "First line\nsecond line")
        val back = Store.decodeTrack(Store.encodeTrack(odd))!!
        assertEquals("First line\nsecond line", back.title)
        assertEquals(odd, back)
    }

    @Test
    fun `separators inside a title survive`() {
        val odd = youtube.copy(title = "a|b\tc", artist = "x\ty")
        assertEquals(odd, Store.decodeTrack(Store.encodeTrack(odd)))
    }

    // ------------------------------------------------------------------- json

    @Test
    fun `a track round trips through backup json`() {
        val back = Backup.trackFrom(Backup.trackJson(youtube))!!
        assertEquals(youtube, back)
    }

    @Test
    fun `a local track round trips through backup json`() {
        val back = Backup.trackFrom(Backup.trackJson(local))!!
        assertEquals(local, back)
        assertEquals(Source.LOCAL, back.source)
    }

    @Test
    fun `json writes empty artwork as an empty string and reads it back as null`() {
        val json = Backup.trackJson(local).toString()
        assertTrue(json.contains("\"artUrl\":\"\""))
        assertNull(Backup.trackFrom(Backup.trackJson(local))!!.artUrl)
    }

    @Test
    fun `a json track without an id is rejected`() {
        val obj = Json.parseToJsonElement("""{"title":"x","artist":"y"}""") as JsonObject
        assertNull(Backup.trackFrom(obj))
    }

    @Test
    fun `a json track without a title is rejected`() {
        val obj = Json.parseToJsonElement("""{"id":"x","artist":"y"}""") as JsonObject
        assertNull(Backup.trackFrom(obj))
    }

    @Test
    fun `missing optional json fields become empty rather than failing`() {
        val obj = Json.parseToJsonElement("""{"id":"x","title":"t"}""") as JsonObject
        val track = Backup.trackFrom(obj)!!
        assertEquals("", track.artist)
        assertEquals("", track.album)
        assertEquals(0L, track.durationMs)
        assertEquals(Source.YOUTUBE, track.source)
    }

    @Test
    fun `a duration written as a string still parses`() {
        val obj = Json.parseToJsonElement("""{"id":"x","title":"t","durationMs":"1234"}""") as JsonObject
        assertEquals(1234L, Backup.trackFrom(obj)!!.durationMs)
    }

    @Test
    fun `an unknown json source falls back to youtube`() {
        val obj = Json.parseToJsonElement("""{"id":"x","title":"t","source":"SPOTIFY"}""") as JsonObject
        assertEquals(Source.YOUTUBE, Backup.trackFrom(obj)!!.source)
    }

    @Test
    fun `backup json keeps unicode titles intact`() {
        val odd = youtube.copy(title = "सावन", artist = "ए आर  रहमान")
        assertEquals(odd, Backup.trackFrom(Backup.trackJson(odd)))
    }
}
