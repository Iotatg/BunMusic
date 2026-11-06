package app.bunmusic

import app.bunmusic.core.CloudOptics
import app.bunmusic.core.Contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Cloud Blue's glass, decided by arithmetic and checked on the JVM.
 *
 * Before 2.11.0 a "glass" skin was a solid white card: there was no translucency
 * to reason about, so nothing could be tested. Now every pane sits on a drifting
 * sky whose colour is unknown at compile time. The rules that keep a caption
 * readable on that moving surface — how opaque the pane is, where its highlight
 * lands, which colour a caption actually sits on — are decisions, not decoration,
 * and they live in [CloudOptics] with no Android in them. This suite is the proof
 * that they hold: no phone, no Compose tree, no RenderEffect required.
 */
class CloudGlassTest {

    // Cloud's own palette, mirrored here so a palette regression is caught by a
    // contrast failure rather than discovered by eye on a screenshot.
    private val skyVoid = 0xFFE9EFFA.toInt()
    private val skyBase = 0xFFF3F6FC.toInt()
    private val skyRaised = 0xFFFFFFFF.toInt()
    private val heroDark = 0xFF2459E8.toInt()
    private val heroMid = 0xFF2E6BFF.toInt()
    private val heroLight = 0xFF4B82FF.toInt()
    private val accent = 0xFF2560EC.toInt()
    private val mint = 0xFF12B48A.toInt()
    private val glassWhite = 0xFFFFFFFF.toInt()
    private val darkText = 0xFF0E1A33.toInt()

    private val skies = listOf(
        skyVoid, skyBase, skyRaised, heroDark, heroMid, heroLight, accent, mint,
    )

    // ------------------------------------------------------------------ fillAlpha

    @Test
    fun `a flat pane rests at base opacity`() {
        assertEquals(CloudOptics.BASE_FILL, CloudOptics.fillAlpha(), 1e-6f)
    }

    @Test
    fun `elevation thickens a pane and high contrast thickens it further`() {
        val flat = CloudOptics.fillAlpha(elevation = 0f)
        val raised = CloudOptics.fillAlpha(elevation = 1f)
        val highContrast = CloudOptics.fillAlpha(elevation = 0f, highContrast = true)
        assertTrue("a raised sheet must be thicker than a flat card", raised > flat)
        assertTrue("high contrast must push a pane toward opaque", highContrast > flat)
        assertEquals(
            CloudOptics.BASE_FILL + CloudOptics.ELEVATION_GAIN,
            raised,
            1e-6f,
        )
    }

    @Test
    fun `reduce makes a pane more see-through but never invisible`() {
        val seeThrough = CloudOptics.fillAlpha(reduce = 1f)
        assertTrue(seeThrough < CloudOptics.fillAlpha())
        assertTrue(seeThrough >= CloudOptics.MIN_FILL)
    }

    @Test
    fun `wild inputs cannot paint a fully invisible or fully solid pane`() {
        // A caller passing a wild number is a bug we contain here, not a pane we
        // let disappear. Elevation, reduce and the contrast flag are all bounded.
        val cases = listOf(
            CloudOptics.fillAlpha(elevation = -50f),
            CloudOptics.fillAlpha(elevation = 50f),
            CloudOptics.fillAlpha(reduce = -50f),
            CloudOptics.fillAlpha(reduce = 50f),
            CloudOptics.fillAlpha(elevation = 50f, highContrast = true),
            CloudOptics.fillAlpha(elevation = 50f, highContrast = true, reduce = 50f),
            CloudOptics.fillAlpha(elevation = Float.NaN),
        )
        cases.forEach { a ->
            assertTrue("alpha $a below MIN_FILL", a >= CloudOptics.MIN_FILL)
            assertTrue("alpha $a above MAX_FILL", a <= CloudOptics.MAX_FILL)
        }
    }

    @Test
    fun `non-finite input never becomes a NaN pane or a stray highlight`() {
        // Kotlin's coerceIn returns NaN unchanged (both comparisons are false),
        // so without the finite() guard these would all leak a NaN alpha or a NaN
        // centre into a draw call. Every public entry must stay finite.
        val nan = Float.NaN
        val inf = Float.POSITIVE_INFINITY
        listOf(nan, inf, Float.NEGATIVE_INFINITY).forEach { bad ->
            val fill = CloudOptics.fillAlpha(elevation = bad, reduce = bad)
            assertTrue("fillAlpha($bad)=$fill below MIN", fill >= CloudOptics.MIN_FILL)
            assertTrue("fillAlpha($bad)=$fill above MAX", fill <= CloudOptics.MAX_FILL)
            // A composite of a non-finite alpha is still opaque and in gamut.
            val out = CloudOptics.composite(heroMid, glassWhite, bad)
            assertEquals(0xFF, (out shr 24) and 0xFF)
            for (shift in intArrayOf(16, 8, 0)) {
                val c = (out shr shift) and 0xFF
                assertTrue("channel $c out of gamut", c in 0..255)
            }
        }
        // A NaN/Inf phase must resolve to a clean, deterministic frame.
        assertNull(CloudOptics.sheenSweep(nan))
        assertNull(CloudOptics.sheenSweep(inf))
        val pools = CloudOptics.poolCentres(nan)
        assertEquals(CloudOptics.POOL_COUNT, pools.size)
        assertEquals(pools, CloudOptics.poolCentres(0f))
        pools.forEach { pool ->
            assertTrue(pool.x in 0f..1f)
            assertTrue(pool.y in 0f..1f)
        }
    }

