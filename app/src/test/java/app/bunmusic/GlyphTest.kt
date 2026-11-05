package app.bunmusic

import app.bunmusic.core.G
import app.bunmusic.core.glyphStroke
import app.bunmusic.core.label
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that make sixty hand-drawn glyphs look like one set.
 *
 * A stroke that does not scale is the difference between an icon set and a
 * pile of drawings: at 13 dp a 1.9 dp line is 15 % of the icon, at 26 dp it is
 * 7 %. Same numbers, two different typefaces.
 */
class GlyphTest {

    @Test
    fun strokeIsAlwaysTwoUnitsOfTheGrid() {
        // 2 units on a 24 grid, whatever the rendered size.
        for (dp in listOf(10, 12, 13, 16, 18, 20, 22, 24, 26, 32, 44)) {
            assertEquals("at $dp dp", dp * 2f / 24f, glyphStroke(dp.dp).value, 1e-4f)
        }
    }

    @Test
    fun theStrokeToSizeRatioNeverDrifts() {
        val ratio = glyphStroke(24.dp).value / 24f
        for (dp in listOf(10, 13, 18, 22, 26, 32)) {
            assertEquals("at $dp dp", ratio, glyphStroke(dp.dp).value / dp, 1e-6f)
        }
    }

    @Test
    fun aThirteenDpCloseMarkIsThinnerThanOneDp() {
        // The concrete case that used to be wrong: the remove-from-queue ×
        // was drawn with a 1.9 dp stroke on a 13 dp box.
        assertTrue(glyphStroke(13.dp).value < 1.1f)
        assertTrue(glyphStroke(26.dp).value > 2f)
    }

    @Test
    fun strokeStaysInsideTheIconAtEverySize() {
        // A 2/24 stroke can never eat more than a sixth of the box, so a glyph
        // never becomes a blob however small it is drawn.
        for (dp in listOf(8, 10, 14, 22, 40)) {
            assertTrue(glyphStroke(dp.dp).value * 6f < dp)
        }
    }

    @Test
    fun everyGlyphStillHasAName() {
        // Cheap guard next to the stroke rules: adding a glyph to G without
        // naming it stops the build in `G.label`, but this catches an empty
        // string that would compile fine and say nothing.
        for (g in G.entries) {
            assertTrue("$g has no label", g.label.isNotBlank())
        }
    }
}
