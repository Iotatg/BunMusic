package app.bunmusic

import app.bunmusic.core.Cosmos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The aurora's physics is pure arithmetic, so it is checked here rather than
 * trusted to a screenshot.
 *
 * The field is drawn on a Canvas, but *which* motes exist, where they go, and
 * whether the closed-form [Cosmos.at] agrees with the frame-by-frame
 * [Cosmos.step] are decidable on the JVM. The last test is the important one:
 * the UI uses `at` (because a frozen clock must still show the right sky) while
 * the integrator is the thing that would be used in a frame loop.
 */
class DustTest {

    @Test
    fun theSameSongAlwaysGetsTheSameField() {
        assertEquals(Cosmos.dust("Kesariya", 40), Cosmos.dust("Kesariya", 40))
        assertNotEquals(Cosmos.dust("Kesariya", 40), Cosmos.dust("Tum Hi Ho", 40))
        assertEquals(Cosmos.dust(null, 12), Cosmos.dust(null, 12))
        assertEquals(0, Cosmos.dust("x", 0).size)
    }

    @Test
    fun everyMoteStartsOnThePage() {
        val field = Cosmos.dust("bounds", 500)
        field.forEach { m ->
            assertTrue("x ${m.x}", m.x in 0f..1f)
            assertTrue("y ${m.y}", m.y in 0f..1f)
            assertTrue("phase ${m.phase}", m.phase in 0f..1f)
            assertTrue("radius ${m.radius}", m.radius in 0.001f..0.006f)
            assertTrue("hue ${m.hue}", m.hue in 0..2)
            // Upward drift: a mote that falls would read as rain, not starlight.
            assertTrue("vy ${m.vy}", m.vy < 0f)
        }
    }

    @Test
    fun wrapKeepsBothDirectionsOnThePage() {
        assertEquals(0.25f, Cosmos.wrap01(0.25f), 0.0001f)
        assertEquals(0.75f, Cosmos.wrap01(-0.25f), 0.0001f)
        assertEquals(0.25f, Cosmos.wrap01(2.25f), 0.0001f)
        assertEquals(0f, Cosmos.wrap01(1f), 0.0001f)
    }

    @Test
    fun aThousandFramesNeverLeaveThePage() {
        var field = Cosmos.dust("long-run", 60)
        repeat(3_000) {
            field = Cosmos.step(field, 1f / 30f)
            field.forEach { m ->
                assertTrue("x ${m.x}", m.x in 0f..1f)
                assertTrue("y ${m.y}", m.y in 0f..1f)
            }
        }
        assertEquals(60, field.size)
    }

    @Test
    fun theClosedFormAgreesWithTheIntegrator() {
        // Drive the same field for one second two ways: 60 small Euler steps,
        // and the algebraic solution the UI calls with phase × period.
        val field = Cosmos.dust("equivalence", 40)
        var stepped = field
        repeat(60) { stepped = Cosmos.step(stepped, 1f / 60f) }
        val solved = Cosmos.at(field, 1f)
        stepped.zip(solved).forEach { (a, b) ->
            assertEquals("x", a.x, b.x, 0.002f)
            assertEquals("y", a.y, b.y, 0.002f)
        }
    }

    @Test
    fun aHugeFrameCannotFlingAMote() {
        val field = Cosmos.dust("huge-frame", 30)
        val jumped = Cosmos.step(field, 100f)
        jumped.forEach { m ->
            assertTrue("vx ${m.vx}", m.vx in -0.05f..0.05f)
            assertTrue("x ${m.x}", m.x in 0f..1f)
        }
        // dt is clamped, so a 100 s frame advances no further than 0.1 s.
        assertEquals(Cosmos.step(field, 0.1f), jumped)
    }

    @Test
    fun moteAlphaNeverGoesDarkOrBurnsOut() {
        for (i in 0..200) {
            val v = Cosmos.dustAlpha(i / 200f, (i * 7 % 200) / 200f)
            assertTrue("alpha $v", v in 0.18f..0.81f)
        }
    }
}