    @Test
    fun `the specular and rim alphas are bounded and high contrast lifts them`() {
        assertTrue(CloudOptics.specularAlpha() <= 1f)
        assertTrue(CloudOptics.rimAlpha() <= 1f)
        assertTrue(CloudOptics.specularAlpha(true) >= CloudOptics.specularAlpha())
        assertTrue(CloudOptics.rimAlpha(true) >= CloudOptics.rimAlpha())
        // The top edge is always brighter than the cool bottom rim.
        assertTrue(CloudOptics.specularAlpha() > CloudOptics.rimAlpha())
    }

    // ---------------------------------------------------------------- sheenSweep

    @Test
    fun `the first and last frames are clean glass with no bright stripe`() {
        assertNull(CloudOptics.sheenSweep(0f))
        assertNull(CloudOptics.sheenSweep(1f))
        assertNull(CloudOptics.sheenSweep(-1f))   // out-of-range clamps to a null window
        assertNull(CloudOptics.sheenSweep(2f))
    }

    @Test
    fun `the band fades in and out at the window edges`() {
        val start = CloudOptics.sheenSweep(0.08f)
        val end = CloudOptics.sheenSweep(0.82f)
        val middle = CloudOptics.sheenSweep(0.45f)
        assertNotNull(start)
        assertNotNull(end)
        assertNotNull(middle)
        // Raised-cosine envelope: the edges are (near) zero, the middle is bright.
        assertTrue(start!!.alpha < 0.01f)
        assertTrue(end!!.alpha < 0.01f)
        assertTrue(middle!!.alpha > start.alpha)
        assertTrue(middle.alpha > end.alpha)
        assertEquals(0.34f, middle.alpha, 1e-4f)
    }

    @Test
    fun `the band travels left to right exactly once`() {
        var lastCentre = Float.NEGATIVE_INFINITY
        var lastAlpha = -1f
        var peaked = false
        var samples = 0
        var p = 0.08f
        while (p <= 0.82f) {
            val sh = CloudOptics.sheenSweep(p)
            assertNotNull("no band at p=$p", sh)
            sh!!
            assertTrue("centre must move right monotonically at p=$p", sh.centre >= lastCentre)
            lastCentre = sh.centre
            // Envelope rises to a single peak then falls.
            if (sh.alpha < lastAlpha - 1e-6f) peaked = true
            // Once falling, it must never rise again (one hump, not a shimmer).
            if (peaked) assertTrue("second rise at p=$p", sh.alpha <= lastAlpha + 1e-6f)
            lastAlpha = sh.alpha
            assertTrue("alpha out of range at p=$p", sh.alpha in 0f..1f)
            assertTrue("width must be positive", sh.width > 0f)
            samples++
            p += 0.01f
        }
        assertTrue("expected many samples, got $samples", samples >= 70)
        assertTrue("the envelope must peak and fall within the window", peaked)
        assertTrue("band should enter from the left", CloudOptics.sheenSweep(0.08f)!!.centre < 0f)
        assertTrue("band should exit to the right", CloudOptics.sheenSweep(0.82f)!!.centre > 1f)
    }

    @Test
    fun `a phase outside the unit range is clamped, not wrapped into a stray band`() {
        // Negative phases must not alias to a bright late frame; >1 must not alias
        // to a bright early frame.
        assertNull(CloudOptics.sheenSweep(-0.5f))
        assertNull(CloudOptics.sheenSweep(1.5f))
    }

    // --------------------------------------------------------------- poolCentres

    @Test
    fun `the same phase always gives the same sky`() {
        // Deterministic closed-form motion is what lets a frozen-clock screenshot
        // capture the one frame that belongs to it, on every device.
        val a = CloudOptics.poolCentres(0.37f)
        val b = CloudOptics.poolCentres(0.37f)
        assertEquals(a, b)
    }

    @Test
    fun `different phases give a different sky`() {
        val a = CloudOptics.poolCentres(0.10f)
        val b = CloudOptics.poolCentres(0.60f)
        assertTrue(a != b)
    }

