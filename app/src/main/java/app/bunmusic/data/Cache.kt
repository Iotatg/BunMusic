package app.bunmusic.data

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Disk caches, in two flavours.
 *
 * **Stream cache** — anything played from the network lands here as it buffers, so
 * replaying a song, scrubbing back, or hearing it again tomorrow costs no data.
 * Eviction is least-recently-used against a size the listener picks (256 MB by
 * default, off, or unlimited). This is the cache that matters on a phone.
 *
 * **Image cache** — cover art, owned by Coil, trimmed the same way.
 *
 * Downloads are deliberately *not* a cache: they live in their own folder with no
 * evictor, because "saved" has to mean saved. The player reads them straight off
 * disk and never routes them through here.
 *
 * The cache is opened lazily: [SimpleCache] takes an exclusive lock on its folder,
 * so nothing touches it until a stream actually needs it.
 */
object Cache {

    private const val STREAM_DIR = "stream"
    private const val IMAGE_DIR = "art"

    /** Every stream key is "yt:<videoId>:<itag>" — stable across URL rotations. */
    const val KEY_PREFIX = "yt:"

    private var context: Context? = null
    private var stream: SimpleCache? = null
    private var database: StandaloneDatabaseProvider? = null
    private val prefetching = ConcurrentHashMap.newKeySet<String>()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

    fun init(ctx: Context) {
        context = ctx.applicationContext
        loadRegistry()
        refreshIndex()
    }

    // ------------------------------------------------------------ registry

    /**
     * What we know about each cached track: which itag it was, how many bytes it
     * should be, and when it landed. Without this the app could not tell a
     * complete file from a half-buffered one, and would have to go online just to
     * find out — which is exactly what caching is supposed to avoid.
     */
    data class CachedStream(val videoId: String, val itag: Int, val bytes: Long, val savedAt: Long)

    private val registry = HashMap<String, CachedStream>()
    private var registryPrefs: android.content.SharedPreferences? = null

    private fun loadRegistry() {
        val ctx = context ?: return
        val sp = ctx.getSharedPreferences("bun_streams", Context.MODE_PRIVATE)
        registryPrefs = sp
        registry.clear()
        sp.all.forEach { (key, value) ->
            if (key.startsWith("s_") && value is String) {
                val parts = value.split('|')
                if (parts.size >= 3) {
                    val id = key.removePrefix("s_")
                    val itag = parts[0].toIntOrNull() ?: return@forEach
                    val bytes = parts[1].toLongOrNull() ?: return@forEach
                    val at = parts[2].toLongOrNull() ?: return@forEach
                    registry[id] = CachedStream(id, itag, bytes, at)
                }
            }
        }
    }

    @Synchronized
    private fun record(videoId: String, itag: Int, bytes: Long) {
        val entry = CachedStream(videoId, itag, bytes, System.currentTimeMillis())
        registry[videoId] = entry
        registryPrefs?.edit()?.putString("s_$videoId", "$itag|$bytes|${entry.savedAt}")?.apply()
        completeIds.add(videoId)
    }

    fun cached(videoId: String): CachedStream? {
        val key = completeKey(videoId) ?: return null
        val cache = streamCache() ?: return registry[videoId]
        val itag = key.substringAfterLast(':').toIntOrNull() ?: 0
        val bytes = runCatching {
            ContentMetadata.getContentLength(cache.getContentMetadata(key))
        }.getOrDefault(0L)
        return CachedStream(videoId, itag, bytes, registry[videoId]?.savedAt ?: 0L)
    }

    /**
     * The key a fully cached track lives under, or null.
     *
     * The cache is asked directly — its own content metadata records how long the
     * stream was, so "complete" means every byte is present. A SharedPreferences
     * bookkeeping entry can be lost (the app is killed between the last write and
     * the record, the user clears app data), and then the app would refuse to play
     * offline something that is sitting right there on disk. Trust the disk.
     */
    fun completeKey(videoId: String): String? {
        if (videoId.isBlank()) return null
        val cache = streamCache() ?: return null
        val prefix = "$KEY_PREFIX$videoId:"
        val keys = runCatching { cache.keys.filter { it.startsWith(prefix) } }.getOrDefault(emptyList())
        for (key in keys) {
            val total = runCatching {
                ContentMetadata.getContentLength(cache.getContentMetadata(key))
            }.getOrDefault(0L)
            if (total > 0 && runCatching { cache.isCached(key, 0L, total) }.getOrDefault(false)) {
                return key
            }
        }
        return null
    }

    /** True only when the whole track is on disk — the difference between "starts" and "plays offline". */
    fun isComplete(videoId: String): Boolean = completeIds.contains(videoId) || completeKey(videoId) != null

    // -------------------------------------------------------------- fast index

