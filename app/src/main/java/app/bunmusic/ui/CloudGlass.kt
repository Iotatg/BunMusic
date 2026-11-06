package app.bunmusic.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.bunmusic.core.CloudOptics
import app.bunmusic.core.Ink
import app.bunmusic.core.LocalReducedMotion
import app.bunmusic.core.Skin

/**
 * Cloud Blue, made of light instead of paper.
 *
 * Before 2.11.0 Cloud was a flat chalk page: `glass()` degraded every light bun
 * to a solid white card, and the only moving backdrop belonged to Nebula. This
 * file gives Cloud its own weather and its own surface language:
 *
 *  - [CloudBackdrop] — a pale sky with slow, drifting blue/white/cyan light
 *    pools. They are what the glass has to *be* translucent over; without them a
 *    translucent pane over a flat page is just a slightly wrong white.
 *  - [cloudGlass] — a frosted pane: a translucent fill, a bright specular line
 *    along the top edge, a cool rim at the bottom, a soft blue shadow, and one
 *    slow band of light crossing it.
 *  - [cloudShine] — just the top-edge light, for surfaces that already have a
 *    colour of their own (the blue hero card, the play disc).
 *  - [cloudArtSheen] — a moving reflection laid over cover art.
 *
 * Every opacity and position comes from [CloudOptics], so the physics is tested
 * on the JVM and identical on every device. Motion is read from a single phase
 * [State] inside the draw lambdas — the sky and the sweeps repaint without a
 * recomposition, and a frozen clock still produces the frame that belongs to it.
 * Under reduced motion the phase is a fixed constant: the light is still there,
 * it simply stops travelling.
 */

/** One full drift of the sky. Slow enough to be weather, not a spinner. */
const val CLOUD_SKY_PERIOD_MS = 22_000

/** One specular pass across a surface. */
const val CLOUD_SHEEN_PERIOD_MS = 5_600

/** A phase that is fixed and complete when motion is reduced, else drifting. */
@Composable
private fun cloudPhase(label: String, periodMs: Int, stillPhase: Float): State<Float> {
    if (LocalReducedMotion.current) {
        return remember { mutableStateOf(stillPhase) }
    }
    val transition = rememberInfiniteTransition(label = label)
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(periodMs, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = label,
    )
}

// ------------------------------------------------------------------- backdrop

/**
 * The sky behind Cloud's glass. Three tinted light pools drift on independent
 * slow paths over a pale vertical ground. It is deliberately *not* a blur: a
 * RenderEffect needs API 31 and a full-screen offscreen buffer per frame, while
 * three soft radial gradients cost almost nothing behind a scrolling list.
 */
@Composable
fun CloudBackdrop(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
) {
    val phase = cloudPhase("cloudSky", CLOUD_SKY_PERIOD_MS, 0.24f)
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawCloudSky(phase.value, intensity)
        }
    }
}

private fun DrawScope.drawCloudSky(phase: Float, intensity: Float) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val span = size.minDimension

    // 1. The ground: chalk at the top easing into the faintest blue at the base.
    //    A flat page has nothing for glass to refract; this has a little depth.
    drawRect(
        Brush.verticalGradient(
            0f to Ink.Raised,
            0.55f to Ink.Base,
            1f to Ink.Void,
        )
    )

    // 2. Ambient pools. Tints are the bun's own blue, a clean white and a cool
    //    cyan; alpha stays low so captions drawn over the sky keep their ratio.
    val tints = arrayOf(Ink.Lime, Color.White, Ink.Mint)
    CloudOptics.poolCentres(phase).forEach { pool ->
        val cx = w * pool.x
        val cy = h * pool.y
        val r = span * pool.radius
        val color = tints[pool.tint % tints.size]
        val a = (pool.alpha * intensity).coerceIn(0f, 0.30f)
        drawCircle(
            brush = Brush.radialGradient(
                0f to color.copy(alpha = a),
                0.55f to color.copy(alpha = a * 0.35f),
                1f to color.copy(alpha = 0f),
                center = Offset(cx, cy),
                radius = r,
            ),
            radius = r,
            center = Offset(cx, cy),
        )
    }

    // 3. A very wide, very dim horizon band so the transport has a floor, with
    //    explicit startY/endY — a brush sized to the canvas would otherwise be
    //    most of the way through its ramp where this band begins, painting a seam.
    drawRect(
        Brush.verticalGradient(
            0f to Color.Transparent,
            1f to Ink.Lime.copy(alpha = 0.045f * intensity),
            startY = h * 0.70f,
            endY = h,
        ),
        topLeft = Offset(0f, h * 0.70f),
        size = Size(w, h * 0.30f),
    )
}

// ---------------------------------------------------------------------- glass

/**
 * A frosted pane for Cloud. [elevation] feeds [CloudOptics.fillAlpha] — a raised
 * sheet is thicker than a flat card. The top specular line, cool bottom rim,
 * translucent fill and blue shadow are what separate this from a white card.
 */
