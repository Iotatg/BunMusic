package app.bunmusic.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.Cosmos
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Ink
import app.bunmusic.core.LocalReducedMotion
import app.bunmusic.core.SoraFamily
import app.bunmusic.core.Star
import kotlin.math.min

/**
 * The cosmic layer: a sky, a moon, glass, neon, and the bun.
 *
 * Everything in here is painted, not shipped as an image. A 1080 px backdrop
 * would add a megabyte to the APK and go soft on a tablet; a star field is sixty
 * circles whose positions are decided by [Cosmos] and therefore testable.
 *
 * The layer is only used by the Nebula bun. Stars behind a chalk page read as
 * dust on the glass, so the other four buns keep their flat paper and only
 * borrow the glass surfaces, which derive their tint from the palette.
 */

// ------------------------------------------------------------------ backdrop

/**
 * The page's weather: a violet well, two slow clouds, a field of stars and
 * optionally a crescent.
 *
 * [moon] is passed explicitly because where a moon belongs is a layout decision:
 * Home wants stars only (its header sits at the top), the player wants one
 * behind the artwork. Nothing here decides that for the caller.
 */
@Composable
fun NebulaBackdrop(
    seed: String?,
    modifier: Modifier = Modifier,
    moon: Cosmos.Moon? = null,
    intensity: Float = 1f,
    count: Int = Cosmos.DEFAULT_STARS,
    clouds: Boolean = true,
) {
    val reduced = LocalReducedMotion.current
    val transition = if (reduced) null else rememberInfiniteTransition(label = "sky")
    val phase = transition?.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9_000, easing = LinearEasing), RepeatMode.Restart),
        label = "twinkle",
    )
    Box(modifier.fillMaxSize()) {
        // The ground: gradient, clouds, moon. It does not depend on the song, so
        // it is drawn once and never re-entered when the track changes.
        Canvas(Modifier.fillMaxSize()) { drawSkyBase(moon, intensity, clouds) }

        // The stars do depend on the song, and a hard cut from one sky to another
        // reads as a glitch rather than as a change of weather. Crossfading the
        // star layer only — the gradient underneath is identical — means a track
        // change looks like the sky rearranging itself.
        Crossfade(
            targetState = seed,
            modifier = Modifier.fillMaxSize(),
            animationSpec = tween(if (reduced) 0 else 700),
            label = "skyChange",
        ) { s ->
            val stars = remember(s) { Cosmos.stars(s, count) }
            Canvas(Modifier.fillMaxSize()) {
                // The animated value is read here, in the draw phase, not in the
                // composable body: a sky that recomposes sixty times a second to
                // move sixty dots is the expensive way to draw nothing.
                drawStars(stars, phase?.value ?: 0.35f, intensity)
            }
        }
    }
}

private fun DrawScope.drawSkyBase(
    moon: Cosmos.Moon?,
    intensity: Float,
    clouds: Boolean,
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val span = size.minDimension

    // 1. the page itself
    drawRect(
        Brush.verticalGradient(
            0f to Color(0xFF12082A),
            0.42f to Ink.Base,
            1f to Ink.Void,
        )
    )

    // 2. nebula clouds. Not a blur — two overlapping radial gradients cost
    //    almost nothing and sit behind a list that is also scrolling.
    if (clouds) {
        blob(w * 0.18f, h * 0.06f, span * 0.95f, Ink.Lime, 0.16f * intensity)
        blob(w * 0.92f, h * 0.26f, span * 0.80f, Ink.Lavender, 0.10f * intensity)
        blob(w * 0.50f, h * 0.88f, span * 0.90f, Color(0xFF3B1E7A), 0.22f * intensity)
    }

    // 3. the moon, if this page has one
    if (moon != null) {
        val cx = w * moon.cx
        val cy = h * moon.cy
        val r = span * moon.radius
        blob(cx, cy, r * 2.1f, Color(0xFFFFD9A0), 0.13f * intensity)
        drawCrescent(cx, cy, r)
    }
}

private fun DrawScope.blob(cx: Float, cy: Float, radius: Float, color: Color, a: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = a), color.copy(alpha = 0f)),
            center = Offset(cx, cy),
            radius = radius,
        ),
        radius = radius,
        center = Offset(cx, cy),
    )
}

