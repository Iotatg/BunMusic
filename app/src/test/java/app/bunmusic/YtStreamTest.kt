package app.bunmusic

import app.bunmusic.data.YtStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stream resolution can only be fully proven on a device with a residential
 * network. From a data-centre IP YouTube answers "Sign in to confirm you're not
 * a bot", so this test asserts the resolver either returns a playable URL or
 * fails with a clear, catchable error (never a crash).
 */
class YtStreamTest {

    @Test
    fun resolves_or_reports_clearly(): Unit = runBlocking {
        val id = "NAkZ8gUXcIE"
        val result = runCatching { YtStream.audioUrl(id) }
        result.onSuccess { url ->
            println("stream[$id] → ${url.take(140)}…")
            assertTrue("expected http url", url.startsWith("http"))
        }.onFailure { t ->
            println("stream[$id] blocked in this environment → ${t.message}")
            assertTrue(
                "error should be descriptive",
                t is IllegalStateException && (t.message?.contains("Could not resolve stream") == true),
            )
        }
    }
}
