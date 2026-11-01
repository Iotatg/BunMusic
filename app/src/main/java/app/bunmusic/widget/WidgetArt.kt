package app.bunmusic.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import androidx.compose.ui.graphics.toArgb
import app.bunmusic.core.BunPalette
import app.bunmusic.data.hq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Cover art and progress bars for the launcher.
 *
 * A widget cannot use Coil: the launcher inflates it in its own process, so the
 * bitmap has to be handed over already decoded. That makes this the one place in
 * the app that talks to the network by hand — and the one place that must not
 * block the main thread doing it.
 *
 * Three layers, cheapest first: an in-memory LRU, a disk cache under
 * `cacheDir/widget_art`, then the network. The disk layer is what makes a widget
 * survive a reboot: the launcher asks for a redraw before the phone has finished
 * connecting to Wi-Fi, and a placeholder there looks broken.
 */
object WidgetArt {

    private const val DIR = "widget_art"
    private const val MAX_ENTRIES = 24

    private val mem = LruCache<String, Bitmap>(MAX_ENTRIES)

    /** Rounded cover art, or null when it is not on disk and not in memory. */
    fun cached(url: String?, sizePx: Int, radiusPx: Int): Bitmap? {
        val key = key(url, sizePx, radiusPx) ?: return null
        mem.get(key)?.let { return it }
        return null
    }

    /**
     * Load art from disk if possible. Safe to call from a `RemoteViewsFactory`,
     * which runs on a binder thread and must not touch the network.
     */
    fun cachedOnDisk(context: Context, url: String?, sizePx: Int, radiusPx: Int): Bitmap? {
        val key = key(url, sizePx, radiusPx) ?: return null
        mem.get(key)?.let { return it }
        val file = fileFor(context, key)
        if (!file.exists()) return null
        val bitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull() ?: return null
        mem.put(key, bitmap)
        return bitmap
    }

    /** Fetch, round and cache. Always off the main thread. */
    suspend fun load(context: Context, url: String?, sizePx: Int, radiusPx: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            val key = key(url, sizePx, radiusPx) ?: return@withContext null
            mem.get(key)?.let { return@withContext it }
            val file = fileFor(context, key)
            if (file.exists()) {
                val cached = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
                if (cached != null) {
                    mem.put(key, cached)
                    return@withContext cached
                }
            }
            val raw = fetch(hq(url!!), sizePx) ?: return@withContext null
            val rounded = round(raw, radiusPx)
            mem.put(key, rounded)
            runCatching {
                file.parentFile?.mkdirs()
                file.outputStream().use { rounded.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            rounded
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
        runCatching { File(context.cacheDir, DIR).deleteRecursively() }
    }

    fun bytes(context: Context): Long {
        val dir = File(context.cacheDir, DIR)
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    // --------------------------------------------------------------- internals

    private fun key(url: String?, sizePx: Int, radiusPx: Int): String? {
        if (url.isNullOrBlank()) return null
        return "${md5(url)}_${sizePx}_$radiusPx"
    }

    private fun fileFor(context: Context, key: String): File =
        File(File(context.cacheDir, DIR), "$key.png")

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

    private fun fetch(url: String, sizePx: Int): Bitmap? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 12_000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
        val decoded = conn.inputStream.use { BitmapFactory.decodeStream(it) } ?: return null
        // Covers arrive around 500 px. Anything larger is scaled down before it
        // is rounded, so the LRU holds 24 widgets' worth in a few megabytes.
        if (decoded.width > sizePx) {
            val scaled = Bitmap.createScaledBitmap(decoded, sizePx, sizePx, true)
            if (scaled !== decoded) decoded.recycle()
            scaled
        } else {
            decoded
        }
    }.getOrNull()

    private fun md5(s: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
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
