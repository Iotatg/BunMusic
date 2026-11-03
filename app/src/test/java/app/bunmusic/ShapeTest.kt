package app.bunmusic

import app.bunmusic.core.BunShape
import app.bunmusic.core.CornerRadii
import app.bunmusic.core.Shapes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shape as arithmetic: four fractions, interpolated and chosen deterministically. */
class ShapeTest {

    private fun assertClose(expected: CornerRadii, actual: CornerRadii) {
        assertEquals(expected.topStart, actual.topStart, 0.001f)
        assertEquals(expected.topEnd, actual.topEnd, 0.001f)
        assertEquals(expected.bottomEnd, actual.bottomEnd, 0.001f)
        assertEquals(expected.bottomStart, actual.bottomStart, 0.001f)
    }

    @Test
    fun `every shape stays inside a half circle`() {
        BunShape.entries.forEach { s ->
            val r = Shapes.radii(s)
            listOf(r.topStart, r.topEnd, r.bottomEnd, r.bottomStart).forEach {
                assertTrue("${s.label} produced $it", it in 0f..0.5f)
            }
        }
    }

    @Test
    fun `a shape scales with its container`() {
        val small = Shapes.radii(BunShape.ROUND, 48f)
        val large = Shapes.radii(BunShape.ROUND, 480f)
        assertEquals(small.topStart * 10f, large.topStart, 0.01f)
    }

    @Test
    fun `morph ends exactly on both shapes`() {
        val a = Shapes.radii(BunShape.ARCH)
        val b = Shapes.radii(BunShape.BLOCK)
        assertClose(a, Shapes.morph(a, b, 0f))
        assertClose(b, Shapes.morph(a, b, 1f))
    }

    @Test
    fun `morph halfway sits between the corners`() {
        val a = CornerRadii(0.4f, 0.0f, 0.2f, 0.2f)
        val b = CornerRadii(0.0f, 0.4f, 0.2f, 0.2f)
        val mid = Shapes.morph(a, b, 0.5f)
        assertEquals(0.2f, mid.topStart, 0.001f)
        assertEquals(0.2f, mid.topEnd, 0.001f)
        // Corners that already matched must not drift.
        assertEquals(0.2f, mid.bottomEnd, 0.001f)
    }

    @Test
    fun `morph clamps outside the range`() {
        val a = Shapes.radii(BunShape.ROUND)
        val b = Shapes.radii(BunShape.LEAF)
        assertClose(a, Shapes.morph(a, b, -3f))
        assertClose(b, Shapes.morph(a, b, 4f))
    }

    @Test
    fun `a cover keeps its shape forever`() {
        assertEquals(Shapes.pick("yt:dQw4w9WgXcQ"), Shapes.pick("yt:dQw4w9WgXcQ"))
        assertEquals(BunShape.ROUND, Shapes.pick(null))
        assertEquals(BunShape.ROUND, Shapes.pick(""))
    }

    @Test
    fun `neighbouring covers are not all the same silhouette`() {
        val seeds = (0 until 40).map { Shapes.pick(it) }
        assertTrue("only ${seeds.toSet().size} distinct shapes", seeds.toSet().size >= 4)
        // And not a strict cycle either: two in a row may match, but rarely.
        val repeats = seeds.zipWithNext().count { (a, b) -> a == b }
        assertTrue("$repeats identical neighbours", repeats < 14)
    }

    @Test
    fun `the nearest shape follows a dragged corner`() {
        assertEquals(BunShape.BLOCK, Shapes.nearest(0f))
        assertTrue(Shapes.sharpness(Shapes.nearest(0.5f)) > 0.9f)
        assertNotEquals(BunShape.PILL, Shapes.nearest(0.1f))
    }

    @Test
    fun `an unknown stored id falls back instead of throwing`() {
        assertEquals(BunShape.ROUND, BunShape.byId("nonsense"))
        assertEquals(BunShape.LEAF, BunShape.byId("leaf"))
        assertEquals(BunShape.ROUND, BunShape.byId(null))
    }

    @Test
    fun `sharpness is a 0 to 1 dial`() {
        assertEquals(0.04f, Shapes.sharpness(BunShape.BLOCK), 0.01f)
        assertEquals(1f, Shapes.sharpness(BunShape.PILL), 0.001f)
    }
}
