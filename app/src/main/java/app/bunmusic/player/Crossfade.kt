package app.bunmusic.player

/**
 * The gain curve behind the crossfade setting.
 *
 * BunMusic has one player, so a true overlap — two songs audible at once — is not
 * possible without a second engine. What *is* possible, and what most players
 * mean by the word anyway, is this: the last seconds of a song ease down, the next
 * one eases up. The result has no hard cut in it, which is the thing anyone
 * actually notices.
 *
 * Kept as a pure function so the arithmetic can be tested: [gain] returns the
 * multiplier for the current position, and everything about the player is
 * somewhere else.
 */
object Crossfade {

    /**
     * Smoothstep. A linear ramp has a corner at both ends that the ear hears as a
     * small click; `t²(3-2t)` leaves and arrives at zero slope.
     */
    private fun ease(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    /**
     * @param fadeMs length of the fade at each end; 0 disables it.
     * @return 0..1 gain. 1 means "untouched".
     */
    fun gain(positionMs: Long, durationMs: Long, fadeMs: Long): Float {
        if (fadeMs <= 0L || durationMs <= 0L || positionMs < 0L) return 1f
        // On a track shorter than two fades, halve both so they cannot overlap and
        // cancel each other into silence.
        val f = if (durationMs < fadeMs * 2) durationMs / 2 else fadeMs
        if (f <= 0L) return 1f
        if (positionMs >= durationMs - f) {
            return ease(((durationMs - positionMs).toFloat() / f).coerceIn(0f, 1f))
        }
        if (positionMs < f) return ease(positionMs.toFloat() / f)
        return 1f
    }
}
