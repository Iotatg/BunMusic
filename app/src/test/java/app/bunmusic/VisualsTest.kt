package app.bunmusic

import app.bunmusic.core.BunStagger
import app.bunmusic.ui.auroraTints
import app.bunmusic.ui.groupDigits
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parts of the 2.2.0 visual layer that are arithmetic rather than drawing.
 *
 * A stagger, a number formatter and a colour derivation all decide what the eye
 * sees, and all three can be wrong in ways a screenshot would not reveal: a
 * stagger that grows without a cap makes a long list feel broken, a formatter
 * that drops a digit lies about a play count, and a derivation that returns the
 * same hue three times turns the aurora into a flat wash.
 */
class VisualsTest {

    // ------------------------------------------------------------- stagger

    @Test
    fun `the first row appears immediately`() {
        assertEquals(0, BunStagger.delayFor(0))
        assertEquals(0, BunStagger.delayFor(-3))
    }

    @Test
    fun `later rows wait, but never more than the cap`() {
        assertTrue(BunStagger.delayFor(1) > 0)
        assertEquals(26, BunStagger.delayFor(1))
        assertTrue("the fourth row is still inside the ramp", BunStagger.delayFor(4) < BunStagger.CAP)
        // The whole point: the hundredth row is not slower than the tenth.
        assertEquals(BunStagger.CAP, BunStagger.delayFor(10))
        assertEquals(BunStagger.CAP, BunStagger.delayFor(100))
        assertEquals(BunStagger.CAP, BunStagger.delayFor(Int.MAX_VALUE))
    }

    @Test
    fun `the delay never goes backwards`() {
        var last = -1
        for (i in 0..40) {
            val d = BunStagger.delayFor(i)
            assertTrue("delay fell at $i: $d after $last", d >= last)
            last = d
        }
    }

    // ------------------------------------------------------------- numbers

    @Test
    fun `thousands are grouped`() {
        assertEquals("0", groupDigits(0))
        assertEquals("7", groupDigits(7))
        assertEquals("999", groupDigits(999))
        assertEquals("1,000", groupDigits(1000))
        assertEquals("12,345", groupDigits(12_345))
        assertEquals("1,234,567", groupDigits(1_234_567))
    }

    @Test
    fun `a negative count keeps its sign`() {
        assertEquals("-1,200", groupDigits(-1200))
    }

    @Test
    fun `grouping never changes the digit count`() {
        for (n in listOf(0, 1, 9, 10, 99, 100, 1001, 999_999, 1_000_000)) {
            val digits = groupDigits(n).count { it.isDigit() }
            assertEquals("$n lost a digit", n.toString().length, digits)
        }
    }

    // -------------------------------------------------------------- aurora

    @Test
    fun `the aurora is three different colours`() {
        val tints = auroraTints("dQw4w9WgXcQ", light = false)
        assertEquals(3, tints.size)
        assertNotEquals(tints[0], tints[1])
        assertNotEquals(tints[1], tints[2])
        assertNotEquals(tints[0], tints[2])
    }

    @Test
    fun `the same track always glows the same way`() {
        // Determinism is the requirement: a recomposition must not repaint the
        // backdrop a different colour.
        assertEquals(auroraTints("abc", false), auroraTints("abc", false))
        assertNotEquals(auroraTints("abc", false), auroraTints("xyz", false))
    }

    @Test
    fun `a light page gets paler tints than a dark one`() {
        val dark = auroraTints("seed", light = false)
        val light = auroraTints("seed", light = true)
        assertEquals(3, light.size)
        // Value, not hue, is what changes: the same three hues lifted toward the
        // page colour so a chalk background does not turn into a colour field.
        assertTrue(light[0].luminance() > dark[0].luminance())
    }

    @Test
    fun `an empty seed still produces a palette`() {
        assertEquals(3, auroraTints(null, false).size)
        assertEquals(3, auroraTints("", true).size)
    }
}