@Composable
fun Modifier.cloudGlass(
    radius: Dp = 22.dp,
    elevation: Float = 0f,
    tint: Color = Color.White,
): Modifier {
    val shape = RoundedCornerShape(radius)
    val highContrast = Skin.contrast
    val fill = CloudOptics.fillAlpha(elevation, highContrast)
    val specular = CloudOptics.specularAlpha(highContrast)
    val rim = CloudOptics.rimAlpha(highContrast)
    val phase = cloudPhase("cloudGlass", CLOUD_SHEEN_PERIOD_MS, 0.30f)
    val blue = Color(CloudOptics.GLASS_BLUE)
    val shadowDp = (6f + elevation * 14f).dp

    return this
        // A blue-tinted shadow, not black: a lit pane lifts off a blue sky.
        .shadow(
            elevation = shadowDp,
            shape = shape,
            clip = false,
            ambientColor = blue.copy(alpha = 0.16f),
            spotColor = blue.copy(alpha = 0.14f),
        )
        .clip(shape)
        .drawBehind {
            // Translucent fill, brighter at the top where the sky hits it.
            drawRect(
                Brush.verticalGradient(
                    0f to tint.copy(alpha = fill),
                    0.5f to tint.copy(alpha = fill * 0.78f),
                    1f to tint.copy(alpha = fill * 0.60f),
                )
            )
            // One slow band of light crossing the pane.
            drawSheen(phase.value, tint)
            // Top specular line: transparent at the corners, lit in the middle.
            drawRect(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, tint.copy(alpha = specular), Color.Transparent)
                ),
                topLeft = Offset.Zero,
                size = Size(size.width, 1.6f.dp.toPx()),
            )
            // Cool rim at the bottom, refraction into the sky.
            drawRect(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, blue.copy(alpha = rim), Color.Transparent)
                ),
                topLeft = Offset(0f, size.height - 1.4f.dp.toPx()),
                size = Size(size.width, 1.4f.dp.toPx()),
            )
        }
        // A 1px edge that holds the light at the top and cools toward the base.
        .border(
            1.dp,
            Brush.verticalGradient(
                listOf(
                    tint.copy(alpha = specular * 0.9f),
                    tint.copy(alpha = 0.12f),
                    blue.copy(alpha = rim),
                )
            ),
            shape,
        )
}

/** Just the top-edge specular light, for a surface that already has its colour. */
@Composable
fun Modifier.cloudShine(
    radius: Dp = 22.dp,
    tint: Color = Color.White,
): Modifier {
    val shape = RoundedCornerShape(radius)
    val specular = CloudOptics.specularAlpha(Skin.contrast)
    val phase = cloudPhase("cloudShine", CLOUD_SHEEN_PERIOD_MS, 0.34f)
    return this
        .clip(shape)
        .drawWithContent {
            drawContent()
            drawSheen(phase.value, tint, strength = 0.6f)
            drawRect(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, tint.copy(alpha = specular), Color.Transparent)
                ),
                topLeft = Offset.Zero,
                size = Size(size.width, 2f.dp.toPx()),
            )
        }
        .border(
            1.dp,
            Brush.verticalGradient(
                listOf(tint.copy(alpha = specular * 0.7f), tint.copy(alpha = 0.05f))
            ),
            shape,
        )
}

/** A moving reflection over cover art — glass behaving like glass over an image. */
@Composable
fun Modifier.cloudArtSheen(shape: Shape): Modifier {
    val phase = cloudPhase("cloudArt", CLOUD_SHEEN_PERIOD_MS, 0.38f)
    return this
        .clip(shape)
        .drawWithContent {
            drawContent()
            // A permanent soft top-left reflection, plus one light sweep.
            drawRect(
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.16f),
                    0.35f to Color.White.copy(alpha = 0.02f),
                    1f to Color.Transparent,
                    end = Offset(size.width, size.height * 0.9f),
                )
            )
            drawSheen(phase.value, Color.White, strength = 0.55f)
        }
        .border(
            1.dp,
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0.04f))
            ),
            shape,
        )
}

/** Draws the one-shot light band from [CloudOptics.sheenSweep] within these bounds. */
private fun DrawScope.drawSheen(phase: Float, tint: Color, strength: Float = 1f) {
    val sh = CloudOptics.sheenSweep(phase) ?: return
    if (sh.alpha <= 0f) return
    val w = size.width
    val h = size.height
    val half = (sh.width * w) / 2f
    val c = sh.centre * w
    drawRect(
        brush = Brush.linearGradient(
            0f to Color.Transparent,
            0.5f to tint.copy(alpha = sh.alpha * strength),
            1f to Color.Transparent,
            start = Offset(c - half, 0f),
            end = Offset(c + half, h),
        ),
    )
}

