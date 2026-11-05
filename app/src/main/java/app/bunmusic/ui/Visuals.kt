package app.bunmusic.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.BunEase
import app.bunmusic.core.BunSpring
import app.bunmusic.core.BunStagger
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Haptics
import app.bunmusic.core.Ink
import app.bunmusic.core.Labels
import app.bunmusic.core.LocalReducedMotion
import app.bunmusic.core.SoraFamily
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The layer every screen sits on: colour that moves, press feedback, and the
 * small amount of choreography that makes a list feel dealt rather than loaded.
 *
 * The rule this file follows is that none of it is decoration for its own sake.
 * A press scale tells the thumb the tap landed. A stagger tells the eye where to
 * start reading. The aurora gives a dark screen a centre of gravity so a 33 sp
 * greeting is not floating on a flat rectangle. Everything collapses to a still
 * frame when the phone asks for less motion — that is checked here, once per
 * effect, not at each call site.
 */

// ------------------------------------------------------------------- aurora

/**
 * Three hues from one id, spaced far enough apart to read as light rather than
 * as a gradient. Deterministic: the same song always glows the same way, so the
 * backdrop does not change when a screen recomposes.
 */
fun auroraTints(seed: String?, light: Boolean): List<Color> {
    val s = seed ?: "bunmusic"
    var h = 7
    for (ch in s) h = (h * 31 + ch.code) and 0x7fffffff
    val base = (h % 360).toFloat()
    return listOf(
        Color.hsv(base, if (light) 0.30f else 0.55f, if (light) 0.99f else 0.62f),
        Color.hsv((base + 74f) % 360f, if (light) 0.34f else 0.52f, if (light) 0.97f else 0.55f),
        Color.hsv((base + 214f) % 360f, if (light) 0.30f else 0.48f, if (light) 0.96f else 0.50f),
    )
}

/**
 * A slow field of colour behind the content.
 *
 * Not a blur: three radial gradients whose centres drift on a Lissajous path.
 * A blur would need RenderEffect (API 31) or a full-screen offscreen buffer per
 * frame; two soft gradients that overlap look the same and cost almost nothing,
 * which matters because this sits behind a list that is also scrolling.
 */
