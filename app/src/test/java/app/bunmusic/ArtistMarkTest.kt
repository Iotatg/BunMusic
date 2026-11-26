package app.bunmusic

import app.bunmusic.core.ArtistMark
import app.bunmusic.core.Contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The monogram rules.
 *
 * The player had no artist face because the model had no artist identity; when
 * a portrait is missing the UI draws these letters instead, so the two things
 * that must never fail are: there is always a letter, and it is always readable.
 */
class ArtistMarkTest {

    @Test
    fun `two words become two initials`() {
        assertEquals("AS", ArtistMark.initials("Arijit Singh"))
        assertEquals("AR", ArtistMark.initials("A. R. Rahman"))
        assertEquals("SG", ArtistMark.initials("Simon & Garfunkel"))
        assertEquals("CS", ArtistMark.initials("Chase & Status"))
    }

    @Test
    fun `a single word becomes one initial`() {
        assertEquals("A", ArtistMark.initials("Adele"))
        assertEquals("M", ArtistMark.initials("M83"))
        assertEquals("C", ArtistMark.initials("Coldplay"))
    }

    @Test
    fun `an empty name still yields a drawable mark`() {
        assertEquals("?", ArtistMark.initials(""))
        assertEquals("?", ArtistMark.initials("   "))
        assertEquals("?", ArtistMark.initials("!!!"))
    }

    @Test
    fun `a non-latin name keeps its own letters`() {
        assertEquals("स", ArtistMark.initials("सावन"))
        assertEquals("एआ", ArtistMark.initials("ए आर रहमान"))
    }

    @Test
    fun `three words stop at two initials`() {
        assertEquals("DP", ArtistMark.initials("Daft Punk Forever"))
    }

    @Test
    fun `the same name always gets the same hue`() {
        assertEquals(ArtistMark.hue("M83"), ArtistMark.hue("M83"))
        assertEquals(ArtistMark.hue("  M83  "), ArtistMark.hue("m83"))
    }

    @Test
    fun `different names get different hues`() {
        assertNotEquals(ArtistMark.hue("Arijit Singh"), ArtistMark.hue("Adele"))
        assertNotEquals(ArtistMark.hue("Coldplay"), ArtistMark.hue("Radiohead"))
    }

    @Test
    fun `the hue stays inside the wheel`() {
        listOf("Adele", "M83", "सावन", "!!!", "", "A very long artist name indeed")
            .forEach { name ->
                val h = ArtistMark.hue(name)
                assertTrue("$name hue $h", h >= 0f && h < 360f)
            }
    }

    @Test
    fun `the disc is fully opaque`() {
        listOf("Adele", "M83", "Coldplay").forEach { name ->
            assertEquals("$name alpha", 0xFF, (ArtistMark.argb(name) ushr 24) and 0xFF)
        }
    }

    @Test
    fun `the initials always clear AA on their own disc`() {
        val names = listOf(
            "Adele", "M83", "Coldplay", "Radiohead", "Arijit Singh", "सावन",
            "Simon & Garfunkel", "Daft Punk", "The Weeknd", "Beyoncé",
            "五十嵐", "!!!", "", "A very long artist name indeed",
        )
        names.forEach { name ->
            val ratio = Contrast.ratio(ArtistMark.ink(name), ArtistMark.argb(name))
            assertTrue("$name ink ratio $ratio", ratio >= Contrast.AA)
        }
    }
}
