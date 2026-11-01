package app.bunmusic

import app.bunmusic.data.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The hero card prints a track's credit line, and the two halves of it come from
 * different places: the artist always exists, the album often doesn't. The card
 * used to concatenate both with a separator in between regardless, so a track
 * with no album read "Mehul Mahesh & DJ Aynik ·" — a separator pointing at
 * nothing, right under the song title on the first screen of the app.
 */
class CreditLineTest {

    private fun track(artist: String, album: String) =
        Track(id = "x", title = "Zulfien", artist = artist, album = album)

    @Test
    fun albumIsDroppedWhenMissing() {
        val line = track("Mehul Mahesh & DJ Aynik", "").creditLine()
        assertEquals("Mehul Mahesh & DJ Aynik", line)
        assertFalse("no dangling separator", line.trimEnd().endsWith("·"))
    }

    @Test
    fun bothPartsAreJoined() {
        assertEquals("Arijit Singh · Aashiqui 2", track("Arijit Singh", "Aashiqui 2").creditLine())
    }

    @Test
    fun whitespaceCountsAsMissing() {
        assertEquals("Arijit Singh", track("Arijit Singh", "   ").creditLine())
        assertEquals("Aashiqui 2", track("  ", "Aashiqui 2").creditLine())
    }

    @Test
    fun neverPrintsAnEmptyLine() {
        assertEquals("Unknown artist", track("", "").creditLine())
    }
}
