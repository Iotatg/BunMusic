package app.bunmusic

import app.bunmusic.data.LyricsApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LRC parsing and query cleaning.
 *
 * Both are pure string work that decides whether the lyrics panel has anything
 * in it at all: a mis-parsed timestamp shows the wrong line, and an unclean
 * query asks LRCLIB for "Song (Remastered 2011) [Deluxe Edition]" and gets
 * nothing back.
 */
class LyricsTest {

    // ------------------------------------------------------------------ parse

    @Test
    fun `a two digit fraction is hundredths`() {
        val lines = LyricsApi.parseLrc("[00:12.34]Hello")
        assertEquals(1, lines.size)
        assertEquals(12_340L, lines[0].atMs)
        assertEquals("Hello", lines[0].text)
    }

    @Test
    fun `a whole second tag needs no fraction`() {
        val lines = LyricsApi.parseLrc("[01:02]Line")
        assertEquals(62_000L, lines[0].atMs)
    }

    @Test
    fun `one digit of fraction means tenths not milliseconds`() {
        // "[00:01.5]" is one and a half seconds. Read as 5 ms it would put the
        // line 495 ms early, which is the difference between a lyric that lands
        // and one that leads.
        assertEquals(1_500L, LyricsApi.parseLrc("[00:01.5]x")[0].atMs)
    }

    @Test
    fun `padded and unpadded fractions agree`() {
        assertEquals(1_100L, LyricsApi.parseLrc("[00:01.1]x")[0].atMs)
        assertEquals(1_100L, LyricsApi.parseLrc("[00:01.10]x")[0].atMs)
        assertEquals(1_100L, LyricsApi.parseLrc("[00:01.100]x")[0].atMs)
    }

    @Test
    fun `a colon before the fraction is accepted`() {
        // Some editors write [mm:ss:cc]. The same line has to land at the same ms.
        assertEquals(12_340L, LyricsApi.parseLrc("[00:12:34]Hello")[0].atMs)
    }

    @Test
    fun `several tags on one line repeat the text`() {
        val lines = LyricsApi.parseLrc("[00:01.00][00:05.00]Chorus")
        assertEquals(2, lines.size)
        assertEquals(listOf("Chorus", "Chorus"), lines.map { it.text })
        assertEquals(listOf(1_000L, 5_000L), lines.map { it.atMs })
    }

    @Test
    fun `lines without a timestamp are dropped`() {
        val lines = LyricsApi.parseLrc(
            """
            [ar:Someone]
            [ti:Song]
            just a stray line
            [00:03.00]Real lyric
            """.trimIndent(),
        )
        assertEquals(1, lines.size)
        assertEquals("Real lyric", lines[0].text)
    }

    @Test
    fun `a timestamp with no words is dropped`() {
        // Blank lines between verses are timing markers, not lyrics; rendering
        // them would push the visible lyric off the highlight.
        assertTrue(LyricsApi.parseLrc("[00:01.00]\n[00:02.00]   ").isEmpty())
    }

    @Test
    fun `output is sorted even when the file is not`() {
        val lines = LyricsApi.parseLrc("[00:09.00]third\n[00:01.00]first\n[00:05.00]second")
        assertEquals(listOf("first", "second", "third"), lines.map { it.text })
    }

    @Test
    fun `brackets inside the lyric survive`() {
        val lines = LyricsApi.parseLrc("[00:01.00]Hello [world] again")
        assertEquals("Hello [world] again", lines[0].text)
    }

    @Test
    fun `carriage returns from a windows file are trimmed`() {
        val lines = LyricsApi.parseLrc("[00:01.00]One\r\n[00:02.00]Two\r\n")
        assertEquals(listOf("One", "Two"), lines.map { it.text })
    }

    @Test
    fun `an empty file parses to no lines`() {
        assertTrue(LyricsApi.parseLrc("").isEmpty())
        assertTrue(LyricsApi.parseLrc("\n\n\n").isEmpty())
    }

    @Test
    fun `leading spaces after the tag are removed`() {
        assertEquals("Hi", LyricsApi.parseLrc("[00:01.00]    Hi")[0].text)
    }

    @Test
    fun `minutes beyond an hour still count as minutes`() {
        // LRC has no hour field; a 90-minute DJ set writes [90:00.00] and must
        // not wrap around to half an hour.
        assertEquals(5_400_000L, LyricsApi.parseLrc("[90:00.00]x")[0].atMs)
    }

    // ------------------------------------------------------------------ clean

    @Test
    fun `parenthetical suffixes are cut`() {
        assertEquals("Song", LyricsApi.clean("Song (Remastered 2011)"))
        assertEquals("Song", LyricsApi.clean("Song [Deluxe Edition]"))
        assertEquals("Song", LyricsApi.clean("Song (feat. Someone)"))
    }

    @Test
    fun `feature credits are cut from the artist`() {
        assertEquals("Artist", LyricsApi.clean("Artist feat. Guest"))
        assertEquals("Artist", LyricsApi.clean("Artist ft. Guest"))
        assertEquals("Artist", LyricsApi.clean("Artist featuring Guest"))
    }

    @Test
    fun `feature credits are cut case insensitively`() {
        assertEquals("Artist", LyricsApi.clean("Artist FEAT. Guest"))
        assertEquals("Artist", LyricsApi.clean("Artist Featuring Guest"))
    }

    @Test
    fun `version suffixes after a dash are cut`() {
        assertEquals("Song", LyricsApi.clean("Song - Live"))
        assertEquals("Song", LyricsApi.clean("Song - Radio Edit"))
        assertEquals("Song", LyricsApi.clean("Song - Remastered 2009"))
        assertEquals("Song", LyricsApi.clean("Song - Remaster"))
    }

    @Test
    fun `a dash that is part of the title is kept`() {
        // "Jay-Z" and "Love-Hate" must not be mistaken for a version suffix.
        assertEquals("Jay-Z", LyricsApi.clean("Jay-Z"))
        assertEquals("Love-Hate", LyricsApi.clean("Love-Hate"))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("Song", LyricsApi.clean("   Song   "))
    }

    @Test
    fun `a clean title is left alone`() {
        assertEquals("Midnight City", LyricsApi.clean("Midnight City"))
    }
}
