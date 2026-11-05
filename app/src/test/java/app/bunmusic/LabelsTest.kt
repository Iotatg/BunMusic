package app.bunmusic

import app.bunmusic.core.G
import app.bunmusic.core.Labels
import app.bunmusic.core.label
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every control that is only a drawing needs a name.
 *
 * These are the strings a screen reader speaks. They are checked here rather
 * than on a device because the failure mode is silent: an unlabelled button
 * looks perfect and says nothing, and nobody notices until they cannot use the
 * app at all.
 */
class LabelsTest {

    @Test
    fun everyGlyphHasAName() {
        val blank = G.entries.filter { it.label.isBlank() }
        assertEquals("glyphs with no spoken name", emptyList<G>(), blank)
    }

    @Test
    fun namesAreWordsNotShapes() {
        // "Two crossed arrows" describes the drawing; "Shuffle" describes the
        // action. A listener needs the second one.
        for (glyph in G.entries) {
            val label = glyph.label
            assertTrue("$glyph is too long: $label", label.length <= 24)
            assertTrue("$glyph starts lowercase: $label", label.first().isUpperCase())
            assertTrue("$glyph ends with a period: $label", !label.endsWith("."))
        }
    }

    @Test
    fun repeatSaysWhichModeIsOnAndWhatComesNext() {
        val off = Labels.repeat(0)
        val all = Labels.repeat(1)
        val one = Labels.repeat(2)
        assertNotEquals(off, all)
        assertNotEquals(all, one)
        assertNotEquals(off, one)
        // The first clause is the state, so a listener can tell where they are
        // without pressing the button to find out.
        assertTrue(off.startsWith("Repeat off"))
        assertTrue(all.startsWith("Repeat all"))
        assertTrue(one.startsWith("Repeat one"))
        // The second clause is the action, so they know what a tap will do.
        assertTrue(all.contains("repeat one"))
        assertTrue(one.contains("off"))
        assertTrue(off.contains("repeat all"))
    }

    @Test
    fun repeatNeverFallsBackToTheBareWordForARealMode() {
        for (mode in 0..2) {
            assertNotEquals("Repeat", Labels.repeat(mode))
        }
        // Only an impossible value gets the plain noun.
        assertEquals("Repeat", Labels.repeat(-1))
        assertEquals("Repeat", Labels.repeat(7))
    }

    @Test
    fun shuffleAndLikeDescribeTheStateTheyAreIn() {
        assertEquals("Shuffle on. Turn it off", Labels.shuffle(true))
        assertEquals("Shuffle off. Turn it on", Labels.shuffle(false))
        assertEquals("Unlike", Labels.like(true))
        assertEquals("Like", Labels.like(false))
    }

    @Test
    fun thePlayButtonIsNamedForWhatATapWillDo() {
        assertEquals("Play", Labels.transport(playing = false, buffering = false))
        assertEquals("Pause", Labels.transport(playing = true, buffering = false))
        // While buffering it must not claim to be a play button — tapping it
        // does not start playback, it waits.
        assertEquals("Buffering", Labels.transport(playing = false, buffering = true))
        assertEquals("Buffering", Labels.transport(playing = true, buffering = true))
    }

    @Test
    fun noLabelShoutsOrWhispers() {
        // All-caps is read letter by letter by some screen readers.
        val shouting = G.entries.map { it.label }.filter { it == it.uppercase() && it.any { c -> c.isLetter() } }
        assertEquals(emptyList<String>(), shouting)
    }
}
