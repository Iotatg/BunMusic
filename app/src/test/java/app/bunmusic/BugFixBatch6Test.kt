package app.bunmusic

import app.bunmusic.core.CrashGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressions for the concurrency sweep. The parts that need a device are
 * covered by the annotations in the sources; these assert the pure contract
 * behind the log writer so the read-modify-write cannot silently lose a line.
 */
class BugFixBatch6Test {

    @Test
    fun `appending a crash keeps every earlier line and stays capped`() {
        val lines = (1..24).map { "old-$it" }
        val entry = CrashGuard.Entry(1L, "main", "where", "message", "stack")
        val out = CrashGuard.appendLines(lines, entry)
        assertEquals(24, out.size)
        // The oldest line is evicted, the other 23 survive in order.
        assertEquals("old-2", out.first())
        assertTrue(out.dropLast(1).withIndex().all { (i, line) -> line == "old-${i + 2}" })
        assertEquals(CrashGuard.format(entry), out.last())
    }

    @Test
    fun `the appended line is one parseable record`() {
        val entry = CrashGuard.Entry(
            at = 42L, thread = "main\rio", where = "net",
            message = "java.io.IOException: CRLF\r\nfrom server",
            stack = "line1\r\nline2",
        )
        val out = CrashGuard.appendLines(emptyList(), entry)
        assertEquals(1, out.size)
        val line = out.single()
        assertTrue(!line.contains('\n'))
        assertTrue(!line.contains('\r'))
        val parsed = CrashGuard.parse(line)
        assertNotNull(parsed)
        assertEquals(42L, parsed!!.at)
        assertTrue(parsed.stack.contains("line1"))
    }

    @Test
    fun `a short log is not padded or reordered`() {
        val lines = listOf("a", "b")
        val entry = CrashGuard.Entry(2L, "t", "w", "m", "s")
        val out = CrashGuard.appendLines(lines, entry)
        assertEquals(listOf("a", "b"), out.take(2))
        assertEquals(3, out.size)
    }
}
