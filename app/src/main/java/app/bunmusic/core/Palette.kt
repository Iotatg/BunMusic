package app.bunmusic.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import app.bunmusic.data.Track
import app.bunmusic.data.hq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Colour taken from the cover, not from the id.
 *
 * [Ambient] hashes a track's id into one of eight hues. That is stable and it
 * never flickers, and it is also why two songs with the same cover can come out
 * green and orange. This reads the actual artwork instead: a median-cut over a
 * 64 px decode, scored so the winner is vivid enough to be a tint and light
 * enough to sit under white text.
 *
 * Everything below [extract] is pure arithmetic on an IntArray, so it is tested
 * on the JVM. [ArtColors] is the part that touches the network and a bitmap.
 */
object Palette {

    /**
     * A cover's two colours.
     *
     * [primary] is the one worth tinting with. [secondary] is far enough away in
     * hue to be visible as a second stop in a gradient — picking the runner-up
     * by population alone tends to return a slightly different shade of the same
     * brown.
     */
    data class Swatch(
        val primary: Color,
        val secondary: Color,
    ) {
        /** True when the cover is dark, so text and glyphs should go white. */
        val dark: Boolean get() = luminance(primary) < 0.55f
    }

    /** How many buckets median-cut ends up with. Five is enough to have a choice. */
    private const val BOXES = 5

    /**
     * Extract a swatch from ARGB pixels.
     *
     * Returns null when there is nothing to look at — an all-transparent image,
     * or an empty array. Callers fall back to [Ambient] in that case.
     */
    fun extract(pixels: IntArray, boxes: Int = BOXES): Swatch? {
        val opaque = IntArray(pixels.size)
        var n = 0
        for (p in pixels) {
            // Anything under ~78 % alpha is a shadow or a rounded corner, and
            // averaging those in drags every swatch toward the background.
            if (((p ushr 24) and 0xFF) >= 200) opaque[n++] = p
        }
        if (n == 0) return null
        val source = opaque.copyOf(n)

        var cut = listOf(Box(source))
        while (cut.size < boxes) {
            val next = cut.filter { it.splittable }.maxByOrNull { it.spread() * it.size } ?: break
            val halves = next.split() ?: break
            cut = cut - next + listOf(halves.first, halves.second)
        }

        val swatches = cut.map { it.average() }
        if (swatches.isEmpty()) return null
        val total = swatches.sumOf { it.size }.coerceAtLeast(1)

        fun score(c: Candidate): Float {
            val population = c.size.toFloat() / total
            val sat = saturation(c.r, c.g, c.b)
            val lum = luminance(c.r, c.g, c.b)
            // A tint has to survive being laid under 100 % white text, so a
            // near-white or near-black cover loses on purpose rather than
            // producing an invisible gradient.
            val usable = 1f - abs(lum - 0.5f) * 0.9f
            return population * (0.30f + sat) * max(0.25f, usable)
        }

        val ranked = swatches.sortedByDescending { score(it) }
        val primary = ranked.first()
        val secondary = ranked.drop(1).firstOrNull {
            hueDistance(it, primary) > 25f
        } ?: ranked.getOrNull(1) ?: primary

        return Swatch(
            primary = Color(primary.r, primary.g, primary.b),
            secondary = Color(secondary.r, secondary.g, secondary.b),
        )
    }

    // ------------------------------------------------------------- arithmetic

    private data class Candidate(val r: Int, val g: Int, val b: Int, val size: Int)

    /** One median-cut box: the pixels still together, plus their bounds. */
    private class Box(private val pixels: IntArray) {
        val size: Int get() = pixels.size

        private val rMin: Int; private val rMax: Int
        private val gMin: Int; private val gMax: Int
        private val bMin: Int; private val bMax: Int

        init {
            var rl = 255; var rh = 0
            var gl = 255; var gh = 0
            var bl = 255; var bh = 0
            for (p in pixels) {
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                if (r < rl) rl = r; if (r > rh) rh = r
                if (g < gl) gl = g; if (g > gh) gh = g
                if (b < bl) bl = b; if (b > bh) bh = b
            }
            rMin = rl; rMax = rh
            gMin = gl; gMax = gh
            bMin = bl; bMax = bh
        }

        val splittable: Boolean get() = pixels.size > 1 && spread() > 6

        /** The longest side of the colour box — where a cut does the most good. */
        private fun axis(): Int {
            val r = rMax - rMin
            val g = gMax - gMin
            val b = bMax - bMin
            return if (r >= g && r >= b) 0 else if (g >= b) 1 else 2
        }

