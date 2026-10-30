package app.bunmusic.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Synced lyrics from LRCLIB (open, community-maintained, no API key).
 * Timestamps are parsed into [LyricLine] so the player can highlight the
 * line that is being sung right now.
 */
object LyricsApi {

    private const val UA = "BunMusic/1.0 (Android; +https://github.com/Iotatg)"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun forTrack(track: Track): Lyrics = withContext(Dispatchers.IO) {
        val artist = track.artist.substringBefore(",").trim()
        val title = track.title.substringBefore("(").trim()

        val direct = runCatching {
            val q = buildString {
                append("https://lrclib.net/api/get?artist_name=").append(enc(artist))
                append("&track_name=").append(enc(title))
                if (track.album.isNotBlank()) append("&album_name=").append(enc(track.album))
                if (track.durationMs > 0) append("&duration=").append(track.durationMs / 1000)
            }
            fetch(q)
        }.getOrNull()

        val body = direct ?: runCatching {
            fetch("https://lrclib.net/api/search?q=" + enc("$artist $title"))
                ?.let { text ->
                    val arr = json.parseToJsonElement(text) as? JsonArray
                    (arr?.firstOrNull() as? JsonObject)?.toString()
                }
        }.getOrNull()

        if (body.isNullOrBlank()) return@withContext Lyrics()

        val obj = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: return@withContext Lyrics()
        val synced = (obj["syncedLyrics"] as? JsonPrimitive)?.contentOrNull
        val plain = (obj["plainLyrics"] as? JsonPrimitive)?.contentOrNull
        Lyrics(
            lines = if (synced.isNullOrBlank()) emptyList() else parseLrc(synced),
            plain = plain,
        )
    }

    private fun fetch(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 12000
        conn.readTimeout = 12000
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Accept", "application/json")
        return try {
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun parseLrc(raw: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        val regex = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")
        for (line in raw.lines()) {
            val matches = regex.findAll(line).toList()
            if (matches.isEmpty()) continue
            val text = line.substring(matches.last().range.last + 1).trim()
            if (text.isEmpty()) continue
            for (m in matches) {
                val min = m.groupValues[1].toLongOrNull() ?: 0
                val sec = m.groupValues[2].toLongOrNull() ?: 0
                val fracRaw = m.groupValues[3]
                val frac = if (fracRaw.isEmpty()) 0L else fracRaw.padEnd(3, '0').take(3).toLong()
                out.add(LyricLine(min * 60_000 + sec * 1000 + frac, text))
            }
        }
        return out.sortedBy { it.atMs }
    }
}
