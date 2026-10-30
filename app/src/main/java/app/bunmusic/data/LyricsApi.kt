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
 * Lyrics, multi-provider and fail-soft:
 *
 *  1. LRCLIB exact match  → time-synced LRC
 *  2. LRCLIB search       → first hit, synced or plain
 *  3. lyrics.ovh          → plain text fallback
 *
 * A miss is never an error: the player just shows "no lyrics".
 */
object LyricsApi {

    private const val UA = "BunMusic/1.0.9 (Android; +https://github.com/Iotatg)"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val cache = LinkedHashMap<String, Lyrics>()
    private const val CACHE_MAX = 40

    suspend fun forTrack(track: Track): Lyrics = withContext(Dispatchers.IO) {
        val key = track.id
        synchronized(cache) { cache[key] }?.let { return@withContext it }

        val artist = clean(track.artist.substringBefore(","))
        val title = clean(track.title)

        var result = fromLrclib(artist, title, track)
        if (result.isEmpty) result = fromOvh(artist, title)

        synchronized(cache) {
            cache[key] = result
            while (cache.size > CACHE_MAX) cache.remove(cache.keys.first())
        }
        result
    }

    private fun clean(s: String): String = s
        .substringBefore("(")
        .substringBefore("[")
        .replace(Regex("(?i)\\s*(feat\\.?|ft\\.?|featuring)\\s+.*"), "")
        .replace(Regex("(?i)\\s*-\\s*(remaster(ed)?|live|radio edit|version).*"), "")
        .trim()

    // --------------------------------------------------------------- lrclib

    private fun fromLrclib(artist: String, title: String, track: Track): Lyrics {
        val direct = runCatching {
            val url = buildString {
                append("https://lrclib.net/api/get?artist_name=").append(enc(artist))
                append("&track_name=").append(enc(title))
                if (track.album.isNotBlank()) append("&album_name=").append(enc(track.album))
                if (track.durationMs > 0) append("&duration=").append(track.durationMs / 1000)
            }
            fetch(url)
        }.getOrNull()

        val body = direct ?: runCatching {
            fetch("https://lrclib.net/api/search?q=" + enc("$artist $title"))?.let { text ->
                val arr = json.parseToJsonElement(text) as? JsonArray
                (arr?.firstOrNull() as? JsonObject)?.toString()
            }
        }.getOrNull()

        if (body.isNullOrBlank()) return Lyrics()
        val obj = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return Lyrics()
        val synced = (obj["syncedLyrics"] as? JsonPrimitive)?.contentOrNull
        val plain = (obj["plainLyrics"] as? JsonPrimitive)?.contentOrNull
        return Lyrics(
            lines = if (synced.isNullOrBlank()) emptyList() else parseLrc(synced),
            plain = plain,
            provider = "LRCLIB",
        )
    }

    // ----------------------------------------------------------- lyrics.ovh

    private fun fromOvh(artist: String, title: String): Lyrics {
        val raw = runCatching {
            fetch("https://api.lyrics.ovh/v1/" + enc(artist) + "/" + enc(title))
        }.getOrNull() ?: return Lyrics()
        val obj = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return Lyrics()
        val text = (obj["lyrics"] as? JsonPrimitive)?.contentOrNull ?: return Lyrics()
        if (text.isBlank()) return Lyrics()
        return Lyrics(lines = emptyList(), plain = text.trim(), provider = "lyrics.ovh")
    }

    // ------------------------------------------------------------- utilities

    private fun fetch(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 12_000
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
