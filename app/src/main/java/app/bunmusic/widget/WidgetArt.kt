package app.bunmusic.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import androidx.compose.ui.graphics.toArgb
import app.bunmusic.core.BunPalette
import app.bunmusic.core.CrashGuard
import app.bunmusic.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Cover art and progress bars for the launcher, the notification and the
 * lock screen.
 *
 * A widget cannot use Coil: the launcher inflates it in its own process, so the
 * bitmap has to be handed over already decoded. That makes this the one place
 * in the app that talks to the network by hand — and the one place that must not
 * block the main thread doing it.
 *
 * Three layers, cheapest first: an in-memory LRU, a disk cache under
 * `cacheDir/widget_art`, then the network. The disk layer is what makes a widget
 * survive a reboot: the launcher asks for a redraw before the phone has
 * finished connecting to Wi-Fi, and a placeholder there looks broken.
 *
 * The disk key is **one canonical square per URL**, not one file per
 * `(url, size, radius)` as it used to be. The main widget loaded at 180 px, the
 * compact one at 320 px and the queue rows asked for 96 px, each with a
 * different corner radius — so every consumer wrote a file no other consumer
 * ever looked for. Sizes and radii are now memory-only derivations of the same
 * file. See [ArtKeys], which is pure and tested.
 */
object WidgetArt {

    private const val DIR = "widget_art"
    /** One canonical square per cover; every consumer crops down from this. */
    private const val DISK_SIZE = 320
    /** 8 MB of decoded bitmaps, measured in bytes — not "8 kilobytes". */
    private const val MAX_MEM_BYTES = 8 * 1024 * 1024
    private const val MAX_DISK_ENTRIES = 64
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Mobile Safari/537.36"

    private val mem = object : LruCache<String, Bitmap>(MAX_MEM_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val io = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CrashGuard.handler("widget-art"),
    )

    /** URLs already warmed for the queue, so a dead cover is retried only once. */
    private val warmed = ConcurrentHashMap.newKeySet<String>()

    /** Memory only. For callers that must not touch the disk at all. */
    fun cached(url: String?, sizePx: Int, radiusPx: Int): Bitmap? =
        mem.get(ArtKeys.memory(url, sizePx, radiusPx) ?: return null)

    /**
     * Memory, then disk. Safe on a binder thread: it never opens the network.
     *
     * This is the one that fixed "cached thumbnails don't show". The first draw
     * after a reboot (and every queue row) reads the same canonical file, crops
     * it to the requested size and radius, and caches that derivation in memory.
     */
    fun cachedOnDisk(context: Context, url: String?, sizePx: Int, radiusPx: Int): Bitmap? {
        val key = ArtKeys.memory(url, sizePx, radiusPx) ?: return null
        mem.get(key)?.let { return it }
        val canonical = readCanonical(context, url) ?: return null
        val derived = runCatching { derive(canonical, sizePx, radiusPx) }.getOrNull() ?: return null
        mem.put(key, derived)
        return derived
    }

    /** Fetch, round and cache. Always off the main thread. */
    suspend fun load(context: Context, url: String?, sizePx: Int, radiusPx: Int): Bitmap? =
        withContext(Dispatchers.IO) { loadBlocking(context, url, sizePx, radiusPx) }

    /** The same work without a coroutine, for the notification's art thread. */
    fun loadBlocking(context: Context, url: String?, sizePx: Int, radiusPx: Int): Bitmap? {
        val key = ArtKeys.memory(url, sizePx, radiusPx) ?: return null
        mem.get(key)?.let { return it }
        val existing = readCanonical(context, url)
        if (existing != null) {
            val derived = runCatching { derive(existing, sizePx, radiusPx) }.getOrNull()
            if (derived != null) {
                mem.put(key, derived)
                return derived
            }
        }
        val raw = fetch(ArtKeys.normalized(url) ?: return null) ?: return null
        writeCanonical(context, url, raw)
        val derived = runCatching { derive(raw, sizePx, radiusPx) }.getOrNull() ?: return null
        mem.put(key, derived)
        return derived
    }

    /**
     * Warm the covers of the rows a queue widget is about to draw.
     *
     * The queue factory is disk-only on purpose — it runs on a binder thread
     * with a deadline and must not fetch ten images. Without this, rows for
     * tracks that have never been played had no thumbnail at all, because
     * nothing ever wrote their cover to disk. Warming happens on IO and asks the
     * launcher for one redraw when a cover lands.
     */
    fun warmQueue(context: Context, queue: List<Track>, index: Int, onLoaded: () -> Unit) {
        if (queue.isEmpty()) return
        if (warmed.size > 512) warmed.clear()
        for (track in queue.drop((index + 1).coerceAtLeast(0)).take(8)) {
            val u = track.artUrl?.takeIf { it.isNotBlank() } ?: continue
            if (!warmed.add(u)) continue
            io.launch {
                val loaded = runCatching { loadBlocking(context, u, DISK_SIZE, 0) }.getOrNull()
                if (loaded != null) withContext(Dispatchers.Main) { runCatching { onLoaded() } }
            }
        }
    }

