package app.bunmusic

import app.bunmusic.core.BunShape
import app.bunmusic.core.Contrast
import app.bunmusic.core.CornerRadii
import app.bunmusic.core.Cosmos
import app.bunmusic.core.CrashGuard
import app.bunmusic.core.Dust
import app.bunmusic.core.Shapes
import app.bunmusic.core.SwipeSkip
import app.bunmusic.data.QueueCode
import app.bunmusic.data.QueueMath
import app.bunmusic.data.SortKey
import app.bunmusic.data.SortSpec
import app.bunmusic.data.Sorting
import app.bunmusic.data.Track
import app.bunmusic.player.PlayerState
import app.bunmusic.widget.WidgetText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater

/**
 * Regressions for the 2.11.x bug sweep. Every assertion here failed before the
 * fix (or would have thrown). Kept together so the guards are easy to audit.
 */
class BugFixRegressionTest {

    private fun t(id: String, duration: Long = 180_000L) =
        Track(id = id, title = id, artist = "A", durationMs = duration)

    /** A shareable track: encode() drops rows without a videoId. */
    private fun shared(id: String) = Track(
        id = "yt:$id", title = id, artist = "A", videoId = id, durationMs = 180_000L,
    )

    /**
     * Build a queue code the way QueueCode.pack does, but from a raw payload so
     * a test can smuggle hostile field values past encode()'s own cleaning.
     */
    private fun rawCode(payload: String): String {
        val deflater = Deflater(9, false) // zlib-wrapped, matching QueueCode.deflate
        val packed = try {
            val input = payload.toByteArray(Charsets.UTF_8)
            deflater.setInput(input)
            deflater.finish()
            val out = ByteArrayOutputStream(input.size)
            val buffer = ByteArray(4096)
            while (!deflater.finished()) {
                val n = deflater.deflate(buffer)
                if (n == 0) break
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        } finally {
            deflater.end()
        }
        return QueueCode.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(packed)
    }

    // ------------------------------------------------------------- A1: Sorting

    @Test
    fun `recent descending still keeps never-added tracks at the end`() {
        val tracks = listOf(t("a"), t("b"), t("c"))
        // "b" was never added; ascending already sent it to the end.
        val asc = Sorting.sort(tracks, SortSpec(SortKey.RECENT), addedOrder = listOf("c", "a"))
        assertEquals(listOf("c", "a", "b"), asc.map { it.id })
        // Descending used to reverse the whole list, flipping MAX_VALUE strangers
        // to the front. They must stay last in both directions.
        val desc = Sorting.sort(tracks, SortSpec(SortKey.RECENT, descending = true), addedOrder = listOf("c", "a"))
        assertEquals(listOf("b"), desc.takeLast(1).map { it.id })
        assertEquals(setOf("a", "c"), desc.take(2).map { it.id }.toSet())
    }

    @Test
    fun `descending is a real reversal for the other keys`() {
        val tracks = listOf(t("a"), t("b"), t("c"))
        assertEquals(listOf("c", "b", "a"), Sorting.sort(tracks, SortSpec(SortKey.TITLE, descending = true)).map { it.id })
    }

    // ------------------------------------------------------------- A2: QueueMath

    @Test
    fun `insertNext and jumpTarget survive an extreme index`() {
        val q = listOf(t("a"), t("b"))
        // Int.MAX_VALUE + 1 used to overflow to MIN_VALUE and coerce to head.
        val inserted = QueueMath.insertNext(q, Int.MAX_VALUE, t("x"))
        assertEquals("x", inserted.last().id)
        assertEquals("a", inserted.first().id)
        assertEquals(q.lastIndex, QueueMath.jumpTarget(q, Int.MAX_VALUE, Int.MAX_VALUE))
        assertEquals(0, QueueMath.jumpTarget(q, Int.MIN_VALUE, Int.MIN_VALUE))
    }

    // ------------------------------------------------------------- A3/A4: QueueCode

    @Test
    fun `an absurd pasted duration is clamped before it can overflow into a negative`() {
        // Long.MAX_VALUE seconds: the old `toLongOrNull() * 1000` overflowed Long
        // into a negative durationMs. decode() must clamp to one day *before* ×1000.
        val items = QueueCode.decode(rawCode("vid\tSong\tArtist\t9223372036854775807"))
        assertNotNull(items)
        val duration = items!!.single().durationMs
        assertTrue("durationMs must stay non-negative, was $duration", duration >= 0L)
        assertEquals(86_400_000L, duration)
    }

    @Test
    fun `a negative pasted duration cannot leak through as a negative length`() {
        val items = QueueCode.decode(rawCode("vid\tSong\tArtist\t-999999999"))
        assertNotNull(items)
        assertEquals(0L, items!!.single().durationMs)
    }

    @Test
    fun `a deflate zip bomb is refused instead of inflating without bound`() {
        // 200 KB of one letter compresses to a tiny blob, so it clears the code
        // length check; inflate() must cap the output at 64 KB and decode return null.
        val bomb = rawCode("vid\t" + "A".repeat(200_000) + "\tArtist\t180")
        assertNull(QueueCode.decode(bomb))
    }

    @Test
    fun `an over-long pasted code is refused before decoding`() {
        val huge = "BQ1" + "A".repeat(QueueCode.MAX_CHARS * 2 + 1)
        assertNull(QueueCode.decode(huge))
    }

    @Test
    fun `normal queue still round-trips after the safety guards`() {
        val tracks = listOf(shared("abc"), shared("def"))
        val code = QueueCode.encode(tracks)
        val decoded = QueueCode.decode(code)
        assertNotNull(decoded)
        assertEquals(2, decoded!!.size)
    }

    // ------------------------------------------------------------- A7: CrashGuard

    @Test
    fun `a carriage return in a field no longer splits a crash record`() {
        val entry = CrashGuard.Entry(
            at = 1L, thread = "main\r2", where = "net\rio",
            message = "java.io.IOException: CRLF\r\nfrom server", stack = "line1\r\nline2",
        )
        val line = CrashGuard.format(entry)
        // The encoded record must be exactly one line (\r/\n are scrubbed to spaces / BR).
        assertFalse(line.contains('\n'))
        assertFalse(line.contains('\r'))
        val parsed = CrashGuard.parse(line)
        assertNotNull(parsed)
        assertEquals(1L, parsed!!.at)
        assertTrue(parsed.stack.contains("line1"))
    }

    @Test
    fun `format then parse preserves the field count with separators scrubbed`() {
        val entry = CrashGuard.Entry(42L, "th\nread", "wh\u0001ere", "msg", "a\nb")
        val parsed = CrashGuard.parse(CrashGuard.format(entry))
        assertNotNull(parsed)
        assertEquals(42L, parsed!!.at)
        assertEquals(5, CrashGuard.format(entry).split('\u0001').size)
    }

    // ------------------------------------------------------------- A8: Contrast NaN

    @Test
    fun `blend with a non-finite fraction returns a real colour not black`() {
        val black = 0xFF000000.toInt()
        val mid = 0xFF808080.toInt()
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val out = Contrast.blend(black, mid, bad)
            // A non-finite t now falls back to 0, i.e. the first colour — an
            // actual argb value, never the pure black produced by NaN arithmetic.
            assertEquals(black, out)
        }
    }

