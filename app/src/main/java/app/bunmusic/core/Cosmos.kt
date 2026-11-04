package app.bunmusic.core

/**
 * The arithmetic behind the night sky.
 *
 * Pure functions on purpose: the star field is drawn on a Canvas, but *which*
 * stars exist is decided here, where a JVM test can check that the same song
 * always gets the same sky and that no star lands outside the page. The drawing
 * code in `ui/Cosmos.kt` is then only responsible for paint.
 *
 * Nothing here allocates per frame — [stars] is called once per seed and the
 * result is remembered by the composable.
 */
data class Star(
    /** 0…1 across the page. */
    val x: Float,
    /** 0…1 down the page. */
    val y: Float,
    /** Radius as a fraction of the short side: 0.0018…0.0055. */
    val radius: Float,
    /** 0…1 offset into the twinkle cycle, so they do not blink in unison. */
    val phase: Float,
    /** A handful of stars are brighter and get a cross flare. */
    val bright: Boolean,
)

object Cosmos {

    /** Stars per page. Sixty is enough to read as a sky at 400 dp, and cheap. */
    const val DEFAULT_STARS = 60

    /** Stable 31-hash. Same seed in, same sky out — across launches and devices. */
    fun seed(seed: String?): Int {
        val s = seed ?: "bunmusic"
        var h = 17
        for (ch in s) h = (h * 31 + ch.code) and 0x7fffffff
        return if (h == 0) 1 else h
    }

    /**
     * A deterministic field of stars for [seed].
     *
     * xorshift rather than `Random(seed)`: `kotlin.random.Random` is explicitly
     * allowed to change its algorithm between releases, which would silently
     * reshuffle every user's sky on an update. Six lines of arithmetic do not
     * have that problem.
     */
    fun stars(seed: String?, count: Int = DEFAULT_STARS): List<Star> {
        if (count <= 0) return emptyList()
        var s = seed(seed)
        fun next(): Float {
            s = s xor (s shl 13)
            s = s xor (s ushr 17)
            s = s xor (s shl 5)
            s = s and 0x7fffffff
            return (s % 10_000) / 10_000f
        }
        return List(count) { i ->
            Star(
                x = next(),
                y = next(),
                // Squared distribution: small stars are common, big ones rare.
                radius = 0.0018f + next() * next() * 0.0037f,
                phase = next(),
                bright = i % 11 == 0,
            )
        }
    }

    /**
     * Brightness of one star at time [t] (0…1, looping).
     *
     * A raised cosine, not a sine: it holds near full brightness for most of the
     * cycle and dips briefly, which is what a star does. A plain sine makes the
     * whole sky pulse like a slow strobe.
     */
    fun twinkle(phase: Float, t: Float): Float {
        val p = (t + phase) % 1f
        val wave = (1f + kotlin.math.cos((p * 2f * Math.PI).toFloat())) * 0.5f
        return 0.55f + 0.45f * (wave * wave)
    }

    /**
     * Where the crescent sits, as a fraction of the page: a third in from the
     * left, an eighth down, sized against the short side.
     */
    data class Moon(val cx: Float, val cy: Float, val radius: Float)

    fun moon(): Moon = Moon(cx = 0.20f, cy = 0.13f, radius = 0.20f)
}