    /** A neutral tile in the bun's own ink, used before art arrives. */
    fun placeholder(palette: BunPalette, sizePx: Int, radiusPx: Int): Bitmap {
        val key = "ph|${palette.text.value}|$sizePx|$radiusPx"
        mem.get(key)?.let { return it }
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = palette.text.copy(alpha = 0.10f).toArgb()
        val r = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
        canvas.drawRoundRect(r, radiusPx.toFloat(), radiusPx.toFloat(), paint)
        // A small disc in the middle so the tile reads as "art goes here" rather
        // than as a failed image.
        paint.color = palette.text.copy(alpha = 0.22f).toArgb()
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx * 0.16f, paint)
        mem.put(key, out)
        return out
    }

    /**
     * The progress bar as a bitmap.
     *
     * RemoteViews cannot tint a ProgressBar below API 31 (`setProgressTintList`
     * takes a ColorStateList, which `setInt` cannot pass), and a widget that is
     * lime on Midnight and blue on Cloud has to be tinted. Drawing 480 x 12 px is
     * cheaper than shipping fifteen tinted drawables, and it scales exactly.
     */
    fun progress(palette: BunPalette, fraction: Float, likedAccent: Boolean = false): Bitmap {
        val pct = (fraction.coerceIn(0f, 1f) * 200).toInt()
        val key = "pb|${palette.accent.value}|${palette.text.value}|$pct"
        mem.get(key)?.let { return it }
        val w = 480
        val h = 12
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = palette.text.copy(alpha = 0.16f).toArgb()
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), h / 2f, h / 2f, paint)
        if (pct > 0) {
            paint.color = (if (likedAccent) palette.rose else palette.accent).toArgb()
            val fill = (w * pct / 200f).coerceAtLeast(h.toFloat())
            canvas.drawRoundRect(RectF(0f, 0f, fill, h.toFloat()), h / 2f, h / 2f, paint)
        }
        mem.put(key, out)
        return out
    }

    fun clear(context: Context) {
        mem.evictAll()
        warmed.clear()
        runCatching { File(context.cacheDir, DIR).deleteRecursively() }
    }

    fun bytes(context: Context): Long {
        val dir = File(context.cacheDir, DIR)
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    // --------------------------------------------------------------- internals

    private fun readCanonical(context: Context, url: String?): Bitmap? {
        val file = canonicalFile(context, url) ?: return null
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    private fun writeCanonical(context: Context, url: String?, raw: Bitmap) {
        val file = canonicalFile(context, url) ?: return
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { raw.compress(Bitmap.CompressFormat.PNG, 100, it) }
            trim(context)
        }
    }

    private fun canonicalFile(context: Context, url: String?): File? =
        ArtKeys.diskFile(url)?.let { File(File(context.cacheDir, DIR), it) }

    /** Delete legacy `<md5>_<size>_<radius>.png` files and cap the directory. */
    private fun trim(context: Context) {
        val dir = File(context.cacheDir, DIR)
        val all = dir.listFiles()?.filter { it.isFile } ?: return
        all.filterNot { ArtKeys.isCanonicalName(it.name) }.forEach { runCatching { it.delete() } }
        val kept = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
        var extra = kept.size - MAX_DISK_ENTRIES
        if (extra <= 0) return
        for (file in kept) {
            if (extra-- <= 0) break
            runCatching { file.delete() }
        }
    }

    /** Centre-crop to a square of `sizePx`, then round the corners if asked. */
    private fun derive(src: Bitmap, sizePx: Int, radiusPx: Int): Bitmap {
        val square = square(src, sizePx)
        return if (radiusPx <= 0) square else round(square, radiusPx)
    }

    private fun square(src: Bitmap, sizePx: Int): Bitmap {
        if (src.width == sizePx && src.height == sizePx) return src
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val side = minOf(src.width, src.height)
        val from = Rect(
            (src.width - side) / 2, (src.height - side) / 2,
            (src.width + side) / 2, (src.height + side) / 2,
        )
        canvas.drawBitmap(src, from, Rect(0, 0, sizePx, sizePx), paint)
        return out
    }

    private fun round(source: Bitmap, radiusPx: Int): Bitmap {
        val size = source.width
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(
            RectF(0f, 0f, size.toFloat(), size.toFloat()),
            radiusPx.toFloat(),
            radiusPx.toFloat(),
            paint,
        )
        return out
    }

    private fun fetch(url: String): Bitmap? {
        val conn = runCatching { URL(url).openConnection() as HttpURLConnection }.getOrNull() ?: return null
        return try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 12_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", UA)
            val decoded = conn.inputStream.use { BitmapFactory.decodeStream(it) } ?: return null
            // Covers arrive around 500-1080 px. Store one canonical square at
            // DISK_SIZE so a 96 px queue row and a 320 px widget share a file.
            val canonical = square(decoded, DISK_SIZE)
            if (canonical !== decoded) decoded.recycle()
            canonical
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { conn.disconnect() }
        }
    }
}

/** Formatting the widget does on every tick. Pure, so it is unit-tested. */
object WidgetText {

    fun clock(ms: Long): String {
        val total = (ms.coerceAtLeast(0L) / 1000L).toInt()
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun fraction(positionMs: Long, durationMs: Long): Float =
        if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}
