package app.bunmusic.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Offline downloads. A track is resolved to a direct audio URL, streamed to
 * the app's private files directory, and registered here so [Playback] can
 * prefer the local copy over the network.
 */
object Downloads {

    data class Entry(
        val trackId: String,
        val title: String,
        val artist: String,
        val artUrl: String?,
        val path: String,
        val bytes: Long,
        val atMs: Long,
    )

    private const val F = '\u0001'
    private const val R = '\n'

    private lateinit var dir: File
    private lateinit var sp: SharedPreferences

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    fun init(context: Context) {
        if (::dir.isInitialized) return
        dir = File(context.filesDir, "downloads").apply { mkdirs() }
        sp = context.getSharedPreferences("bunmusic_downloads", Context.MODE_PRIVATE)
        _entries.value = read()
    }

    private fun read(): List<Entry> {
        val out = ArrayList<Entry>()
        for (line in (sp.getString("entries", null) ?: "").split(R)) {
            val p = line.split(F)
            if (p.size < 7) continue
            val path = p[5]
            if (!File(path).exists()) continue
            out.add(
                Entry(
                    trackId = p[0], title = p[1], artist = p[2],
                    artUrl = p[3].ifBlank { null }, path = path,
                    bytes = p[4].toLongOrNull() ?: 0L, atMs = p[6].toLongOrNull() ?: 0L,
                )
            )
        }
        return out.sortedByDescending { it.atMs }
    }

    private fun write(list: List<Entry>) {
        sp.edit().putString(
            "entries",
            list.joinToString(R.toString()) {
                listOf(it.trackId, it.title, it.artist, it.artUrl ?: "", it.bytes.toString(), it.path, it.atMs.toString())
                    .joinToString(F.toString())
            },
        ).apply()
        _entries.value = list
    }

    fun isDownloaded(trackId: String): Boolean = _entries.value.any { it.trackId == trackId }

    fun fileFor(trackId: String): File? =
        _entries.value.firstOrNull { it.trackId == trackId }?.let { File(it.path) }?.takeIf { it.exists() }

    fun totalBytes(): Long = _entries.value.sumOf { it.bytes }

    suspend fun download(track: Track): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (isDownloaded(track.id)) return@runCatching
            val url = if (track.isLocal) {
                error("Already offline")
            } else {
                YtStream.audioUrl(track.videoId)
            }
            val safe = track.id.replace(Regex("[^A-Za-z0-9_-]"), "_").take(60)
            val target = File(dir, "$safe.m4a")
            val temp = File(dir, "$safe.part")

            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 20_000
            conn.readTimeout = 40_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
            conn.connect()
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    var last = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        read += n
                        if (total > 0 && read - last > 512 * 1024) {
                            last = read
                            _progress.value = _progress.value + (track.id to (read.toFloat() / total))
                        }
                    }
                }
            }
            conn.disconnect()
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
            _progress.value = _progress.value - track.id
            write(
                _entries.value + Entry(
                    trackId = track.id, title = track.title, artist = track.artist,
                    artUrl = track.artUrl, path = target.absolutePath,
                    bytes = target.length(), atMs = System.currentTimeMillis(),
                )
            )
            Store.remember(track)
        }.onFailure {
            _progress.value = _progress.value - track.id
        }
    }

    fun remove(trackId: String) {
        val entry = _entries.value.firstOrNull { it.trackId == trackId }
        if (entry != null) runCatching { File(entry.path).delete() }
        write(_entries.value.filterNot { it.trackId == trackId })
    }

    fun clearAll() {
        _entries.value.forEach { runCatching { File(it.path).delete() } }
        write(emptyList())
    }
}
