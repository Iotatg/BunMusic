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
import app.bunmusic.data.Store
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
}
