package app.bunmusic.core

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * BunMusic icon set — every glyph is drawn from scratch on a 24x24 grid.
 * No icon-font dependency, no third-party asset: this is our own visual language.
 */
enum class G {
    Play, Pause, Next, Prev, Search, Home, Library, Shuffle, Repeat, RepeatOne,
    Heart, HeartFill, Queue, Back, Forward, More, Plus, Close, Mic, Download,
    Youtube, Clock, Check, Trash, Filter, Wave, Sparkle, Disc, List, Share, Offline
}

@Composable
fun Glyph(
    g: G,
    tint: Color = Ink.Text,
    size: Dp = 22.dp,
    stroke: Dp = 1.9.dp,
) {
    Canvas(Modifier.size(size)) {
        val k = this.size.minDimension / 24f
        val sw = stroke.toPx() / k
        scale(k, k, pivot = Offset.Zero) {
            drawGlyph(g, tint, sw)
        }
    }
}

private fun DrawScope.drawGlyph(g: G, c: Color, sw: Float) {
    val s = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float = sw) =
        drawLine(c, Offset(x1, y1), Offset(x2, y2), strokeWidth = w, cap = StrokeCap.Round)

    fun poly(vararg pts: Float, close: Boolean = false) {
        val p = Path()
        p.moveTo(pts[0], pts[1])
        var i = 2
        while (i + 1 < pts.size) {
            p.lineTo(pts[i], pts[i + 1]); i += 2
        }
        if (close) p.close()
        drawPath(p, c, style = s)
    }

    fun fill(vararg pts: Float) {
        val p = Path()
        p.moveTo(pts[0], pts[1])
        var i = 2
        while (i + 1 < pts.size) {
            p.lineTo(pts[i], pts[i + 1]); i += 2
        }
        p.close()
        drawPath(p, c)
    }

    when (g) {
        G.Play -> fill(8.5f, 5.2f, 19f, 12f, 8.5f, 18.8f)
        G.Pause -> {
            drawRoundRect(c, Offset(7.2f, 5.2f), Size(3.4f, 13.6f), androidx.compose.ui.geometry.CornerRadius(1.6f))
            drawRoundRect(c, Offset(13.4f, 5.2f), Size(3.4f, 13.6f), androidx.compose.ui.geometry.CornerRadius(1.6f))
        }
        G.Next -> {
            fill(5.5f, 5.4f, 15f, 12f, 5.5f, 18.6f)
            drawRoundRect(c, Offset(16.4f, 5.4f), Size(2.2f, 13.2f), androidx.compose.ui.geometry.CornerRadius(1.1f))
        }
        G.Prev -> {
            fill(18.5f, 5.4f, 9f, 12f, 18.5f, 18.6f)
            drawRoundRect(c, Offset(5.4f, 5.4f), Size(2.2f, 13.2f), androidx.compose.ui.geometry.CornerRadius(1.1f))
        }
        G.Search -> {
            drawCircle(c, 6.2f, Offset(10.6f, 10.6f), style = s)
            line(15.2f, 15.2f, 20f, 20f, sw * 1.05f)
        }
        G.Home -> {
            poly(3.4f, 10.6f, 12f, 3.6f, 20.6f, 10.6f)
            poly(6.2f, 10.2f, 6.2f, 20.2f, 17.8f, 20.2f, 17.8f, 10.2f)
        }
        G.Library -> {
            line(5.6f, 4.2f, 5.6f, 19.8f, sw * 1.15f)
            line(10.6f, 4.2f, 10.6f, 19.8f, sw * 1.15f)
            poly(15.2f, 6.2f, 19.4f, 18.6f)
            line(15.2f, 6.2f, 15.2f, 6.2f)
        }
        G.Shuffle -> {
            poly(3.5f, 17.5f, 7.5f, 17.5f, 16.5f, 6.5f, 20.5f, 6.5f)
            poly(18f, 4.2f, 20.6f, 6.5f, 18f, 8.8f)
            poly(3.5f, 6.5f, 7.5f, 6.5f, 11f, 11.2f)
            poly(13.4f, 14.2f, 16.5f, 17.5f, 20.5f, 17.5f)
            poly(18f, 15.2f, 20.6f, 17.5f, 18f, 19.8f)
        }
        G.Repeat -> {
            poly(6.6f, 6.2f, 17.4f, 6.2f, 20f, 8.8f)
            poly(20f, 8.8f, 20f, 13.4f)
            poly(17.4f, 17.8f, 6.6f, 17.8f, 4f, 15.2f)
            poly(4f, 15.2f, 4f, 10.6f)
            poly(15.2f, 3.9f, 17.6f, 6.2f, 15.2f, 8.5f)
            poly(8.8f, 20.1f, 6.4f, 17.8f, 8.8f, 15.5f)
        }
        G.RepeatOne -> {
            poly(6.6f, 6.2f, 17.4f, 6.2f, 20f, 8.8f)
            poly(20f, 8.8f, 20f, 13.4f)
            poly(17.4f, 17.8f, 6.6f, 17.8f, 4f, 15.2f)
            poly(4f, 15.2f, 4f, 10.6f)
            poly(15.2f, 3.9f, 17.6f, 6.2f, 15.2f, 8.5f)
            poly(8.8f, 20.1f, 6.4f, 17.8f, 8.8f, 15.5f)
            line(11.2f, 9.8f, 12.6f, 9.8f)
            line(12.6f, 9.8f, 12.6f, 14.6f)
            line(11.4f, 14.6f, 13.8f, 14.6f)
        }
        G.Heart, G.HeartFill -> {
            val p = Path().apply {
                moveTo(12f, 20.4f)
                cubicTo(12f, 20.4f, 3.6f, 15.1f, 3.6f, 9.5f)
                cubicTo(3.6f, 6.7f, 5.7f, 4.6f, 8.3f, 4.6f)
                cubicTo(10f, 4.6f, 11.3f, 5.5f, 12f, 6.9f)
                cubicTo(12.7f, 5.5f, 14f, 4.6f, 15.7f, 4.6f)
                cubicTo(18.3f, 4.6f, 20.4f, 6.7f, 20.4f, 9.5f)
                cubicTo(20.4f, 15.1f, 12f, 20.4f, 12f, 20.4f)
                close()
            }
            if (g == G.HeartFill) drawPath(p, c) else drawPath(p, c, style = s)
        }
        G.Queue -> {
            line(4f, 6.5f, 20f, 6.5f)
            line(4f, 12f, 13.5f, 12f)
            line(4f, 17.5f, 10.5f, 17.5f)
            fill(14.6f, 13.6f, 20.4f, 16.9f, 14.6f, 20.2f)
        }
        G.Back -> poly(14.6f, 4.6f, 7.4f, 12f, 14.6f, 19.4f)
        G.Forward -> poly(9.4f, 4.6f, 16.6f, 12f, 9.4f, 19.4f)
        G.More -> {
            drawCircle(c, 1.55f, Offset(5.4f, 12f))
            drawCircle(c, 1.55f, Offset(12f, 12f))
            drawCircle(c, 1.55f, Offset(18.6f, 12f))
        }
        G.Plus -> { line(12f, 4.8f, 12f, 19.2f); line(4.8f, 12f, 19.2f, 12f) }
        G.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
        G.Mic -> {
            drawRoundRect(c, Offset(9f, 2.8f), Size(6f, 10.6f), androidx.compose.ui.geometry.CornerRadius(3f), style = s)
            poly(5.8f, 11.2f, 5.8f, 12.2f)
            drawArc(c, 200f, 140f, false, Offset(5.4f, 6.2f), Size(13.2f, 13.2f), style = s)
            line(12f, 19.4f, 12f, 21.4f)
        }
        G.Download -> {
            line(12f, 3.8f, 12f, 14.6f)
            poly(7.6f, 10.4f, 12f, 14.9f, 16.4f, 10.4f)
            line(4.6f, 19.6f, 19.4f, 19.6f)
        }
        G.Youtube -> {
            drawRoundRect(c, Offset(2.6f, 5.6f), Size(18.8f, 12.8f), androidx.compose.ui.geometry.CornerRadius(4f), style = s)
            fill(10.2f, 9f, 15.2f, 12f, 10.2f, 15f)
        }
        G.Clock -> {
            drawCircle(c, 8.4f, Offset(12f, 12f), style = s)
            poly(12f, 6.8f, 12f, 12.2f, 15.8f, 14.4f)
        }
        G.Check -> poly(4.8f, 12.8f, 9.8f, 17.8f, 19.2f, 6.6f)
        G.Trash -> {
            line(4.4f, 6.8f, 19.6f, 6.8f)
            poly(9.2f, 4.2f, 14.8f, 4.2f)
            poly(6.4f, 6.8f, 7.4f, 20f, 16.6f, 20f, 17.6f, 6.8f)
            line(10.2f, 10.4f, 10.6f, 16.6f)
            line(13.8f, 10.4f, 13.4f, 16.6f)
        }
        G.Filter -> {
            line(3.8f, 6.4f, 20.2f, 6.4f)
            line(6.6f, 12f, 17.4f, 12f)
            line(9.4f, 17.6f, 14.6f, 17.6f)
        }
        G.Wave -> {
            val hs = floatArrayOf(6f, 12f, 8.5f, 15f, 5f)
            hs.forEachIndexed { i, h ->
                val x = 4.4f + i * 4.1f
                drawRoundRect(
                    c,
                    Offset(x, 12f - h / 2f),
                    Size(2.3f, h),
                    androidx.compose.ui.geometry.CornerRadius(1.15f)
                )
            }
        }
        G.Sparkle -> {
            fill(12f, 2.8f, 13.9f, 9.4f, 20.6f, 11.4f, 13.9f, 13.4f, 12f, 20f, 10.1f, 13.4f, 3.4f, 11.4f, 10.1f, 9.4f)
            fill(18.6f, 15.4f, 19.5f, 18.1f, 22.2f, 19f, 19.5f, 19.9f, 18.6f, 22.6f, 17.7f, 19.9f, 15f, 19f, 17.7f, 18.1f)
        }
        G.Disc -> {
            drawCircle(c, 8.6f, Offset(12f, 12f), style = s)
            drawCircle(c, 1.7f, Offset(12f, 12f))
        }
        G.List -> {
            line(8.6f, 6.4f, 20.2f, 6.4f)
            line(8.6f, 12f, 20.2f, 12f)
            line(8.6f, 17.6f, 20.2f, 17.6f)
            drawCircle(c, 1.35f, Offset(4.6f, 6.4f))
            drawCircle(c, 1.35f, Offset(4.6f, 12f))
            drawCircle(c, 1.35f, Offset(4.6f, 17.6f))
        }
        G.Share -> {
            drawCircle(c, 2.2f, Offset(17.6f, 5.4f), style = s)
            drawCircle(c, 2.2f, Offset(6.4f, 12f), style = s)
            drawCircle(c, 2.2f, Offset(17.6f, 18.6f), style = s)
            line(8.4f, 10.9f, 15.6f, 6.5f)
            line(8.4f, 13.1f, 15.6f, 17.5f)
        }
        G.Offline -> {
            drawCircle(c, 8.4f, Offset(12f, 12f), style = s)
            line(5.4f, 18.6f, 18.6f, 5.4f)
        }
    }
}
