package app.bunmusic.core

import kotlin.math.abs
import kotlin.math.pow

/**
 * Readable text over anything, including a photograph.
 *
 * The player draws titles over cover art, the share card draws over a gradient,
 * and the widgets draw over whatever the current album happens to be. In each
 * case the "right" colour depends on a number nobody computed: the luminance of
 * what is underneath. A white title on a bright cover is invisible, and a dark
 * one on a dark cover is the same problem inverted.
 *
 * This is the WCAG arithmetic — sRGB channels linearised, weighted 0.2126 /
 * 0.7152 / 0.0722 — plus the two decisions that follow from it: which ink to use,
 * and how heavy a scrim has to be before white text clears 4.5:1. Pure functions,
 * so the answer is testable and identical on every device.
 */
object Contrast {

    /** WCAG AA for body text. */
    const val AA = 4.5

    /** WCAG AA for large text — 18 pt, or 14 pt bold. */
    const val AA_LARGE = 3.0

    const val BLACK = 0xFF000000.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()

    /** One sRGB channel (0–255) to linear light (0–1). */
    fun linear(channel: Int): Double {
        val c = channel.coerceIn(0, 255) / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    /** Relative luminance of an opaque colour, 0 (black) to 1 (white). */
    fun luminance(r: Int, g: Int, b: Int): Double =
        0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)

    fun luminance(argb: Int): Double = luminance(
        (argb shr 16) and 0xFF,
        (argb shr 8) and 0xFF,
        argb and 0xFF,
    )

    /** Contrast ratio between two luminances: 1 (identical) to 21 (black on white). */
    fun ratio(l1: Double, l2: Double): Double {
        val hi = maxOf(l1, l2)
        val lo = minOf(l1, l2)
        return (hi + 0.05) / (lo + 0.05)
    }

    fun ratio(a: Int, b: Int): Double = ratio(luminance(a), luminance(b))

    /**
     * Black or white, whichever reads better on [background].
     *
     * The threshold is not 0.5: a mid-grey of 0.18 luminance already gives white
     * 4.6:1 and black 4.5:1, so the crossing sits near 0.179. Choosing by measured
     * ratio instead of by a guessed cut-off is the difference between "usually
     * right" and "right".
     */
    fun bestInk(background: Int, dark: Int = BLACK, light: Int = WHITE): Int {
        val bg = luminance(background)
        return if (ratio(bg, luminance(light)) >= ratio(bg, luminance(dark))) light else dark
    }

    /**
     * How opaque a black scrim must be before [ink] clears [target] on [background].
     *
     * Returned as 0…1. Zero means the background is already dark enough; one means
     * even a fully black overlay is not enough (which cannot happen for white ink,
     * but can for a saturated ink colour, and the caller should then switch ink).
     *
     * Solved by bisection on the blended colour rather than by a lookup table,
     * because the blend has to happen in sRGB space — averaging luminance is the
     * common mistake and it is visibly wrong on red.
     */
    fun scrimAlpha(background: Int, ink: Int = WHITE, target: Double = AA): Float {
        val bgR = (background shr 16) and 0xFF
        val bgG = (background shr 8) and 0xFF
        val bgB = background and 0xFF
        val inkLum = luminance(ink)

        fun ratioAt(alpha: Double): Double {
            val r = (bgR * (1 - alpha)).toInt()
            val g = (bgG * (1 - alpha)).toInt()
            val b = (bgB * (1 - alpha)).toInt()
            return ratio(luminance(r, g, b), inkLum)
        }

        if (ratioAt(0.0) >= target) return 0f
        if (ratioAt(1.0) < target) return 1f

        var lo = 0.0
        var hi = 1.0
        repeat(12) {
            val mid = (lo + hi) / 2
            if (ratioAt(mid) >= target) hi = mid else lo = mid
        }
        return hi.toFloat()
    }

    /**
     * Whether a pair is legible, with the ratio, for the stability and appearance
     * screens to show. Never throws.
     */
    fun check(foreground: Int, background: Int): Pair<Boolean, Double> {
        val r = ratio(foreground, background)
        return (r >= AA) to r
    }

    /** "4.7:1" — one decimal is all a contrast number ever needs. */
    fun label(foreground: Int, background: Int): String {
        val r = ratio(foreground, background)
        val rounded = (r * 10).toInt() / 10.0
        return "$rounded:1"
    }

    /**
     * A colour that reads on [background] without being pure black or white.
     *
     * Used where the design wants a tinted label (the accent colour) rather than
     * a neutral: if the accent fails the ratio, it is mixed toward the better ink
     * in even steps until it passes, or the neutral is returned.
     */
    fun readable(tint: Int, background: Int, target: Double = AA): Int {
        if (ratio(tint, background) >= target) return tint
        val ink = bestInk(background)
        var best = ink
        var bestRatio = ratio(ink, background)
        for (step in 1..9) {
            val t = step / 10.0
            val mixed = blend(tint, ink, t.toFloat())
            val r = ratio(mixed, background)
            if (r > bestRatio) {
                best = mixed
                bestRatio = r
            }
            if (r >= target) return mixed
        }
        return best
    }

    /** Linear mix in sRGB, [t] = 0 gives [a], 1 gives [b]. */
    fun blend(a: Int, b: Int, t: Float): Int {
        // coerceIn returns NaN unchanged; guard first or a NaN t yields pure black.
        val k = t.finiteOr().coerceIn(0f, 1f)
        fun ch(shift: Int): Int {
            val x = (a shr shift) and 0xFF
            val y = (b shr shift) and 0xFF
            return (x + (y - x) * k).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** True when the two colours are close enough that a scrim would not help. */
    fun similar(a: Int, b: Int, tolerance: Double = 0.06): Boolean =
        abs(luminance(a) - luminance(b)) < tolerance
}
