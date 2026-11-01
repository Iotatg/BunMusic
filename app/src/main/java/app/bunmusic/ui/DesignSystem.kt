package app.bunmusic.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.Ink
import app.bunmusic.core.LocalReducedMotion
import app.bunmusic.core.SoraFamily
import app.bunmusic.core.motionMs

/**
 * The small pieces Spotify's layout is built from, rebuilt here.
 *
 * A section header that carries its own count, a title that scrolls instead of
 * truncating, a ring instead of a bar, and stat pills. Each one is a single
 * composable so a screen cannot invent a fifth version of the same row.
 */

/**
 * A title that scrolls when it does not fit.
 *
 * Truncating a song title is the one place an ellipsis is genuinely wrong: the
 * end of the title is often the part that tells two versions apart. With reduced
 * motion on it falls back to a plain ellipsis, because a permanently moving line
 * of text is exactly what that setting exists to stop.
 */
@Composable
fun MarqueeText(
    text: String,
    color: Color = Ink.Text,
    size: Int = 15,
    family: FontFamily = SoraFamily,
    weight: FontWeight = FontWeight.SemiBold,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    Text(
        text = text,
        color = color,
        fontSize = size.sp,
        fontFamily = family,
        fontWeight = weight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = if (reduced) modifier else modifier.basicMarquee(iterations = Int.MAX_VALUE),
    )
}

/** `Title ——— 12 · See all`. The count is optional; the action is optional. */
@Composable
fun SectionTitle(
    title: String,
    count: Int? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Ink.Text, fontSize = 16.sp, fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(Ink.Field)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text("$count", color = Ink.Faint, fontSize = 10.sp, fontFamily = SoraFamily)
            }
        }
        Spacer(Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                color = Ink.Lime,
                fontSize = 11.5.sp,
                fontFamily = SoraFamily,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = onAction != null) { onAction?.invoke() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** A number with a caption, in a small inset card. */
@Composable
fun StatPill(
    value: String,
    label: String,
    tint: Color = Ink.Text,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.Field)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(value, color = tint, fontSize = 16.sp, fontFamily = SoraFamily, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(2.dp))
        Text(label, color = Ink.Faint, fontSize = 9.5.sp, fontFamily = SoraFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A circular progress indicator. Used around the play button and on mix cards. */
@Composable
fun ProgressRing(
    progress: Float,
    size: Dp = 44.dp,
    stroke: Dp = 2.5.dp,
    tint: Color = Ink.Lime,
    track: Color = Ink.Hairline,
    content: @Composable (() -> Unit)? = null,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(if (LocalReducedMotion.current) 0 else 320),
        label = "ring",
    )
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val inset = sw / 2f
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = sw, cap = StrokeCap.Round),
            )
            if (animated > 0f) {
                drawArc(
                    color = tint,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = sw, cap = StrokeCap.Round),
                )
            }
        }
        content?.invoke()
    }
}

/** A row of stat pills that scrolls nowhere: it wraps to two rows of three. */
@Composable
fun StatRow(items: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { (value, label) ->
            StatPill(value, label, modifier = Modifier.weight(1f))
        }
    }
}

/** Fade in/out that respects reduced motion. */
@Composable
fun MotionFade(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(motionMs(220))),
        exit = fadeOut(tween(motionMs(160))),
    ) { content() }
}
