package app.bunmusic

import app.bunmusic.core.Qr
import app.bunmusic.data.QueueCode
import app.bunmusic.data.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueCodeTest {

    private fun track(id: String, title: String = "Song $id", artist: String = "Artist") =
        Track(id = id, title = title, artist = artist, videoId = id, durationMs = 214_000)

    private val sample = listOf(
        track("dQw4w9WgXcQ", "Kesariya", "Arijit Singh"),
        track("kJQP7kiw5Fk", "Tum Hi Ho", "Arijit Singh"),
        track("3JZ_D3ELwOQ", "Channa Mereya", "Pritam"),
    )

    @Test
    fun aQueueSurvivesTheRoundTrip() {
        val items = QueueCode.decode(QueueCode.encode(sample))!!
        assertEquals(3, items.size)
        assertEquals("dQw4w9WgXcQ", items[0].videoId)
        assertEquals("Kesariya", items[0].title)
        assertEquals("Arijit Singh", items[0].artist)
        assertEquals(214_000L, items[0].durationMs)
    }

    @Test
    fun hindiTitlesSurviveToo() {
        val hindi = listOf(track("abc12345678", "क्यू साझा करो", "ए आर रहमान"))
        val items = QueueCode.decode(QueueCode.encode(hindi))!!
        assertEquals("क्यू साझा करो", items[0].title)
        assertEquals("ए आर रहमान", items[0].artist)
    }

    @Test
    fun fiftyTracksStillFitInAScannableSymbol() {
        val many = (0 until 60).map { track("id%08d".format(it), "Track number $it", "Some Artist $it") }
        val code = QueueCode.encode(many)
        assertEquals(50, QueueCode.decode(code)!!.size)
        assertTrue("code is ${code.length} chars", code.length <= QueueCode.MAX_CHARS)
        // Must still fit a QR code at M with room to spare.
        val symbol = Qr.encode(code, Qr.Ecc.M)
        assertTrue("version ${(symbol.size - 17) / 4}", symbol.size <= 129)
    }

    /**
     * The case that actually used to break it: fifty long, unrelated names do
     * not deflate, so the naive code was ~12 KB against a 2.3 KB QR. Trimming
     * must keep a prefix, stay under the ceiling, and still decode.
     */
    @Test
    fun incompressibleNamesAreTrimmedButStillProduceReadableCode() {
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        fun rnd(n: Int, seed: Int): String {
            var x = seed * 2654435761L + 1
            return buildString {
                repeat(n) {
                    x = x * 6364136223846793005L + 1442695040888963407L
                    append(alphabet[((x ushr 33) and 0x7fffffff).toInt() % alphabet.length])
                }
            }
        }
        val tracks = (0 until 50).map { i ->
            val id = rnd(11, i * 7 + 1)
            track(id, rnd(120, i * 7 + 2), rnd(120, i * 7 + 3))
        }
        val code = QueueCode.encode(tracks)
        val items = QueueCode.decode(code)!!
        assertTrue("code is ${code.length} chars", code.length <= QueueCode.MAX_CHARS)
        assertTrue("expected some tracks, got ${items.size}", items.size >= 1)
        assertTrue("expected trimming, got ${items.size}", items.size < 50)
        // What survives is the head of the queue, in order.
        assertEquals(QueueCode.decode(QueueCode.encode(tracks.take(items.size)))!!.map { it.videoId },
            items.map { it.videoId })
        // And it must still become a scannable symbol, not just short text.
        val symbol = Qr.encode(code, Qr.Ecc.M)
        assertTrue("version ${(symbol.size - 17) / 4}", symbol.size <= 129)
        assertEquals(items.size, QueueCode.count(code))
    }

    @Test
    fun aNormalQueueIsNeverTrimmed() {
        val many = (0 until 50).map { track("id%08d".format(it), "Track number $it", "Some Artist $it") }
        assertEquals(50, QueueCode.count(QueueCode.encode(many)))
    }

    @Test
    fun countOfAnEmptyOrBrokenCodeIsZero() {
        assertEquals(0, QueueCode.count(""))
        assertEquals(0, QueueCode.count("BQ1not-a-code"))
    }

    @Test
    fun duplicatesAreSentOnce() {
        val doubled = listOf(sample[0], sample[0], sample[1])
        assertEquals(2, QueueCode.decode(QueueCode.encode(doubled))!!.size)
    }

    @Test
    fun localFilesAreDroppedRatherThanSentBroken() {
        val local = Track(id = "local:1", title = "Voice note", artist = "", videoId = "")
        val items = QueueCode.decode(QueueCode.encode(listOf(local, sample[0])))!!
        assertEquals(1, items.size)
        assertEquals("dQw4w9WgXcQ", items[0].videoId)
    }

    @Test
    fun nothingShareableGivesAnEmptyCode() {
        assertEquals("", QueueCode.encode(emptyList()))
        assertEquals("", QueueCode.encode(listOf(Track(id = "local:1", title = "x", artist = ""))))
    }

    @Test
    fun rubbishIsRefusedInsteadOfHalfRead() {
        assertNull(QueueCode.decode(""))
        assertNull(QueueCode.decode("hello world"))
        assertNull(QueueCode.decode("BQ1"))
        assertNull(QueueCode.decode("BQ1not-base64!!"))
        assertNull(QueueCode.decode("BQ1AAAAAAAAAAAA"))
    }

    @Test
    fun aTruncatedCodeFailsLoudly() {
        val code = QueueCode.encode(sample)
        assertNull(QueueCode.decode(code.dropLast(12)))
    }

    @Test
    fun whitespaceFromPastingDoesNotBreakIt() {
        val code = QueueCode.encode(sample)
        val wrapped = code.chunked(40).joinToString("\n  ")
        assertEquals(3, QueueCode.decode(wrapped)!!.size)
    }

    @Test
    fun aTabInATitleCannotBreakTheRow() {
        val nasty = listOf(track("aaaaaaaaaaa", "Title\twith\ttabs", "Art\nist"))
        val items = QueueCode.decode(QueueCode.encode(nasty))!!
        assertEquals("Title with tabs", items[0].title)
        assertEquals("Art ist", items[0].artist)
    }

    @Test
    fun artworkIsDerivedFromTheIdSoCoversAppearAtOnce() {
        val tracks = QueueCode.toTracks(QueueCode.decode(QueueCode.encode(sample))!!)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", tracks[0].artUrl)
        assertEquals("dQw4w9WgXcQ", tracks[0].videoId)
        assertEquals("yt:dQw4w9WgXcQ", tracks[0].id)
    }

    @Test
    fun looksIsCheapAndHonest() {
        assertTrue(QueueCode.looks(QueueCode.encode(sample)))
        assertFalse(QueueCode.looks("BQ1"))
        assertFalse(QueueCode.looks("https://example.com"))
    }

    @Test
    fun theCodeStartsWithTheVersionPrefix() {
        assertTrue(QueueCode.encode(sample).startsWith("BQ1"))
        assertEquals("BQ1", QueueCode.PREFIX)
    }
}
