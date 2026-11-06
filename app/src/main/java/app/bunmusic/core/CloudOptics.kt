package app.bunmusic.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The optics behind Cloud Blue's glass, as pure arithmetic.
 *
 * Glassmorphism is easy to fake with a translucent rectangle and hard to make
 * *readable*: every surface sits on a moving sky, so the colour underneath it is
 * never known at compile time. The rules that decide how opaque a pane is, where
 * its highlight sits, and whether dark text still clears AA on the result are
 * decisions, not decoration — so they live here, in plain Kotlin, where a JVM
 * test can check them without a phone, a Compose tree, or a RenderEffect.
 *
 * Nothing in this file touches Android. The Compose layer (`ui/CloudGlass.kt`)
 * only *draws* what these functions decide.
 */
object CloudOptics {

    /** The white a pane is made of. Composited over the sky at [fillAlpha]. */
    const val GLASS_WHITE = 0xFFFFFFFF.toInt()

    /** A cool tint at the bottom of a pane — refraction into the blue sky. */
    const val GLASS_BLUE = 0xFF2E6BFF.toInt()

    /** Base opacity of a pane at rest, before elevation is applied. */
    const val BASE_FILL = 0.52f

    /** A raised pane (a sheet, a play disc) is thicker, so less see-through. */
    const val ELEVATION_GAIN = 0.26f

    /** High-contrast mode pushes panes toward opaque so text never sits on sky. */
    const val HIGH_CONTRAST_GAIN = 0.30f

    const val MAX_FILL = 0.94f
    const val MIN_FILL = 0.10f

    /**
     * Fold a caller-supplied number into something drawable before clamping it.
     *
     * Kotlin's `coerceIn` returns NaN unchanged — both comparisons against NaN
     * are false — so a stray NaN would otherwise sail through every clamp below
     * and end up as an alpha or a coordinate of NaN: a pane that silently fails
     * to paint, or a highlight drawn at an undefined position. Every public
     * entry reduces non-finite input to a safe finite value first. The normal
     * path (finite phases from Compose, finite elevations from constants) is
     * bit-for-bit unchanged; this is purely a guard against a bad caller.
     */
    private fun finite(value: Float, fallback: Float = 0f): Float =
        if (value.isNaN() || value.isInfinite()) fallback else value

    /**
     * How opaque a pane is, 0…1.
     *
     * Three inputs, all bounded: [elevation] (0 = flat card, 1 = a floating
     * sheet), [highContrast] (the accessibility switch), and [reduce] (how much
     * the caller wants to see through it — the tiny chips stay frosted, the big
     * hero card stays legible). Everything is clamped so a caller passing a wild
     * number cannot paint a fully invisible or fully solid pane by accident.
     */
    fun fillAlpha(
        elevation: Float = 0f,
        highContrast: Boolean = false,
        reduce: Float = 0f,
    ): Float {
        val e = finite(elevation).coerceIn(0f, 1f)
        var a = BASE_FILL + ELEVATION_GAIN * e
        if (highContrast) a += HIGH_CONTRAST_GAIN
        a -= finite(reduce).coerceIn(0f, 1f) * 0.30f
        return finite(a, BASE_FILL).coerceIn(MIN_FILL, MAX_FILL)
    }

    /** Alpha of the bright specular line along a pane's top edge. */
    fun specularAlpha(highContrast: Boolean = false): Float =
        if (highContrast) 0.95f else 0.80f

    /** Alpha of the cool rim light along a pane's bottom edge. */
    fun rimAlpha(highContrast: Boolean = false): Float =
        if (highContrast) 0.32f else 0.20f

    // ------------------------------------------------------------- sheen sweep

