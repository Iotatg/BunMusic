package app.bunmusic

import app.bunmusic.data.Selection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Picking many rows: toggle, range, all, invert, and the anchor that makes it work. */
class SelectionTest {

    private val order = listOf("a", "b", "c", "d", "e")

    @Test
    fun `a first tap selects and a second deselects`() {
        var s = Selection().toggle("b")
        assertTrue(s.has("b"))
        assertEquals(1, s.count)
        s = s.toggle("b")
        assertFalse(s.has("b"))
        assertEquals(0, s.count)
    }

    @Test
    fun `a range from the anchor takes everything between`() {
        val s = Selection().toggle("b").toggleRange("d", order)
        assertEquals(setOf("b", "c", "d"), s.ids)
    }

    @Test
    fun `a range works backwards too`() {
        val s = Selection().toggle("d").toggleRange("b", order)
        assertEquals(setOf("b", "c", "d"), s.ids)
    }

    @Test
    fun `a range keeps what was already picked`() {
        val s = Selection().toggle("a").toggle("c").toggleRange("e", order)
        assertEquals(setOf("a", "c", "d", "e"), s.ids)
    }

    @Test
    fun `a second range inside the first shrinks it`() {
        val s = Selection().toggle("a").toggleRange("e", order)
        assertEquals(5, s.count)
        val shrunk = s.toggleRange("c", order)
        // "a" to "e" was all five; asking again for "a" to "c" must not keep all
        // five — it takes that run back out.
        assertEquals(setOf("d", "e"), shrunk.ids)
        assertEquals("c", shrunk.anchor)
    }

    @Test
    fun `a range with no anchor is just a toggle`() {
        val s = Selection().toggleRange("c", order)
        assertEquals(setOf("c"), s.ids)
    }

    @Test
    fun `select all and none`() {
        assertEquals(5, Selection().all(order).count)
        assertEquals(0, Selection().all(order).none().count)
    }

    @Test
    fun `invert swaps the two halves`() {
        val s = Selection(setOf("b", "d"), "b").invert(order)
        assertEquals(setOf("a", "c", "e"), s.ids)
        assertEquals(3, s.count)
    }

    @Test
    fun `order is preserved for queueing`() {
        val s = Selection(setOf("e", "b", "d"), "b")
        assertEquals(listOf("b", "d", "e"), s.ordered(order))
    }

    @Test
    fun `filtering the list drops the rows that vanished`() {
        val s = Selection(setOf("a", "x"), "a")
        val pruned = s.prune(order)
        assertEquals(setOf("a"), pruned.ids)
        assertEquals("a", pruned.anchor)
    }

    @Test
    fun `pruning an unchanged list returns the same object`() {
        val s = Selection(setOf("a", "b"), "a")
        assertEquals(s, s.prune(order))
    }

    @Test
    fun `removing the anchor forgets the anchor`() {
        val s = Selection().toggle("c").remove("c")
        assertEquals(null, s.anchor)
    }
}
