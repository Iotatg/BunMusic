package app.bunmusic

import app.bunmusic.data.YtMusic
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Live checks for the 1.0.1 browse surfaces: Explore, Charts and mood pages.
 * These hit the real YouTube Music endpoints.
 */
class ExploreTest {

    @Test
    fun explore_returns_moods_and_new_releases() = runBlocking {
        val page = YtMusic.explore()
        println("explore → moods=${page.moods.size} newReleases=${page.newReleases.size} trending=${page.trending.size}")
        page.moods.take(6).forEach { println("  mood: ${it.title} [${it.params.take(12)}…]") }
        page.newReleases.take(3).forEach { println("  new: ${it.title} — ${it.subtitle}") }
        assertTrue("expected mood tiles", page.moods.isNotEmpty())
        assertTrue("expected new releases", page.newReleases.isNotEmpty())
        assertTrue("expected trending tracks", page.trending.isNotEmpty())
    }

    @Test
    fun charts_returns_shelves() = runBlocking {
        val shelves = YtMusic.charts()
        println("charts → ${shelves.size} shelves")
        shelves.forEach { println("  [${it.title}] items=${it.items.size} tracks=${it.tracks.size}") }
        assertTrue("expected chart shelves", shelves.isNotEmpty())
    }

    @Test
    fun mood_page_returns_collections() = runBlocking {
        val page = YtMusic.explore()
        val mood = page.moods.firstOrNull { it.params.isNotBlank() }
        if (mood == null) {
            println("no mood with params — skipping")
            return@runBlocking
        }
        val shelves = YtMusic.browseShelves(mood.browseId, mood.params)
        println("mood '${mood.title}' → ${shelves.size} shelves, ${shelves.sumOf { it.items.size }} collections")
        shelves.take(3).forEach { println("  [${it.title}] ${it.items.size}") }
        assertTrue("expected shelves for ${mood.title}", shelves.isNotEmpty())
    }
}