    /**
     * A single band of light crossing a surface, left to right, once.
     *
     * This is the Apple "Liquid Glass" specular sweep: not an everlasting shimmer
     * (which reads as a loading spinner) but one pass when a surface appears.
     * [phase] is 0…1 over the whole animation; the band is only visible inside a
     * window, so the first and last frames are clean glass with no bright stripe.
     *
     * Returns null outside the active window — the caller then draws no band at
     * all, rather than a zero-alpha one.
     */
    fun sheenSweep(phase: Float): Sheen? {
        val p = finite(phase).coerceIn(0f, 1f)
        val start = 0.08f
        val end = 0.82f
        if (p < start || p > end) return null
        val t = (p - start) / (end - start)      // 0…1 inside the window
        // The band travels a little further than the pane: it enters before its
        // centre is on-screen and leaves after, so it never pops in at an edge.
        val centre = -0.25f + 1.5f * t
        // Raised-cosine envelope: bright in the middle, invisible at both ends.
        val envelope = (0.5f - 0.5f * cos(2f * PI.toFloat() * t)).coerceIn(0f, 1f)
        return Sheen(
            centre = centre,
            alpha = (0.34f * envelope).coerceIn(0f, 1f),
            width = 0.42f,
        )
    }

    data class Sheen(val centre: Float, val alpha: Float, val width: Float)

    // -------------------------------------------------------------- pool drift

    /**
     * Positions of the ambient light pools behind the glass at [phase] (0…1).
     *
     * Each pool moves on its own slow Lissajous path with an incommensurate
     * frequency pair, so the sky never visibly loops in place. Deterministic:
     * the same phase always gives the same sky, which is what lets a screenshot
     * test freeze the clock and still capture the frame that belongs to it.
     * (Same closed-form idea as [Cosmos.at], but for light, not particles.)
     */
    fun poolCentres(phase: Float, count: Int = POOL_COUNT): List<Pool> {
        val p = finite(phase).coerceIn(0f, 1f)
        val t = p * 2f * PI.toFloat()
        val n = count.coerceIn(0, 8)
        return (0 until n).map { i ->
            val k = 1f + i * 0.63f
            // Different frequency ratios per pool; amplitudes keep centres in
            // roughly the middle 70 % so a pool never leaves a hard edge on-screen.
            val x = 0.5f + 0.30f * sin(t / k + i * 1.7f)
            val y = 0.5f + 0.24f * cos(t * (0.7f + i * 0.11f) + i * 2.3f)
            Pool(
                x = x.coerceIn(0.05f, 0.95f),
                y = y.coerceIn(0.05f, 0.95f),
                radius = 0.62f - i * 0.06f,
                alpha = (0.16f - i * 0.02f).coerceAtLeast(0.04f),
                tint = i % 3,
            )
        }
    }

    const val POOL_COUNT = 4

    data class Pool(
        val x: Float,
        val y: Float,
        val radius: Float,
        val alpha: Float,
        /** 0 = blue, 1 = white, 2 = cyan — index into the backdrop's tints. */
        val tint: Int,
    )

    // -------------------------------------------------------------- compositing

    /**
     * Source-over composite in sRGB of [overlay] at [alpha] onto opaque
     * [background]. Both are 0xAARRGGBB; the result is opaque.
     *
     * This is the same linear-in-channel blend as [Contrast.blend], kept here so
     * the glass can predict the *exact* colour a caption will sit on before it
     * paints anything. Averaging luminance instead — the common shortcut — is
     * visibly wrong on the blue tint.
     */
    fun composite(background: Int, overlay: Int, alpha: Float): Int {
        val a = finite(alpha).coerceIn(0f, 1f)
        fun ch(shift: Int): Int {
            val b = (background shr shift) and 0xFF
            val o = (overlay shr shift) and 0xFF
            return (o * a + b * (1f - a)).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /**
     * Contrast ratio of [text] on a glass pane over [background], using the real
     * [fillAlpha] decision. The whole point: glass must never push a caption
     * below AA just because the sky behind it drifted. Delegates the WCAG maths
     * to [Contrast] so there is one definition of a ratio in this app.
     */
    fun glassContrast(
        background: Int,
        text: Int,
        elevation: Float = 0f,
        highContrast: Boolean = false,
    ): Double {
        val a = fillAlpha(elevation, highContrast)
        val surface = composite(background, GLASS_WHITE, a)
        return Contrast.ratio(text, surface)
    }
}