@Composable
fun AuroraBackdrop(
    seed: String?,
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
) {
    val light = Ink.isLight
    val tints = remember(seed, light) { auroraTints(seed, light) }
    val base = if (light) 0.16f else 0.20f

    if (LocalReducedMotion.current) {
        Canvas(modifier.fillMaxSize()) {
            drawAurora(tints, base * intensity, 0.0)
        }
        return
    }

    val drift = rememberInfiniteTransition(label = "aurora")
    val phase by drift.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(46_000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift",
    )
    Canvas(modifier.fillMaxSize()) {
        drawAurora(tints, base * intensity, phase.toDouble())
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAurora(
    tints: List<Color>,
    alpha: Float,
    phase: Double,
) {
    val w = size.width
    val h = size.height
    val span = size.minDimension
    if (w <= 0f || h <= 0f) return

    fun blob(cx: Float, cy: Float, radius: Float, color: Color, a: Float) {
        // Fading to the colour's own zero alpha, not to Transparent: transparent
        // black drags every gradient toward grey and the field looks dirty.
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

    val a = phase * 2.0 * PI
    blob(
        cx = w * (0.30f + 0.10f * sin(a).toFloat()),
        cy = h * (0.10f + 0.05f * cos(a * 0.7).toFloat()),
        radius = span * 0.85f,
        color = tints[0],
        a = alpha,
    )
    blob(
        cx = w * (0.86f + 0.08f * cos(a * 0.8).toFloat()),
        cy = h * (0.30f + 0.08f * sin(a * 0.6).toFloat()),
        radius = span * 0.72f,
        color = tints[1],
        a = alpha * 0.72f,
    )
    blob(
        cx = w * (0.12f + 0.12f * cos(a * 0.5 + 2.0).toFloat()),
        cy = h * (0.62f + 0.06f * sin(a * 0.9).toFloat()),
        radius = span * 0.66f,
        color = tints[2],
        a = alpha * 0.55f,
    )
}

/** A single soft halo, for the player's artwork. Breathes while [active]. */
@Composable
fun SoftGlow(
    color: Color,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    alpha: Float = 0.30f,
) {
    val breathe = if (LocalReducedMotion.current || !active) {
        0.5f
    } else {
        val t = rememberInfiniteTransition(label = "glow")
        val v by t.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(5400, easing = LinearEasing), RepeatMode.Reverse),
            label = "breathe",
        )
        v
    }
    val a = alpha * (0.72f + 0.28f * breathe)
    Box(
        modifier
            .graphicsLayer { this.alpha = if (active) 1f else 0.55f }
            .background(
                Brush.radialGradient(
                    colors = listOf(color.copy(alpha = a), color.copy(alpha = 0f)),
                )
            )
    )
}

// -------------------------------------------------------------------- press

/**
 * Names a control that has no text of its own.
 *
 * A `Glyph` is drawn on a `Canvas`, so to a screen reader an icon button is an
 * unlabelled "button". `contentDescription` gives it the action, and `Role.Button`
 * makes TalkBack say what it is — including the "double tap to activate" hint.
 * Applied after `clickable`, so the click action itself is not replaced.
 */
fun Modifier.iconLabel(label: String): Modifier =
    if (label.isEmpty()) this else semantics {
        contentDescription = label
        role = Role.Button
    }

/**
 * A clickable that shrinks under the finger.
 *
 * `clickable` alone gives a ripple on release; by then the eye has already moved
 * on. The scale is the acknowledgement — 3 % is enough to feel, small enough
 * that a grid of cards does not visibly wobble as you tap through it.
 *
 * [label] is for controls whose only child is an icon. Rows with visible text
 * leave it empty: TalkBack reads the text, and repeating it would be noise.
 */
@Composable
fun Modifier.tapable(
    enabled: Boolean = true,
    scale: Float = 0.965f,
    label: String = "",
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val target = if (pressed && enabled && !reduced) scale else 1f
    val s by animateFloatAsState(target, BunSpring.snappy(), label = "press")
    return this
        .graphicsLayer {
            scaleX = s
            scaleY = s
        }
        .clickable(
            interactionSource = source,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick,
        )
        .iconLabel(label)
}

/**
 * The same press feedback for a row that also answers a long press.
 *
 * A full-width row gets a smaller scale than a card: 3.5 % of 360 dp is twelve
 * pixels, and a list that visibly shortens under the thumb looks broken rather
 * than responsive.
 */
@Composable
fun Modifier.longTapable(
    enabled: Boolean = true,
    scale: Float = 0.988f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val s by animateFloatAsState(
        targetValue = if (pressed && enabled && !reduced) scale else 1f,
        animationSpec = BunSpring.snappy(),
        label = "rowPress",
    )
    return this
        .graphicsLayer {
            scaleX = s
            scaleY = s
        }
        .combinedClickable(
            interactionSource = source,
            indication = LocalIndication.current,
            enabled = enabled,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

// ------------------------------------------------------------------ reveal

/**
 * Fade and rise, once, on first composition.
 *
 * [index] is the position in whatever list the caller is drawing; rows are
 * capped at [BunStagger.CAP] so a long list never waits on its own animation.
 */
@Composable
fun Modifier.reveal(
    index: Int = 0,
    distance: Dp = 12.dp,
    durationMs: Int = 300,
): Modifier {
    if (LocalReducedMotion.current) return this
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(BunStagger.delayFor(index).toLong())
        shown = true
    }
    val t by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMs, easing = BunEase.Emphasised),
        label = "reveal",
    )
    return this.graphicsLayer {
        alpha = t
        translationY = (1f - t) * distance.toPx()
    }
}

// ------------------------------------------------------------------ numbers

/**
 * A number that counts to its value instead of appearing at it.
 *
 * Used where the number is the point — a year in review, a storage total, a play
 * count. It is deliberately not used for a clock: a timer that rolls looks like
 * a bug.
 */
@Composable
fun AnimatedCount(
    value: Int,
    color: Color = Ink.Text,
    size: Int = 16,
    weight: FontWeight = FontWeight.Bold,
    family: FontFamily = SoraFamily,
    suffix: String = "",
    modifier: Modifier = Modifier,
) {
    val v by animateIntAsState(
        targetValue = value,
        animationSpec = tween(if (LocalReducedMotion.current) 0 else 760, easing = BunEase.Emphasised),
        label = "count",
    )
    Text(
        text = groupDigits(v) + suffix,
        color = color,
        fontSize = size.sp,
        fontFamily = family,
        fontWeight = weight,
        modifier = modifier,
    )
}

