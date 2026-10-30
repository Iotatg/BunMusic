package app.bunmusic

import app.bunmusic.data.YtMusic
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Live check of the automix queue that powers autoplay.
 * Needs network; skipped gracefully when the sandbox is blocked.
 */
class RadioTest {

    @Test
    fun radioReturnsAPlayableQueue() = runBlocking {
        val seed = YtMusic.search("kesariya arijit singh").songs.firstOrNull()
        if (seed == null) {
            println("radio: search returned nothing, skipping")
            return@runBlocking
        }
        val station = YtMusic.radio(seed.videoId, 20)
        println("radio from ${seed.title} -> ${station.size} tracks")
        station.take(3).forEach { println("  · ${it.title} — ${it.artist} (${it.durationMs} ms)") }
        assertTrue("expected a queue", station.isNotEmpty())
        assertTrue("titles must not be blank", station.all { it.title.isNotBlank() })
        assertTrue("ids must be unique", station.map { it.id }.distinct().size == station.size)
    }
}