    @Test
    fun `pools stay inside the frame and their alpha is bounded`() {
        for (phase in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            CloudOptics.poolCentres(phase).forEach { pool ->
                assertTrue("x out of frame at $phase: ${pool.x}", pool.x in 0f..1f)
                assertTrue("y out of frame at $phase: ${pool.y}", pool.y in 0f..1f)
                assertTrue("alpha negative at $phase", pool.alpha >= 0f)
                assertTrue("alpha too strong at $phase", pool.alpha <= 0.30f)
                assertTrue("radius must be positive", pool.radius > 0f)
                assertTrue("tint out of range", pool.tint in 0..2)
            }
        }
    }

    @Test
    fun `pool count is clamped to a sane ceiling`() {
        assertEquals(CloudOptics.POOL_COUNT, CloudOptics.poolCentres(0.5f).size)
        assertTrue(CloudOptics.poolCentres(0.5f, count = 0).isEmpty())
        assertTrue(CloudOptics.poolCentres(0.5f, count = -3).isEmpty())
        assertTrue(CloudOptics.poolCentres(0.5f, count = 100).size <= 8)
    }

    // ----------------------------------------------------------------- composite

    @Test
    fun `composite is source-over with clean endpoints`() {
        // alpha 0 = the untouched sky; alpha 1 = the pure overlay.
        assertEquals(heroDark, CloudOptics.composite(heroDark, glassWhite, 0f))
        assertEquals(glassWhite, CloudOptics.composite(heroDark, glassWhite, 1f))
        // Out-of-range alpha is clamped, exactly like fillAlpha.
        assertEquals(heroDark, CloudOptics.composite(heroDark, glassWhite, -1f))
        assertEquals(glassWhite, CloudOptics.composite(heroDark, glassWhite, 2f))
    }

    @Test
    fun `composite agrees with the app's one definition of a blend`() {
        // CloudOptics.composite and Contrast.blend must describe the same sRGB
        // mix, or the glass would predict a caption's background that the rest of
        // the app computes differently. Allow ±1 for per-channel int rounding.
        for (alpha in listOf(0f, 0.2f, 0.52f, 0.78f, 1f)) {
            val viaOptics = CloudOptics.composite(heroMid, glassWhite, alpha)
            val viaContrast = Contrast.blend(heroMid, glassWhite, alpha)
            for (shift in intArrayOf(16, 8, 0)) {
                val a = (viaOptics shr shift) and 0xFF
                val b = (viaContrast shr shift) and 0xFF
                assertTrue("channel drift at alpha=$alpha shift=$shift: $a vs $b", abs(a - b) <= 1)
            }
        }
    }

    @Test
    fun `composite result is always opaque`() {
        skies.forEach { sky ->
            val out = CloudOptics.composite(sky, glassWhite, CloudOptics.BASE_FILL)
            assertEquals(0xFF, (out shr 24) and 0xFF)
        }
    }

    // -------------------------------------------------------------- glassContrast

    @Test
    fun `dark captions clear WCAG AA on glass over every sky colour`() {
        // The whole point of deciding opacity in code: glass must never push a
        // caption below AA just because the sky behind it drifted. Even over the
        // deepest hero blue, a translucent white pane keeps dark text legible.
        skies.forEach { sky ->
            val ratio = CloudOptics.glassContrast(sky, darkText)
            assertTrue(
                "contrast ${"%.2f".format(ratio)} on sky ${Integer.toHexString(sky)} is below AA",
                ratio >= Contrast.AA,
            )
        }
    }

    @Test
    fun `glass lifts contrast above an unpaned caption on a dark sky`() {
        // Without the pane, dark text on the hero blue is nearly invisible. The
        // frosted fill is what makes the label readable at all.
        val bare = Contrast.ratio(darkText, heroDark)
        val paned = CloudOptics.glassContrast(heroDark, darkText)
        assertTrue("glass must improve legibility: bare=$bare paned=$paned", paned > bare)
        assertTrue(bare < Contrast.AA)
        assertTrue(paned >= Contrast.AA)
    }

    @Test
    fun `elevation and high contrast never lower legibility`() {
        skies.forEach { sky ->
            val flat = CloudOptics.glassContrast(sky, darkText, elevation = 0f)
            val raised = CloudOptics.glassContrast(sky, darkText, elevation = 1f)
            val highContrast = CloudOptics.glassContrast(sky, darkText, highContrast = true)
            assertTrue("a thicker pane must not hurt contrast on ${Integer.toHexString(sky)}", raised >= flat)
            assertTrue("high contrast must not hurt contrast on ${Integer.toHexString(sky)}", highContrast >= flat)
        }
    }

    @Test
    fun `glassContrast uses the same ratio as Contrast`() {
        // Guard against a second, subtly different definition of a contrast ratio
        // sneaking into the optics layer.
        val a = CloudOptics.fillAlpha(elevation = 0.5f)
        val surface = CloudOptics.composite(heroMid, glassWhite, a)
        val expected = Contrast.ratio(darkText, surface)
        assertEquals(expected, CloudOptics.glassContrast(heroMid, darkText, elevation = 0.5f), 1e-9)
    }
}
