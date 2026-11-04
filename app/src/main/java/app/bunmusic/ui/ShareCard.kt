package app.bunmusic.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import app.bunmusic.data.LibraryStats
import app.bunmusic.data.Memory
import app.bunmusic.data.Store
import app.bunmusic.data.Wrapped
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A listening receipt you can send to someone.
 *
 * Drawn with android.graphics rather than captured from Compose: a screenshot of
 * a composable depends on what is on screen (scroll position, theme, whether the
 * keyboard is up), and the receipt has to look the same every time. This is a
 * fixed 1080 x 1350 canvas with a fixed layout, so the output is deterministic.
 *
 * The file goes to `cacheDir/shared` and out through a [FileProvider] — a plain
 * `file://` URI throws FileUriExposedException on every API since 24.
 */
object ShareCard {

    private const val W = 1080
    private const val H = 1350

    private val ink = AColor.rgb(244, 241, 233)
    private val dim = AColor.rgb(150, 145, 160)
    private val lime = AColor.rgb(216, 246, 92)
    private val card = AColor.rgb(22, 19, 28)

    fun render(stats: LibraryStats = Store.stats()): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(AColor.rgb(10, 9, 13))

        // A soft glow at the top so the card does not read as a black rectangle.
        val glow = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, H * 0.55f,
                AColor.rgb(58, 44, 92), AColor.rgb(10, 9, 13),
                Shader.TileMode.CLAMP,
            )
        }
        c.drawRect(0f, 0f, W.toFloat(), H * 0.55f, glow)

        val panel = Paint().apply { color = card }
        c.drawRoundRect(RectF(64f, 120f, W - 64f, H - 120f), 56f, 56f, panel)

        var y = 220f
        fun text(s: String, size: Float, color: Int, bold: Boolean = false, x: Float = 128f): Float {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                textSize = size
                typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            }
            c.drawText(s, x, y, p)
            return p.measureText(s)
        }

        text("BUNMUSIC", 34f, lime, bold = true)
        y += 58
        text("Listening receipt", 62f, ink, bold = true)
        y += 44
        val stamp = SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date())
        text(stamp, 30f, dim)
        y += 96

        // Two headline numbers, side by side.
        val half = (W - 256f) / 2f
        text(fmtDuration(stats.totalMs), 76f, ink, bold = true)
        text("${stats.plays}", 76f, ink, bold = true, x = 128f + half)
        y += 34
        text("listened", 28f, dim)
        text("plays", 28f, dim, x = 128f + half)
        y += 92

        val rule = Paint().apply { color = AColor.rgb(48, 42, 62); strokeWidth = 2f }
        c.drawLine(128f, y, W - 128f, y, rule)
        y += 62

        text("ON REPEAT", 26f, lime, bold = true)
        y += 54
        val tracks = stats.topTracks.take(3)
        if (tracks.isEmpty()) {
            text("Nothing played yet — this one is a blank page.", 32f, dim)
            y += 48
        } else {
            tracks.forEachIndexed { i, (title, count) ->
                text("${i + 1}", 30f, dim)
                text(trim(title, 34), 34f, ink, x = 176f)
                text("${count}×", 28f, dim, x = W - 128f - 40f)
                y += 54
            }
        }

        y += 40
        text("TOP ARTISTS", 26f, lime, bold = true)
        y += 54
        val artists = stats.topArtists.take(3)
        if (artists.isEmpty()) {
            text("—", 32f, dim)
            y += 48
        } else {
            artists.forEach { (name, count) ->
                text(trim(name, 30), 32f, ink)
                text("$count", 28f, dim, x = W - 128f - 24f)
                y += 50
            }
        }

        y = H - 210f
        c.drawLine(128f, y, W - 128f, y, rule)
        y += 56
        text("${stats.days} days of listening, counted on this device.", 27f, dim)
        y += 44
        text("BunMusic · a quiet place for loud music", 27f, lime)

        return bmp
    }

    private fun trim(s: String, max: Int): String =
        if (s.length <= max) s else s.take(max - 1).trimEnd() + "…"

    /**
     * The year in review as one image.
     *
     * Same fixed canvas as the receipt and the same reason: a screenshot of the
     * reel would capture whatever card happened to be on screen. Everything here
     * comes from the same [Wrapped.Summary] the screen renders, so the numbers in
     * the picture are the numbers the user just read.
     */
    fun renderWrapped(s: Wrapped.Summary): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(AColor.rgb(10, 9, 13))

        val glow = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, H * 0.6f,
                AColor.rgb(34, 58, 52), AColor.rgb(10, 9, 13),
                Shader.TileMode.CLAMP,
            )
        }
        c.drawRect(0f, 0f, W.toFloat(), H * 0.6f, glow)

        var y = 190f
        fun text(s: String, size: Float, color: Int, bold: Boolean = false, x: Float = 96f): Float {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                textSize = size
                typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            }
            c.drawText(s, x, y, p)
            return p.measureText(s)
        }

        text("BUNMUSIC", 30f, lime, bold = true)
        y += 74
        text("${s.year}", 132f, ink, bold = true)
        y += 46
        text("in review", 40f, dim)
        y += 96

        val rule = Paint().apply { color = AColor.rgb(46, 42, 58); strokeWidth = 2f }
        c.drawLine(96f, y, W - 96f, y, rule)
        y += 76

        // Two headline numbers, then the three names that carry the year.
        text(Wrapped.fmtMinutes(s.minutes), 66f, ink, bold = true)
        text("${s.plays}", 66f, ink, bold = true, x = W / 2f + 10f)
        y += 32
        text("listened", 26f, dim)
        text("plays", 26f, dim, x = W / 2f + 10f)
        y += 88

        s.topArtist?.let { (name, n) ->
            text("TOP ARTIST", 22f, lime, bold = true)
            y += 46
            text(trim(name, 26), 40f, ink, bold = true)
            text("$n plays", 24f, dim, x = W - 96f - 90f)
            y += 66
        }

        s.topTrack?.let { (track, n) ->
            text("ON REPEAT", 22f, lime, bold = true)
            y += 46
            text(trim(track.title, 24), 40f, ink, bold = true)
            y += 34
            text(trim(track.artist, 34) + " · $n plays", 24f, dim)
            y += 62
        }

        s.topAlbum?.let { (album, n) ->
            text("ALBUM", 22f, lime, bold = true)
            y += 44
            text(trim(album, 30), 34f, ink, bold = true)
            text("$n", 24f, dim, x = W - 96f - 40f)
            y += 56
        }

        y = H - 250f
        c.drawLine(96f, y, W - 96f, y, rule)
        y += 54
        val tail = buildString {
            append("${s.tracks} tracks · ${s.artists} artists")
            if (s.streak >= 3) append(" · ${s.streak}-day streak")
        }
        text(tail, 26f, dim)
        y += 46
        text("BunMusic · a quiet place for loud music", 26f, lime)

        return bmp
    }

    /** Writes the Wrapped card and opens the share sheet. */
    fun shareWrapped(context: Context, summary: Wrapped.Summary) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "bunmusic-${summary.year}-wrapped.png")
        runCatching {
            file.outputStream().use { out -> renderWrapped(summary).compress(Bitmap.CompressFormat.PNG, 100, out) }
        }.onFailure { return }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "My ${summary.year} in music — BunMusic")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share your year"))
    }

    /** Writes the card and opens the system share sheet. */
    fun share(context: Context) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "bunmusic-receipt.png")
        runCatching {
            file.outputStream().use { out -> render().compress(Bitmap.CompressFormat.PNG, 100, out) }
        }.onFailure { return }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "My listening receipt — BunMusic")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share receipt"))
    }

    /**
     * The listening DNA as a card.
     *
     * Same 1080 × 1350 canvas and the same rule as the receipt: drawn with
     * android.graphics so the file is identical every time it is rendered, rather
     * than a screenshot of whatever happened to be on screen. The pentagon comes
     * from [Memory.radar], so the shape in the shared image and the shape on the
     * screen are the same arithmetic.
     */
    fun renderDna(dna: Memory.Dna, dateLabel: String): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(AColor.rgb(10, 5, 24))

        val glow = Paint().apply {
            shader = LinearGradient(
                0f, 0f, W.toFloat(), H.toFloat(),
                AColor.rgb(76, 29, 149), AColor.rgb(10, 5, 24),
                Shader.TileMode.CLAMP,
            )
        }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), glow)

        var y = 150f
        fun text(s: String, size: Float, color: Int, bold: Boolean = false, x: Float = 96f): Float {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                textSize = size
                typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            }
            c.drawText(s, x, y, p)
            return p.measureText(s)
        }

        text("BUNMUSIC", 32f, lime, bold = true)
        y += 66
        text("Listening DNA", 68f, ink, bold = true)
        y += 46
        text("$dateLabel · ${dna.plays} plays over ${dna.days} days", 26f, dim)

        // ------------------------------------------------------------- radar
        val cx = W / 2f
        val cy = 760f
        val r = 300f
        val values = dna.axes.map { it.value }

        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = AColor.argb(40, 255, 255, 255)
        }
        for (ring in 1..3) {
            val pts = Memory.radar(List(values.size) { 100 }, r * ring / 3f)
            val path = android.graphics.Path()
            pts.forEachIndexed { i, p ->
                val x = cx + p.first
                val yy = cy + p.second
                if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
            }
            path.close()
            c.drawPath(path, grid)
        }

        val spokes = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 2f
            color = AColor.argb(34, 255, 255, 255)
        }
        Memory.radar(List(values.size) { 100 }, r).forEach { p ->
            c.drawLine(cx, cy, cx + p.first, cy + p.second, spokes)
        }

        val pts = Memory.radar(values, r)
        if (pts.isNotEmpty()) {
            val shape = android.graphics.Path()
            pts.forEachIndexed { i, p ->
                if (i == 0) shape.moveTo(cx + p.first, cy + p.second)
                else shape.lineTo(cx + p.first, cy + p.second)
            }
            shape.close()

            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = LinearGradient(
                    cx - r, cy - r, cx + r, cy + r,
                    AColor.argb(150, 216, 246, 92), AColor.argb(70, 176, 107, 255),
                    Shader.TileMode.CLAMP,
                )
            }
            c.drawPath(shape, fill)
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 5f
                color = lime
            }
            c.drawPath(shape, stroke)
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lime }
            pts.forEach { p -> c.drawCircle(cx + p.first, cy + p.second, 9f, dot) }
        }

        // Labels sit just outside their own vertex, so the shape is never
        // obscured by the words that explain it.
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 30f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            color = ink
        }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 24f
            color = dim
        }
        val outer = Memory.radar(List(values.size) { 100 }, r + 74f)
        dna.axes.forEachIndexed { i, axis ->
            val p = outer[i]
            val x = cx + p.first
            val yy = cy + p.second + 10f
            c.drawText(axis.label, x, yy, label)
            c.drawText("${axis.value}", x, yy + 34f, sub)
        }

        y = H - 190f
        val rule = Paint().apply { color = AColor.argb(50, 255, 255, 255); strokeWidth = 2f }
        c.drawLine(96f, y, W - 96f, y, rule)
        y += 56
        text("How I listen, not what the songs sound like.", 26f, dim)
        y += 46
        text("BunMusic · a quiet place for loud music", 26f, lime)

        return bmp
    }

    /** Writes the DNA card and opens the share sheet. */
    fun shareDna(context: Context, dna: Memory.Dna) {
        val stamp = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "bunmusic-dna.png")
        runCatching {
            file.outputStream().use { out ->
                renderDna(dna, stamp).compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }.onFailure { return }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "My listening DNA — BunMusic")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share your DNA"))
    }
}