        fun spread(): Int = max(max(rMax - rMin, gMax - gMin), bMax - bMin)

        fun split(): Pair<Box, Box>? {
            if (!splittable) return null
            val a = axis()
            val sorted = pixels.sortedBy {
                when (a) {
                    0 -> (it shr 16) and 0xFF
                    1 -> (it shr 8) and 0xFF
                    else -> it and 0xFF
                }
            }
            val mid = sorted.size / 2
            return Box(sorted.subList(0, mid).toIntArray()) to Box(sorted.subList(mid, sorted.size).toIntArray())
        }

        fun average(): Candidate {
            var r = 0L; var g = 0L; var b = 0L
            for (p in pixels) {
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
            }
            val n = pixels.size.coerceAtLeast(1)
            return Candidate((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), n)
        }
    }

    /** 0..1, HSV saturation. Greys score zero and lose. */
    fun saturation(r: Int, g: Int, b: Int): Float {
        val hi = max(r, max(g, b)).toFloat()
        val lo = min(r, min(g, b)).toFloat()
        return if (hi <= 0f) 0f else (hi - lo) / hi
    }

    /** 0..1, Rec. 709 luma. */
    fun luminance(color: Color): Float = luminance(
        (color.red * 255f).toInt(),
        (color.green * 255f).toInt(),
        (color.blue * 255f).toInt(),
    )

    fun luminance(r: Int, g: Int, b: Int): Float =
        (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f

    private fun hue(r: Int, g: Int, b: Int): Float {
        val rf = r / 255f; val gf = g / 255f; val bf = b / 255f
        val hi = max(rf, max(gf, bf))
        val lo = min(rf, min(gf, bf))
        val d = hi - lo
        if (d < 0.0001f) return 0f
        val h = when (hi) {
            rf -> ((gf - bf) / d) % 6f
            gf -> (bf - rf) / d + 2f
            else -> (rf - gf) / d + 4f
        }
        return (h * 60f + 360f) % 360f
    }

    /** Shortest distance around the colour wheel, in degrees. */
    private fun hueDistance(a: Candidate, b: Candidate): Float {
        val d = abs(hue(a.r, a.g, a.b) - hue(b.r, b.g, b.b))
        return min(d, 360f - d)
    }
}

/**
 * Fetches a cover and remembers its swatch.
 *
 * Deliberately not routed through Coil: this needs the pixels, not a drawable,
 * and Coil keeps its cache keyed in a way that cannot be read back as a bitmap.
 * One extra 64 px decode per track, cached for the life of the process, is a
 * cheaper trade than a second image pipeline.
 */
object ArtColors {

    private val cache = LruCache<String, Palette.Swatch>(64)

    /** URLs that failed once are not tried again this session. */
    private val broken = HashSet<String>()

    /** Whatever is already known, without suspending — for the first frame. */
    fun peek(url: String?): Palette.Swatch? = url?.let { cache.get(it) }

    suspend fun forTrack(context: Context, track: Track?): Palette.Swatch? =
        forUrl(context, track?.artUrl)

    /** Same thing for a bare cover URL — collection headers have no Track. */
    suspend fun forUrl(context: Context, artUrl: String?): Palette.Swatch? {
        val url = artUrl?.takeIf { it.isNotBlank() }?.let { hq(it) } ?: return null
        cache.get(url)?.let { return it }
        if (broken.contains(url)) return null
        return withContext(Dispatchers.IO) {
            val pixels = runCatching { pixelsOf(url) }.getOrNull()
            val swatch = pixels?.let { Palette.extract(it) }
            if (swatch == null) broken.add(url) else cache.put(url, swatch)
            swatch
        }
    }

    private fun pixelsOf(url: String): IntArray {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 12_000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
        }
        val bytes = conn.inputStream.use { it.readBytes() }
        conn.disconnect()
        if (bytes.isEmpty()) return IntArray(0)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val smallest = min(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        // 64 px on the short side is 4096 samples — plenty for five buckets, and
        // small enough that the decode never shows up in a frame.
        val sample = max(1, smallest / 64)

        val bitmap = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: return IntArray(0)

        val w = bitmap.width
        val h = bitmap.height
        val out = IntArray(w * h)
        bitmap.getPixels(out, 0, w, 0, 0, w, h)
        bitmap.recycle()
        return out
    }
}
