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

/**
 * One mote of drifting starlight.
 *
 * Unlike [Star] this has a velocity and an acceleration: the aurora's dust
 * actually moves. Positions are page fractions (0…1) so the same field works on
 * a 411 dp phone and a 1200 dp tablet, and the integrator in [Cosmos.step] is
 * pure arithmetic that a JVM test can check without a Canvas.
 *
 * Modelled on the Euler integration in PiotrPrus/ParticleEmitter
 * (`v += a·dt`, `p += v·dt`, wrap at the edge), reduced to the four fields a
 * background needs. See `ui/Cosmos.kt` for the paint.
 */
data class Dust(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val ax: Float,
    val ay: Float,
    /** Radius as a fraction of the short side. */
    val radius: Float,
    /** 0…1 offset into the twinkle cycle. */
    val phase: Float,
    /** 0 violet, 1 cyan, 2 rose — indexes the aurora palette. */
    val hue: Int,
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

    /**
     * Motes per page. Forty is enough to read as depth behind a card, cheap
     * enough to draw every frame on a 2017 phone.
     */
    const val DEFAULT_DUST = 40

    /**
     * A deterministic field of drifting motes for [seed].
     *
     * The song picks the sky, so opening the same track always opens the same
     * night — but the motes are not welded to it: [step] moves them, with wrap
     * at the edges so the field never empties out.
     */
    fun dust(seed: String?, count: Int = DEFAULT_DUST): List<Dust> {
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
            Dust(
                x = next(),
                y = next(),
                // A slow upward drift, so the sky breathes rather than spins.
                vx = (next() - 0.5f) * 0.010f,
                vy = -(0.004f + next() * 0.016f),
                // A whisper of sideways acceleration gives each mote a curved
                // path. Clamped in [step], so a long frame cannot fling one off.
                ax = (next() - 0.5f) * 0.004f,
                ay = 0f,
                radius = 0.0016f + next() * next() * 0.0038f,
                phase = next(),
                hue = i % 3,
            )
        }
    }

    /**
     * Advance the field by [dt] seconds.
     *
     * Semi-implicit Euler, the same two lines the particle emitter uses:
     * velocity first, then position. Wrapping (rather than bouncing or killing)
     * keeps a fixed particle count forever, which is what a background wants —
     * no allocation, no emission schedule, no gap in the sky.
     */
    fun step(field: List<Dust>, dt: Float): List<Dust> {
        // dt <= 0f is false for NaN, and coerceAtMost leaves NaN unchanged —
        // a non-finite delta would turn every mote coordinate into NaN.
        val safeDt = dt.finiteOr()
        if (safeDt <= 0f) return field
        val d = safeDt.coerceAtMost(0.1f)
        return field.map { p ->
            val nvx = (p.vx + p.ax * d).coerceIn(-0.05f, 0.05f)
            val nvy = (p.vy + p.ay * d).coerceIn(-0.05f, 0.05f)
            p.copy(
                x = wrap01(p.x + nvx * d),
                y = wrap01(p.y + nvy * d),
                vx = nvx,
                vy = nvy,
            )
        }
    }

    /**
     * The same field at absolute time [seconds], in closed form.
     *
     * [step] is the per-frame integrator; this is its algebraic solution for
     * constant acceleration (`x = x₀ + v₀t + ½at²`). The UI drives the sky from
     * an infinite transition's phase rather than a frame loop, so a paused
     * clock, a screenshot test or "reduce motion" still shows the *correct*
     * sky for that instant instead of a frozen first frame. [DustTest] asserts
     * the two agree.
     */
    fun at(field: List<Dust>, seconds: Float): List<Dust> {
        val t = seconds.finiteOr()
        return field.map { p ->
            p.copy(
                x = wrap01(p.x + p.vx * t + 0.5f * p.ax * t * t),
                y = wrap01(p.y + p.vy * t),
                vx = (p.vx + p.ax * t).finiteOr().coerceIn(-0.05f, 0.05f),
            )
        }
    }

    /** Wrap a page fraction into 0…1, including negative drift. */
    fun wrap01(v: Float): Float {
        // NaN % 1f is NaN, and both comparisons are false — guard before wrapping.
        val r = v.finiteOr() % 1f
        return if (r < 0f) r + 1f else r
    }

    /** Alpha of a mote at time [t]; the raised cosine from [twinkle], softened. */
    fun dustAlpha(phase: Float, t: Float): Float = 0.18f + 0.62f * twinkle(phase, t)
}
