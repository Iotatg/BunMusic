package app.bunmusic.core

/**
 * Fold a caller-supplied number into something finite before clamping it.
 *
 * Kotlin's `coerceIn` / `coerceAtLeast` / `coerceAtMost` return NaN unchanged —
 * both comparisons against NaN are false — and `Float.toInt()` then quietly
 * turns NaN into 0. A stray NaN therefore slips through every clamp and ends up
 * as an alpha, a radius or a coordinate of NaN: a pane that silently fails to
 * paint, a shape with undefined corners, or a highlight at an undefined place.
 *
 * This is the single shared guard for the numeric helpers in `core/`. Every
 * public entry reduces non-finite input to a safe finite value first. The normal
 * path (finite values from Compose / constants) is bit-for-bit unchanged; this
 * is purely a guard against a bad caller, a corrupt preference, or an animation
 * interrupted mid-flight.
 */
fun Float.finiteOr(fallback: Float = 0f): Float =
    if (isNaN() || isInfinite()) fallback else this

fun Double.finiteOr(fallback: Double = 0.0): Double =
    if (isNaN() || isInfinite()) fallback else this
