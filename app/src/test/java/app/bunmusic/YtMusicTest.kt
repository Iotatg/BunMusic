package app.bunmusic

import app.bunmusic.data.YtMusic
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Live integration checks for the BunTube client. These hit the real
 * YouTube Music endpoints — they verify that search, the home feed and album
 * pages still parse correctly after API changes.
 */
class YtMusicTest {

    @Test
    fun search_returns_songs_albums_and_artists() = runBlocking {
        val r = YtMusic.search("lofi beats")
        println("search → songs=${r.songs.size} albums=${r.albums.size} artists=${r.artists.size} playlists=${r.playlists.size}")
        r.songs.take(5).forEach { println("  ♪ ${it.title} — ${it.artist} [${it.videoId}] ${it.durationMs}ms") }
        r.artists.take(3).forEach { println("  ◉ ${it.title} — ${it.subtitle} [${it.browseId}]") }
        assertTrue("expected songs", r.songs.isNotEmpty())
        assertTrue("expected a videoId", r.songs.first().videoId.isNotBlank())
    }

    @Test
    fun home_feed_has_shelves() = runBlocking {
        val shelves = YtMusic.home()
        println("home → ${shelves.size} shelves")
        shelves.take(6).forEach { s ->
            println("  [${s.title}] items=${s.items.size} tracks=${s.tracks.size}")
        }
        assertTrue("expected shelves", shelves.isNotEmpty())
    }

    @Test
    fun album_page_has_tracks() = runBlocking {
        val search = YtMusic.search("after hours weeknd")
        val album = search.albums.firstOrNull()
        if (album == null) {
            println("no album in search results — skipping")
            return@runBlocking
        }
        val detail = YtMusic.collection(album.browseId)
        println("album → ${detail.collection.title} / ${detail.tracks.size} tracks")
        detail.tracks.take(5).forEach { println("  ${it.title} — ${it.artist}") }
        assertTrue("expected tracks", detail.tracks.isNotEmpty())
    }
}
