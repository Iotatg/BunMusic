package app.bunmusic.data

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
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

    private var context: Context? = null
    private var stream: SimpleCache? = null
    private var database: StandaloneDatabaseProvider? = null

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
    }

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
        val ctx = context ?: error("Cache.init() not called")
        val upstream = DefaultDataSource.Factory(
            ctx,
            OkHttpDataSource.Factory(http).setUserAgent(UA),
        )
        val cache = streamCache() ?: return upstream
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
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