private fun DrawScope.drawStars(stars: List<Star>, phase: Float, intensity: Float) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val span = size.minDimension
    stars.forEach { star ->
        val a = Cosmos.twinkle(star.phase, phase) * intensity
        val c = Color.White.copy(alpha = a * if (star.bright) 0.95f else 0.62f)
        val cx = w * star.x
        val cy = h * star.y
        val r = span * star.radius
        drawCircle(c, r, Offset(cx, cy))
        if (star.bright) {
            // A cross flare: two hairlines, not a glow. A glow on sixty stars is
            // sixty offscreen buffers.
            val len = r * 5.5f
            drawLine(c.copy(alpha = a * 0.35f), Offset(cx - len, cy), Offset(cx + len, cy), r * 0.45f)
            drawLine(c.copy(alpha = a * 0.35f), Offset(cx, cy - len), Offset(cx, cy + len), r * 0.45f)
        }
    }
}

/**
 * The crescent itself, carved with an even-odd fill.
 *
 * The same geometry as [Crescent] in this file, kept in one place so the one on
 * the page and the one inside a card cannot drift apart.
 */
private fun DrawScope.drawCrescent(
    cx: Float,
    cy: Float,
    r: Float,
    top: Color = Color(0xFFFFE9BE),
    bottom: Color = Color(0xFFFFC98A),
) {
    val outer = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r))
    }
    val inner = Path().apply {
        val ir = r * 0.86f
        val ix = cx + r * 0.52f
        val iy = cy - r * 0.18f
        addOval(androidx.compose.ui.geometry.Rect(ix - ir, iy - ir, ix + ir, iy + ir))
    }
    val crescent = Path().apply {
        fillType = PathFillType.EvenOdd
        addPath(outer)
        addPath(inner)
    }
    drawPath(
        path = crescent,
        brush = Brush.linearGradient(
            listOf(top, bottom),
            start = Offset(cx - r, cy - r),
            end = Offset(cx + r, cy + r),
        ),
    )
}

// --------------------------------------------------------------------- glass

/**
 * A surface that holds light at its top edge.
 *
 * On a light bun this degrades to the card the rest of the app already uses,
 * because white-on-white glass is just a slightly wrong white.
 */
@Composable
fun Modifier.glass(
    radius: Dp = 22.dp,
    tint: Color = Color.White,
    topAlpha: Float = 0.10f,
    bottomAlpha: Float = 0.025f,
): Modifier {
    val shape = RoundedCornerShape(radius)
    if (Ink.isLight) {
        return this
            .clip(shape)
            .background(Ink.Card)
            .border(1.dp, Ink.Hairline, shape)
    }
    return this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(tint.copy(alpha = topAlpha), tint.copy(alpha = bottomAlpha)),
            )
        )
        .border(
            1.dp,
            Brush.verticalGradient(
                listOf(tint.copy(alpha = 0.26f), tint.copy(alpha = 0.04f)),
            ),
            shape,
        )
}

/**
 * A coloured shadow, used instead of a stroke to make something look lit.
 *
 * API 26–27 paint this black — a shadow that is merely dark is still a shadow,
 * so the fallback is acceptable. Nothing is clipped, or the glow would be cut
 * off at the shape's own edge, which is the one place it must not be.
 */
@Composable
fun Modifier.neon(
    color: Color,
    radius: Dp = 24.dp,
    elevation: Dp = 18.dp,
): Modifier = if (Ink.isLight) this else this.shadow(
    elevation = elevation,
    shape = RoundedCornerShape(radius),
    clip = false,
    ambientColor = color,
    spotColor = color,
)

// ----------------------------------------------------------------------- bun

/**
 * The bun: asleep in headphones, on a cloud, with a cat.
 *
 * Drawn in a 100 × 100 unit space so it scales to a 28 dp avatar and a 220 dp
 * hero without a second asset. When music is playing it breathes a little
 * faster and the ear-cup rings pulse; when nothing is playing it is simply
 * asleep, which is the honest state for a music app with no track.
 */
@Composable
fun BunMascot(
    modifier: Modifier = Modifier,
    playing: Boolean = false,
    breathing: Boolean = true,
) {
    val reduced = LocalReducedMotion.current
    // Held as state and read inside the draw lambda: the bun breathes twice a
    // second, and it should not drag the whole composable through recomposition
    // to do it.
    val breath = if (reduced || !breathing) {
        null
    } else {
        rememberInfiniteTransition(label = "bunBreath").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(if (playing) 2_600 else 4_200, easing = LinearEasing),
                RepeatMode.Reverse,
            ),
            label = "breath",
        )
    }
    Canvas(modifier) { drawBun(breath?.value ?: 0.5f, playing) }
}