    // ------------------------------------------------------------- A9: Shapes NaN

    @Test
    fun `shapes stay finite for non-finite inputs`() {
        // scaled() is a CornerRadii member; a NaN shortSide would poison all radii.
        val r = Shapes.radii(BunShape.ROUND).scaled(Float.NaN)
        assertTrue(listOf(r.topStart, r.topEnd, r.bottomEnd, r.bottomStart).all { it.isFinite() })
        val m = Shapes.morph(CornerRadii(0f, 0f, 0f, 0f), CornerRadii(1f, 1f, 1f, 1f), Float.NaN)
        assertTrue(listOf(m.topStart, m.topEnd, m.bottomEnd, m.bottomStart).all { it.isFinite() })
    }

    // ------------------------------------------------------------- A10: SwipeSkip NaN

    @Test
    fun `motion stays finite for a NaN drag or width`() {
        assertTrue(SwipeSkip.clamp(Float.NaN, 100f).isFinite())
        assertTrue(SwipeSkip.clamp(10f, Float.NaN).isFinite())
        assertTrue(SwipeSkip.alpha(Float.NaN, 100f).isFinite())
        assertTrue(SwipeSkip.alpha(10f, Float.NaN).isFinite())
        assertEquals(0, SwipeSkip.direction(Float.NaN, 100f))
    }

    // ------------------------------------------------------------- A11: Cosmos NaN

    private fun dust() = Dust(0.5f, 0.5f, 0.01f, 0.01f, 0.001f, 0.001f, 0.02f, 0.3f, 1)

    @Test
    fun `a NaN time delta cannot poison the particle field`() {
        val field = listOf(dust())
        val stepped = Cosmos.step(field, Float.NaN)
        assertEquals(field, stepped)
        val absolute = Cosmos.at(field, Float.NaN)
        assertTrue(absolute.all { it.x.isFinite() && it.y.isFinite() && it.vx.isFinite() })
        assertTrue(Cosmos.wrap01(Float.NaN).isFinite())
    }

    // ------------------------------------------------------------- A22: clock overflow

    @Test
    fun `widget clock survives a wildly large duration`() {
        // 30 days in ms: the old (ms/1000).toInt() overflowed to a negative clock.
        val huge = 30L * 24 * 3600 * 1000
        val clock = WidgetText.clock(huge)
        assertFalse(clock.startsWith("-"))
        assertTrue(clock.contains(":"))
    }

    // ------------------------------------------------------------- C4: A-B at 0:00

    @Test
    fun `marking A at zero is a valid start, not the unset sentinel`() {
        val fresh = PlayerState()
        assertFalse(fresh.abActive)
        assertEquals(-1L, fresh.abStartMs)
        // A at the very start, B later: the loop must be active.
        val marked = PlayerState(abStartMs = 0L, abEndMs = 30_000L)
        assertTrue(marked.abActive)
        // Cleared state is inactive again.
        assertFalse(PlayerState(abStartMs = -1L, abEndMs = -1L).abActive)
    }
}
