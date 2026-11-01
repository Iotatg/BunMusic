package app.bunmusic.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * ListenBrainz scrobbling.
 *
 * Deliberately the smallest possible client: a user token, one endpoint, and a
 * disk queue. Nothing is sent unless a token is saved, a failure is never shown
 * to the listener, and a submission that cannot go out now is kept for later —
 * scrobbling is bookkeeping, and bookkeeping must never interrupt the music.
 *
 * Last.fm was the obvious alternative and was rejected: it needs a shared API
 * secret shipped inside the APK plus a signed request, which cannot be done
 * without a server. ListenBrainz takes a token the user pastes in, and is open.
 */
object Scrobble {

    private const val ENDPOINT = "https://api.listenbrainz.org/1/submit-listens"
    private const val CLIENT = "BunMusic/1.2.0"

    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.getSharedPreferences("bunmusic_scrobble", Context.MODE_PRIVATE)
    }

    val enabled: Boolean get() = ::sp.isInitialized && sp.getBoolean("enabled", false) && token.isNotBlank()

    var token: String
        get() = if (::sp.isInitialized) sp.getString("token", "") ?: "" else ""
        set(value) {
            if (!::sp.isInitialized) return
            sp.edit().putString("token", value.trim()).apply()
        }

    fun setEnabled(on: Boolean) {
        if (!::sp.isInitialized) return
        sp.edit().putBoolean("enabled", on).apply()
    }

    fun lastSubmitAt(): Long = if (::sp.isInitialized) sp.getLong("last", 0L) else 0L

    fun pending(): Int = if (::sp.isInitialized) {
        (sp.getString("queue", "") ?: "").split('\n').count { it.isNotBlank() }
    } else 0

    /** Sent the moment a track starts, so the profile shows what is on now. */
    suspend fun nowPlaying(track: Track) = post(payload(track, "playing_now", 0L))

    /** Sent once a track has actually been listened to. */
    suspend fun submit(track: Track, startedAtSec: Long) =
        post(payload(track, "single", startedAtSec))

    private fun payload(track: Track, type: String, at: Long): String {
        val meta = JSONObject()
            .put("artist_name", track.artist.ifBlank { "Unknown artist" })
            .put("track_name", track.title)
            .put("additional_info", JSONObject()
                .put("media_player", "BunMusic")
                .put("submission_client", CLIENT)
                .put("duration_ms", track.durationMs))
        if (track.album.isNotBlank()) meta.put("release_name", track.album)
        val listen = JSONObject().put("track_metadata", meta)
        if (type == "single") listen.put("listened_at", at)
        return JSONObject()
            .put("listen_type", type)
            .put("payload", JSONArray().put(listen))
            .toString()
    }

    private suspend fun post(body: String) {
        if (!enabled) return
        withContext(Dispatchers.IO) {
            val ok = runCatching { send(body) }.getOrDefault(false)
            if (ok) {
                sp.edit().putLong("last", System.currentTimeMillis()).apply()
                flush()
            } else if (!body.contains("\"playing_now\"")) {
                // "playing now" is worthless five minutes later; a real listen is not.
                enqueue(body)
            }
        }
    }

    private fun send(body: String): Boolean {
        val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Token $token")
            conn.outputStream.use { it.write(body.toByteArray()) }
            conn.responseCode in 200..299
        } finally {
            conn.disconnect()
        }
    }

    private fun enqueue(body: String) {
        val old = (sp.getString("queue", "") ?: "").split('\n').filter { it.isNotBlank() }
        val next = (old + body).takeLast(40)
        sp.edit().putString("queue", next.joinToString("\n")).apply()
    }

    private suspend fun flush() {
        val lines = (sp.getString("queue", "") ?: "").split('\n').filter { it.isNotBlank() }
        if (lines.isEmpty()) return
        val keep = ArrayList<String>()
        for (line in lines) {
            val ok = runCatching { send(line) }.getOrDefault(false)
            if (!ok) keep.add(line)
        }
        sp.edit().putString("queue", keep.joinToString("\n")).apply()
    }

    fun clearQueue() {
        if (!::sp.isInitialized) return
        sp.edit().remove("queue").apply()
    }
}