private fun DrawScope.drawBun(t: Float, playing: Boolean) {
    val s = min(size.width, size.height) / 100f
    if (s <= 0f) return
    val ox = (size.width - 100f * s) / 2f
    val oy = (size.height - 100f * s) / 2f
    fun o(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
    fun r(v: Float) = v * s

    val accent = Ink.Lime
    val ink = Color(0xFF241638)
    val cream = Color(0xFFFBF7FF)
    val hoodie = Color(0xFF1B1233)

    // halo
    drawCircle(
        brush = Brush.radialGradient(
            listOf(accent.copy(alpha = if (playing) 0.26f else 0.16f), accent.copy(alpha = 0f)),
            center = o(50f, 56f),
            radius = r(48f),
        ),
        radius = r(48f),
        center = o(50f, 56f),
    )

    // the breath: the head rises about a unit, the cloud does not
    val bob = (t - 0.5f) * 1.8f

    // cloud
    val cloud = Color(0xFFEDE6FF)
    drawOval(cloud.copy(alpha = 0.16f), o(18f, 84f), Size(r(64f), r(12f)))
    drawCircle(cloud, r(14f), o(32f, 82f))
    drawCircle(cloud, r(17f), o(50f, 84f))
    drawCircle(cloud, r(13f), o(68f, 82f))
    drawOval(cloud, o(22f, 80f), Size(r(56f), r(14f)))

    // hoodie: a dark mass behind the head, wider at the bottom
    val body = Path().apply {
        moveTo(ox + 30f * s, oy + (82f + bob * 0.3f) * s)
        quadraticTo(ox + 28f * s, oy + (56f + bob * 0.3f) * s, ox + 50f * s, oy + (54f + bob * 0.3f) * s)
        quadraticTo(ox + 72f * s, oy + (56f + bob * 0.3f) * s, ox + 70f * s, oy + (82f + bob * 0.3f) * s)
        close()
    }
    drawPath(body, hoodie)
    // rim light on the shoulders
    drawPath(
        body,
        brush = Brush.verticalGradient(
            listOf(accent.copy(alpha = 0f), accent.copy(alpha = 0.30f)),
        ),
        style = Stroke(width = r(1.2f)),
    )

    // head
    drawOval(
        brush = Brush.verticalGradient(
            listOf(Color(0xFFFFFFFF), cream, Color(0xFFE6DCFA)),
            startY = oy + (28f + bob) * s,
            endY = oy + (72f + bob) * s,
        ),
        topLeft = o(30f, 28f + bob),
        size = Size(r(40f), r(40f)),
    )

    // hood over the top of the head
    drawArc(
        color = hoodie,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = o(26f, 20f + bob),
        size = Size(r(48f), r(40f)),
    )
    // and a soft edge where the hood meets the face
    drawArc(
        color = Color(0xFF3A2A5C),
        startAngle = 186f,
        sweepAngle = 168f,
        useCenter = false,
        topLeft = o(26f, 20f + bob),
        size = Size(r(48f), r(40f)),
        style = Stroke(width = r(1.4f)),
    )

    // headphone band
    drawArc(
        color = Color(0xFF120C22),
        startAngle = 196f,
        sweepAngle = 148f,
        useCenter = false,
        topLeft = o(27f, 22f + bob),
        size = Size(r(46f), r(46f)),
        style = Stroke(width = r(4.4f), cap = StrokeCap.Round),
    )

    // ear cups
    listOf(28f, 72f).forEach { cx ->
        val cy = 50f + bob
        drawCircle(Color(0xFF120C22), r(8.6f), o(cx, cy))
        drawCircle(
            color = accent.copy(alpha = if (playing) 0.55f + 0.35f * t else 0.45f),
            radius = r(8.6f),
            center = o(cx, cy),
            style = Stroke(width = r(1.6f)),
        )
        drawCircle(accent, r(2.2f), o(cx, cy))
    }

    // face — closed eyes, a small mouth, two blushes
    drawArc(
        color = ink,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = o(39.6f, 46f + bob),
        size = Size(r(7f), r(6f)),
        style = Stroke(width = r(2.1f), cap = StrokeCap.Round),
    )
    drawArc(
        color = ink,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = o(53.4f, 46f + bob),
        size = Size(r(7f), r(6f)),
        style = Stroke(width = r(2.1f), cap = StrokeCap.Round),
    )
    drawArc(
        color = ink.copy(alpha = 0.75f),
        startAngle = 20f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = o(47f, 55f + bob),
        size = Size(r(6f), r(4f)),
        style = Stroke(width = r(1.6f), cap = StrokeCap.Round),
    )
    drawCircle(Color(0xFFFF9EC4).copy(alpha = 0.45f), r(3.2f), o(37f, 54f + bob))
    drawCircle(Color(0xFFFF9EC4).copy(alpha = 0.45f), r(3.2f), o(63f, 54f + bob))

    // the cat, asleep against the bun's shoulder
    val fur = Color(0xFFC9C3D8)
    drawOval(fur, o(68f, 74f), Size(r(15f), r(11f)))
    drawCircle(fur, r(6.2f), o(78f, 72f))
    // ears
    val earL = Path().apply {
        moveTo(ox + 73.4f * s, oy + 69.5f * s)
        lineTo(ox + 74.6f * s, oy + 64.6f * s)
        lineTo(ox + 77.2f * s, oy + 68.2f * s)
        close()
    }
    val earR = Path().apply {
        moveTo(ox + 80.2f * s, oy + 68.2f * s)
        lineTo(ox + 82.6f * s, oy + 64.8f * s)
        lineTo(ox + 83.4f * s, oy + 69.6f * s)
        close()
    }
    drawPath(earL, fur)
    drawPath(earR, fur)
    // closed eyes
    drawLine(ink.copy(alpha = 0.55f), o(75.2f, 72f), o(76.8f, 72f), r(0.9f), StrokeCap.Round)
    drawLine(ink.copy(alpha = 0.55f), o(79.2f, 72f), o(80.8f, 72f), r(0.9f), StrokeCap.Round)
    // tail
    drawArc(
        color = fur,
        startAngle = 300f,
        sweepAngle = 130f,
        useCenter = false,
        topLeft = o(66f, 76f),
        size = Size(r(12f), r(10f)),
        style = Stroke(width = r(2.4f), cap = StrokeCap.Round),
    )
}

// -------------------------------------------------------------------- pills

/**
 * The lit pill from the reference: a violet gradient, white label, a real glow.
 *
 * Used where a single action is the point of the card — "Play now", "Find
 * something" — not for every button, because a page of glowing pills is a page
 * with no emphasis left.
 */
@Composable
fun GlowPill(
    text: String,
    glyph: G? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glow: Boolean = true,
) {
    val base = if (Ink.isLight) Ink.Lime else Ink.Lime
    val bright = if (Ink.isLight) Ink.Lime else Ink.Lavender
    val ink = if (Ink.isLight) Ink.OnAccent else Color(0xFF12002B)
    val shape = CircleShape
    Row(
        modifier
            .then(if (glow) Modifier.neon(base, radius = 30.dp, elevation = 20.dp) else Modifier)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(base, bright)))
            .tapable(scale = 0.94f) { onClick() }
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            Glyph(glyph, ink, 13.dp)
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text,
            color = ink,
            fontSize = 12.5.sp,
            fontFamily = SoraFamily,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** A hairline that fades out at both ends, in the bun's own accent. */
@Composable
fun GlowRule(modifier: Modifier = Modifier, height: Dp = 1.dp) {
    androidx.compose.foundation.layout.Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Ink.Lime.copy(alpha = 0.45f), Color.Transparent)
                )
            )
    )
}

/**
 * A crescent on its own, for the places that need one inside a card rather than
 * across a page — the empty home card and the player's backdrop.
 *
 * Even-odd fill, so the shape is carved rather than painted over: a crescent
 * drawn as "circle, then a second circle in the background colour" leaves a seam
 * the moment the card behind it is translucent.
 */
@Composable
fun Crescent(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFFFD9A0),
    alpha: Float = 0.9f,
) {
    Canvas(modifier) {
        val r = size.minDimension * 0.40f
        if (r <= 0f) return@Canvas
        drawCrescent(
            cx = size.width * 0.5f,
            cy = size.height * 0.5f,
            r = r,
            top = color.copy(alpha = alpha),
            bottom = color.copy(alpha = alpha * 0.55f),
        )
    }
}
