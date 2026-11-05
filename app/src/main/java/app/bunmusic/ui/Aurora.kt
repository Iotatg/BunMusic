package app.bunmusic.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import app.bunmusic.core.Cosmos
import app.bunmusic.core.Dust
import app.bunmusic.core.Ink
import app.bunmusic.core.LocalReducedMotion
import kotlin.math.cos
import kotlin.math.sin

/**
 * The rewritten Nebula sky.
 *
 * The old one was a poster: a cream crescent, a fixed field of sixty stars and
 * a violet well. It looked like a greeting card, and it never moved except for
 * a synchronous twinkle. This one is weather — four overlapping aurora lights
 * drifting on slow Lissajous paths, a deep near-black ground, and forty motes
 * that actually travel upward through it.
 *
 * The motion is deliberately driven from a single infinite transition phase,
 * not a frame loop. `phase` is a snapshot state read *inside* the draw lambda,
 * so the whole sky repaints without a single recomposition, and a paused clock
 * (a screenshot test, reduced motion, a process frozen in the background) still
 * produces the frame that belongs to that instant.
 *
 * Particle motion is the semi-implicit Euler step from
 * [PiotrPrus/ParticleEmitter](https://github.com/PiotrPrus/ParticleEmitter)
 * (`v += a·dt`, `p += v·dt`, wrap at the edge), expressed in closed form in
 * [Cosmos.at] so it stays pure and testable. The travelling-wave bar geometry
 * in `NebulaPlayer.kt` comes from
 * [zainkhalid91/compose-loaders](https://github.com/zainkhalid91/compose-loaders).
 */

/** Seconds for one full drift cycle. Slow enough not to read as a loading spinner. */
const val AURORA_PERIOD_SECONDS = 16f

@Composable
fun AuroraBackdrop(
    seed: String?,
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    count: Int = Cosmos.DEFAULT_DUST,
    clouds: Boolean = true,
) {
    val reduced = LocalReducedMotion.current
    val transition = if (reduced) null else rememberInfiniteTransition(label = "aurora")
    // A State, not a Float: the value is read inside the Canvas draw lambda, so
    // the sky repaints without recomposing the screen sixty times a second.
    val phaseState = transition?.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween((AURORA_PERIOD_SECONDS * 1000).toInt(), easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "drift",
    ) ?: remember { androidx.compose.runtime.mutableStateOf(0.28f) }

    // One field per song. A hard cut between two skies reads as a glitch, so a
    // change of track keeps the same lights and merely rearranges the motes.
    val field = remember(seed, count) { Cosmos.dust(seed, count) }

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val phase = phaseState.value
            drawAuroraGround(phase, intensity, clouds)
            val seconds = phase * AURORA_PERIOD_SECONDS
            drawDust(Cosmos.at(field, seconds), phase, intensity)
        }
    }
}

private fun DrawScope.drawAuroraGround(phase: Float, intensity: Float, clouds: Boolean) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val span = size.minDimension
    val t = phase * 2f * Math.PI.toFloat()

    // 1. The ground. Near-black violet, not purple: a dark page is what makes
    //    the neon edges and the artwork read as lit rather than merely coloured.
    drawRect(
        Brush.verticalGradient(
            0f to Color(0xFF0B0620),
            0.40f to Ink.Base,
            1f to Ink.Void,
        )
    )

    if (!clouds) return

    // 2. Aurora lights. Radial gradients, not a blur: four of them cost one draw
    //    call each and stay sharp at any density. Their centres move on slow,
    //    incommensurate paths so the sky never appears to loop in place.
    val lights = listOf(
        AuroraLight(0.16f + 0.10f * sin(t), 0.10f + 0.05f * cos(t * 0.7f), 1.05f, Ink.Lime, 0.20f),
        AuroraLight(0.88f + 0.07f * cos(t * 0.8f), 0.29f + 0.08f * sin(t * 1.1f), 0.92f, Ink.Mint, 0.13f),
        AuroraLight(0.46f + 0.13f * sin(t * 0.6f + 1f), 0.74f + 0.06f * cos(t * 0.9f), 1.00f, Ink.Rose, 0.11f),
        AuroraLight(0.72f + 0.09f * cos(t * 0.5f + 2f), 0.93f + 0.04f * sin(t), 0.80f, Ink.Lavender, 0.12f),
    )
    lights.forEach { l ->
        val cx = w * l.x
        val cy = h * l.y
        val r = span * l.radius
        drawCircle(
            brush = Brush.radialGradient(
                0f to l.color.copy(alpha = l.alpha * intensity),
                0.55f to l.color.copy(alpha = l.alpha * 0.32f * intensity),
                1f to l.color.copy(alpha = 0f),
                center = Offset(cx, cy),
                radius = r,
            ),
            radius = r,
            center = Offset(cx, cy),
        )
    }

    // 3. A horizon: one very wide, very dim band at the bottom, so the page has
    //    a floor and the transport pill has something to sit on.
    // Explicit startY/endY: a Brush created for a rect is sized to the *canvas*,
    // not to the rect's own topLeft. Without this the gradient was already 72 %
    // through its ramp where the band began, which painted a hard horizontal
    // seam across the page.
    drawRect(
        Brush.verticalGradient(
            0f to Color.Transparent,
            1f to Ink.Lime.copy(alpha = 0.055f * intensity),
            startY = h * 0.72f,
            endY = h,
        ),
        topLeft = Offset(0f, h * 0.72f),
        size = androidx.compose.ui.geometry.Size(w, h * 0.28f),
    )
}

private data class AuroraLight(
    val x: Float,
    val y: Float,
    val radius: Float,
    val color: Color,
    val alpha: Float,
)

/**
 * Starlight with depth: motes are tinted by the three aurora colours, scaled by
 * their own radius, and faded by a raised cosine so they do not blink together.
 * No cross flares — a sharp white cross on a soft sky is what made the old one
 * look like clip-art.
 */
private fun DrawScope.drawDust(field: List<Dust>, phase: Float, intensity: Float) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val span = size.minDimension
    val colours = arrayOf(Ink.Lavender, Ink.Mint, Ink.Rose)
    field.forEach { mote ->
        val a = Cosmos.dustAlpha(mote.phase, phase) * intensity
        if (a <= 0.01f) return@forEach
        val r = span * mote.radius
        drawCircle(
            color = colours[mote.hue % colours.size].copy(alpha = a.coerceAtMost(0.85f)),
            radius = r,
            center = Offset(w * mote.x, h * mote.y),
        )
    }
}