    /**
     * The ids we already know are complete, so a list of two hundred rows can ask
     * "is this one playable on a plane?" without touching the disk two hundred times.
     */
    private val completeIds: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet())

    fun hasOffline(videoId: String): Boolean = completeIds.contains(videoId)

    fun offlineCount(): Int = completeIds.size

    /** Rescan the cache on IO. Cheap enough to call after a clear or a prefetch. */
    fun refreshIndex() {
        val cache = streamCache() ?: run { completeIds.clear(); return }
        io.launch {
            val found = HashSet<String>()
            runCatching {
                cache.keys.forEach { key ->
                    if (!key.startsWith(KEY_PREFIX)) return@forEach
                    val total = ContentMetadata.getContentLength(cache.getContentMetadata(key))
                    if (total > 0 && cache.isCached(key, 0L, total)) {
                        key.removePrefix(KEY_PREFIX).substringBeforeLast(':').let { found.add(it) }
                    }
                }
            }
            completeIds.clear()
            completeIds.addAll(found)
        }
    }

    /**
     * Pull a whole track into the cache in the background, so the next play — even
     * with the radio off — never touches the network.
     */
    fun prefetch(info: StreamInfo) {
        if (info.lengthBytes <= 0) return
        val cache = streamCache() ?: return
        if (isComplete(info.videoId)) return
        if (!prefetching.add(info.key)) return
        io.launch {
            runCatching {
                val spec = DataSpec.Builder()
                    .setUri(Uri.parse(info.url))
                    .setKey(info.key)
                    .setLength(info.lengthBytes)
                    .build()
                // CacheWriter needs a source that already knows how to write into the
                // cache; a bare SimpleCache cannot do it on its own.
                val sink = CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(upstreamFactory())
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                    .createDataSource()
                CacheWriter(sink, spec, null, null).cache()
            }.onSuccess {
                record(info.videoId, info.itag, info.lengthBytes)
            }
            prefetching.remove(info.key)
        }
    }

    fun prefetchingNow(): Int = prefetching.size

    fun imageDir(): File = File(context?.cacheDir ?: File("/tmp"), IMAGE_DIR)

    private fun streamDir(): File = File(context?.filesDir ?: File("/tmp"), STREAM_DIR)

    /** Null when the listener turned the stream cache off. */
    fun streamCache(): SimpleCache? {
        val ctx = context ?: return null
        stream?.let { return it }
        val limitMb = Prefs.settings.value.cacheLimitMb
        if (limitMb == 0) return null
        synchronized(this) {
            stream?.let { return it }
            val evictor = if (limitMb < 0) {
                NoOpCacheEvictor()
            } else {
                LeastRecentlyUsedCacheEvictor(limitMb.toLong() * 1024L * 1024L)
            }
            val db = database ?: StandaloneDatabaseProvider(ctx).also { database = it }
            val opened = runCatching {
                SimpleCache(streamDir(), evictor, db)
            }.getOrNull()
            stream = opened
            return opened
        }
    }

    /**
     * Opens the cache on first use instead of at startup: [SimpleCache] does disk
     * work while it loads its index, and the app has no reason to pay for that
     * before the first track is played.
     */
    fun deferredFactory(): DataSource.Factory = DataSource.Factory { dataSourceFactory().createDataSource() }

    /**
     * Network reads go through the stream cache. Local files never do — [Playback]
     * hands those to a plain source, so a downloaded track is not copied twice.
     */
    fun dataSourceFactory(): DataSource.Factory {
        val cache = streamCache() ?: return upstreamFactory()
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private fun upstreamFactory(): DataSource.Factory {
        val ctx = context ?: error("Cache.init() not called")
        return DefaultDataSource.Factory(
            ctx,
            OkHttpDataSource.Factory(http).setUserAgent(UA),
        )
    }

    // ------------------------------------------------------------------ stats

    fun streamBytes(): Long = sizeOf(streamDir())

    fun imageBytes(): Long = sizeOf(imageDir())

    fun totalBytes(): Long = streamBytes() + imageBytes()

    private fun sizeOf(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        dir.walkTopDown().forEach { if (it.isFile) total += it.length() }
        return total
    }

    // ---------------------------------------------------------------- actions

    /** Drop every buffered stream. Safe to call while something is playing. */
    fun clearStream() {
        synchronized(this) {
            runCatching { stream?.release() }
            stream = null
            streamDir().deleteRecursively()
            completeIds.clear()
        }
    }

    fun clearImages() {
        runCatching {
            val loader = coil3.SingletonImageLoader.get(context ?: return)
            loader.diskCache?.clear()
            loader.memoryCache?.clear()
        }
        imageDir().deleteRecursively()
    }

    /**
     * Reopen the stream cache so a new size limit takes effect. The folder and its
     * index survive, so nothing already cached is lost.
     */
    fun reopen() {
        synchronized(this) {
            runCatching { stream?.release() }
            stream = null
        }
    }

    /**
     * A new ceiling cannot be applied to a cache that is being read from — the
     * evictor is fixed when [SimpleCache] is opened, and releasing it mid-track
     * would break playback. So: apply now when idle, otherwise next launch.
     */
    fun onLimitChanged(playbackActive: Boolean) {
        if (playbackActive) return
        if (Prefs.settings.value.cacheLimitMb == 0) clearStream() else { reopen(); trimStream() }
    }

    fun clearAll() {
        clearStream()
        clearImages()
    }

    /**
     * Shrink the stream cache to its limit right now instead of waiting for the
     * next write. Used when the listener lowers the limit.
     */
    fun trimStream() {
        val limitMb = Prefs.settings.value.cacheLimitMb
        if (limitMb <= 0) {
            if (limitMb == 0) clearStream()
            return
        }
        val limit = limitMb.toLong() * 1024L * 1024L
        val dir = streamDir()
        if (!dir.exists()) return
        val files = dir.walkTopDown().filter { it.isFile }.sortedBy { it.lastModified() }.toList()
        var total = files.sumOf { it.length() }
        if (total <= limit) return
        for (file in files) {
            if (total <= limit) break
            val size = file.length()
            if (runCatching { file.delete() }.getOrDefault(false)) total -= size
        }
    }
}