/** 1234567 → "1,234,567". Indian grouping is not used: this is a count, not money. */
fun groupDigits(value: Int): String {
    val negative = value < 0
    val digits = kotlin.math.abs(value).toString()
    val out = StringBuilder()
    for ((i, c) in digits.withIndex()) {
        if (i > 0 && (digits.length - i) % 3 == 0) out.append(',')
        out.append(c)
    }
    return if (negative) "-$out" else out.toString()
}

// ------------------------------------------------------------------- button

/**
 * The play button, which is the one control a music app is judged by.
 *
 * The glyph swaps with a scale-and-fade rather than a cut, the disc dips under
 * the thumb, and the haptic fires on press instead of on release — the same
 * order a physical button works in.
 */
@Composable
fun MorphPlayButton(
    playing: Boolean,
    tint: Color,
    ink: Color,
    size: Dp = 66.dp,
    glyphSize: Dp = 26.dp,
    busy: Boolean = false,
    stroke: Dp = 2.0.dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val press by animateFloatAsState(
        targetValue = if (pressed && !reduced) 0.92f else 1f,
        animationSpec = BunSpring.snappy(),
        label = "press",
    )
    // The wave while buffering is not a third state of the button: it is the
    // same button saying "hold on". It pulses so it reads as activity, not as a
    // glyph the user is meant to press.
    val glyph = when {
        busy && !playing -> G.Wave
        playing -> G.Pause
        else -> G.Play
    }
    // The label is the *action*, not the picture: while playing it must say
    // "Pause", and while buffering it must not claim to be a play button.
    val label = Labels.transport(playing, busy && !playing)
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }
            .clip(CircleShape)
            .background(tint)
            .clickable(
                interactionSource = source,
                indication = LocalIndication.current,
            ) {
                Haptics.tick()
                onClick()
            }
            .iconLabel(label),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = glyph,
            transitionSpec = {
                (
                    fadeIn(tween(if (reduced) 0 else 130)) +
                        scaleIn(
                            initialScale = 0.5f,
                            animationSpec = if (reduced) tween(0) else BunSpring.bouncy(),
                        )
                    ) togetherWith (
                    fadeOut(tween(if (reduced) 0 else 100)) +
                        scaleOut(targetScale = 0.5f, animationSpec = tween(if (reduced) 0 else 100))
                    )
            },
            label = "morph",
        ) { g ->
            Glyph(g, ink, glyphSize, stroke)
        }
    }
}

/**
 * One glyph becoming another, in place.
 *
 * Repeat has two drawings and three modes; like has an empty heart and a full
 * one. Swapping them in a single frame reads as a repaint — the eye is given no
 * reason to look. A short cross-spring makes the change itself the feedback,
 * which is the whole trick behind Rive's state machines, done here in about
 * fifteen lines with no runtime and no asset.
 *
 * The tint crossfades too, so a control that goes from grey to accent does not
 * flash through an intermediate colour on the way.
 */
@Composable
fun MorphIcon(
    glyph: G,
    tint: Color,
    size: Dp = 22.dp,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val ink by animateColorAsState(tint, tween(if (reduced) 0 else 220), label = "ink")
    AnimatedContent(
        targetState = glyph,
        modifier = modifier.size(size),
        transitionSpec = {
            (
                fadeIn(tween(if (reduced) 0 else 150)) +
                    scaleIn(
                        initialScale = 0.62f,
                        animationSpec = if (reduced) tween(0) else BunSpring.snappy(),
                    )
                ) togetherWith (
                fadeOut(tween(if (reduced) 0 else 110)) +
                    scaleOut(targetScale = 0.62f, animationSpec = tween(if (reduced) 0 else 110))
                )
        },
        label = "iconMorph",
    ) { g ->
        Glyph(g, ink, size)
    }
}

// ------------------------------------------------------------------- rules
/** A hairline that fades in from both ends. A full-width rule cuts a page in two. */
@Composable
fun GradientRule(modifier: Modifier = Modifier, height: Dp = 1.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Ink.Hairline, Color.Transparent)
                )
            )
    )
}
