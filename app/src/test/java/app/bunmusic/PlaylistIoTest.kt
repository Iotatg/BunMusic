package app.bunmusic

import app.bunmusic.data.PlaylistIo
import app.bunmusic.data.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M3U in and out.
 *
 * The files this has to survive are not the ones we write: they come from VLC,
 * from a car stereo's USB stick, from a Windows machine with CRLF and a BOM. The
 * parser is tested against those, not against its own output.
 */
class PlaylistIoTest {

    private fun t(id: String, title: String = id, artist: String = "A", video: String = "v$id") =
        Track(id = id, title = title, artist = artist, durationMs = 185_000L, videoId = video)

    @Test
    fun `export writes a header, a playlist name and one extinf per track`() {
        val text = PlaylistIo.export("Road", listOf(t("1", "One"), t("2", "Two")))
        assertTrue(text.startsWith("#EXTM3U\n"))
        assertTrue(text.contains("#PLAYLIST:Road"))
        assertEquals(2, text.lines().count { it.startsWith("#EXTINF:") })
        assertTrue(text.contains("bunmusic://track/1"))
    }

    @Test
    fun `export and parse round trip`() {
        val tracks = listOf(t("1", "One"), t("2", "Two"))
        val parsed = PlaylistIo.parse(PlaylistIo.export("Road", tracks))
        assertEquals("Road", parsed.name)
        assertEquals(listOf("1", "2"), parsed.ids)
        assertEquals(0, parsed.skipped)
    }

    @Test
    fun `parse survives a BOM and windows line endings`() {
        val text = "\uFEFF#EXTM3U\r\nbunmusic://track/1\r\nbunmusic://track/2\r\n"
        val parsed = PlaylistIo.parse(text)
        assertEquals(listOf("1", "2"), parsed.ids)
    }

    @Test
    fun `parse understands a youtube url`() {
        val parsed = PlaylistIo.parse("https://music.youtube.com/watch?v=abc123&list=x")
        assertEquals(listOf("abc123"), parsed.videoIds)
    }

    @Test
    fun `parse keeps a bare id and skips a path`() {
        val parsed = PlaylistIo.parse("dQw4w9WgXc\n/home/music/song.mp3\n")
        assertEquals(listOf("dQw4w9WgXc"), parsed.ids)
        assertEquals(1, parsed.skipped)
    }

    @Test
    fun `parse ignores comments and blank lines`() {
        val parsed = PlaylistIo.parse("# just a note\n\n#EXTINF:12,A - B\n")
        assertTrue(parsed.isEmpty)
        assertEquals(0, parsed.skipped)
    }

    @Test
    fun `resolve reports what the library does not have`() {
        val all = listOf(t("1"), t("2"))
        val parsed = PlaylistIo.parse("bunmusic://track/1\nbunmusic://track/9\n")
        val (found, missing) = PlaylistIo.resolve(parsed, all)
        assertEquals(listOf("1"), found.map { it.id })
        assertEquals(1, missing)
    }

    @Test
    fun `resolve matches a video id when the local id differs`() {
        val all = listOf(Track(id = "local-1", title = "X", artist = "A", videoId = "abc"))
        val parsed = PlaylistIo.parse("https://youtu.be/watch?v=abc")
        val (found, missing) = PlaylistIo.resolve(parsed, all)
        assertEquals(listOf("local-1"), found.map { it.id })
        assertEquals(0, missing)
    }

    @Test
    fun `file name drops characters a filesystem dislikes`() {
        assertEquals("Road_trip.m3u", PlaylistIo.fileName("Road/trip"))
        assertEquals("playlist.m3u", PlaylistIo.fileName("   "))
    }

    @Test
    fun `an empty file has no name`() {
        assertNull(PlaylistIo.parse("").name)
    }
}
