package app.bunmusic

import app.bunmusic.core.Cosmos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sky is generated, so it can be checked without a display.
 *
 * These tests exist because a star field is exactly the kind of thing that
 * silently breaks: a change of hash, a star at x = 1.02, a twinkle that dips to
 * zero and reads as a dead pixel. None of that throws — it just looks wrong.
 */
class CosmosTest {

    @Test
    fun theSameSongAlwaysGetsTheSameSky() {
        val a = Cosmos.stars("dQw4w9WgXcQ", 40)
        val b = Cosmos.stars("dQw4w9WgXcQ", 40)
        assertEquals(a, b)
        // ...and a different song gets a different one, or every track would look
        // like the same night.
        assertNotEquals(a, Cosmos.stars("kQ2l8mN0pQr", 40))
    }

    @Test
    fun aMissingSeedStillGetsASky() {
        assertEquals(Cosmos.stars(null, 12), Cosmos.stars(null, 12))
        assertEquals(12, Cosmos.stars(null, 12).size)
        assertEquals(0, Cosmos.stars("x", 0).size)
    }

    @Test
    fun everyStarIsOnThePage() {
        val stars = Cosmos.stars("bounds", 500)
        stars.forEach { s ->
            assertTrue("x ${s.x}", s.x in 0f..1f)
            assertTrue("y ${s.y}", s.y in 0f..1f)
            assertTrue("phase ${s.phase}", s.phase in 0f..1f)
            // A radius of zero is an invisible star; anything above 1 % of the
            // short side is a planet.
            assertTrue("radius ${s.radius}", s.radius > 0.001f && s.radius < 0.006f)
        }
    }

    @Test
    fun brightStarsAreRare() {
        val stars = Cosmos.stars("bright", 110)
        val bright = stars.count { it.bright }
        assertTrue("every star flared", bright < stars.size / 5)
        assertTrue("no star flares", bright > 0)
    }

    @Test
    fun twinkleNeverGoesDark() {
        // A star that reaches zero looks like a dead pixel for a frame, and on a
        // 60-star field the eye catches it.
        for (i in 0..200) {
            val v = Cosmos.twinkle(i / 200f, (i * 7 % 200) / 200f)
            assertTrue("twinkle $v", v in 0.549f..1.001f)
        }
    }

    @Test
    fun twinkleLoopsCleanly() {
        val atZero = Cosmos.twinkle(0.3f, 0f)
        val atOne = Cosmos.twinkle(0.3f, 1f)
        assertEquals(atZero, atOne, 0.0001f)
    }

    @Test
    fun theSeedIsNeverZero() {
        // xorshift with a zero state stays zero forever: one blank sky, no crash.
        assertNotEquals(0, Cosmos.seed(null))
        assertNotEquals(0, Cosmos.seed(""))
        assertNotEquals(0, Cosmos.seed("0"))
    }

    @Test
    fun theMoonSitsWhereTheLayoutExpectsIt() {
        val moon = Cosmos.moon()
        assertTrue(moon.cx in 0.05f..0.5f)
        assertTrue(moon.cy in 0.02f..0.4f)
        assertTrue(moon.radius in 0.1f..0.35f)
    }
}
